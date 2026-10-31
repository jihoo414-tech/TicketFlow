import argparse
import csv
import json
import os
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

import numpy as np
import pandas as pd

ROOT = Path(__file__).resolve().parent.parent
PERF = ROOT / 'perf'
K6_DIR = PERF / 'k6'
OUT = PERF / 'out'
RESULTS = ROOT / 'docs' / 'performance'
COMPOSE = ['docker', 'compose', '-f', str(ROOT / 'docker-compose.aws.yml'), '-f', str(PERF / 'docker-compose.perf.yml')]
APP_URL = 'http://localhost:8081'
APP_CONTAINER = 'ticketflow-aws-app-1'
DB_CONTAINER = 'ticketflow-aws-mysql-1'
CPU_LIMIT = 2
MAIN_SHOWTIME = 1

STEADY_VUS = [10, 25, 50, 100, 150]
STEADY_DURATION = '3m'
STEADY_SEATS_P95_TARGET = 300
STEADY_HOLD_P95_TARGET = 500
STEADY_ERROR_RATE_TARGET = 0.001

PEAK_START = 50
PEAK_CAP = 3000
PEAK_RESOLUTION = 10
PEAK_P95_TARGET = 500
PEAK_SEARCHES = 3

LOCK_STATUS = ['Threads_running', 'Threads_connected', 'Questions',
               'Innodb_row_lock_waits', 'Innodb_row_lock_time', 'Innodb_row_lock_current_waits']


def log(message):
    print(f'[{datetime.now():%H:%M:%S}] {message}', flush=True)


def read_env():
    env = {}
    for line in (ROOT / '.env').read_text(encoding='utf-8').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            env[key.strip()] = value.strip()
    return env


ENV = read_env()


def sh(cmd, **kwargs):
    return subprocess.run(cmd, check=True, capture_output=True, text=True, encoding='utf-8', **kwargs)


def mysql(sql):
    out = sh(['docker', 'exec', '-e', f'MYSQL_PWD={ENV["MYSQL_ROOT_PASSWORD"]}', DB_CONTAINER,
              'mysql', '-uroot', '-N', '-B', ENV['MYSQL_DATABASE'], '-e', sql]).stdout
    return [row.split('\t') for row in out.splitlines() if row]


def build():
    log('앱 jar와 이미지를 빌드한다')
    gradlew = 'gradlew.bat' if os.name == 'nt' else './gradlew'
    subprocess.run([str(ROOT / gradlew), 'bootJar', '-q'], check=True, cwd=ROOT, shell=os.name == 'nt')
    subprocess.run(COMPOSE + ['build', 'app'], check=True, cwd=ROOT)


def fetch_json(path, timeout=1.0):
    with urllib.request.urlopen(APP_URL + path, timeout=timeout) as res:
        return json.load(res)


def reset():
    sh(COMPOSE + ['down', '-v', '--remove-orphans'], cwd=ROOT)
    sh(COMPOSE + ['up', '-d', '--wait'], cwd=ROOT)
    deadline = time.time() + 120
    while time.time() < deadline:
        try:
            if fetch_json('/actuator/health').get('status') == 'UP':
                return
        except (urllib.error.URLError, ConnectionError, TimeoutError, OSError):
            pass
        time.sleep(1)
    raise RuntimeError('앱이 120초 안에 뜨지 않았다')


def k6(script, run_dir, env):
    cmd = ['k6', 'run', '--quiet', '--out', f'csv={run_dir / "k6.csv"}']
    for key, value in env.items():
        cmd += ['-e', f'{key}={value}']
    cmd.append(script)
    with open(run_dir / f'{Path(script).stem}.log', 'w', encoding='utf-8') as out:
        return subprocess.run(cmd, cwd=K6_DIR, stdout=out, stderr=subprocess.STDOUT,
                              env={**os.environ, 'K6_CSV_TIME_FORMAT': 'unix_milli'}).returncode


def warmup(run_dir):
    warm_dir = run_dir / 'warmup'
    warm_dir.mkdir(parents=True, exist_ok=True)
    k6('warmup.js', warm_dir, {'SHOWTIME_ID': 2, 'MEMBER_OFFSET': 9000})
    mysql('TRUNCATE TABLE performance_schema.events_statements_summary_by_digest')


class Collector:
    def __init__(self):
        self.stop_event = threading.Event()
        self.app, self.docker, self.db = [], [], []
        self.threads = [threading.Thread(target=self.loop, args=(fn,), daemon=True)
                        for fn in (self.sample_app, self.sample_docker, self.sample_db)]

    def __enter__(self):
        for thread in self.threads:
            thread.start()
        return self

    def __exit__(self, *exc):
        self.stop_event.set()
        for thread in self.threads:
            thread.join()

    def loop(self, sample):
        while not self.stop_event.is_set():
            started = time.time()
            try:
                sample(started)
            except Exception:
                pass
            self.stop_event.wait(max(0.0, 1.0 - (time.time() - started)))

    def metric(self, name, tag=None):
        path = f'/actuator/metrics/{name}' + (f'?tag={tag}' if tag else '')
        try:
            return {m['statistic']: m['value'] for m in fetch_json(path)['measurements']}
        except urllib.error.HTTPError:
            return {}

    def sample_app(self, t):
        gc = self.metric('jvm.gc.pause')
        acquire = self.metric('hikaricp.connections.acquire')
        self.app.append({
            't': t,
            'hikari_pending': self.metric('hikaricp.connections.pending').get('VALUE'),
            'hikari_active': self.metric('hikaricp.connections.active').get('VALUE'),
            'hikari_acquire_count': acquire.get('COUNT'),
            'hikari_acquire_total_s': acquire.get('TOTAL_TIME'),
            'heap_used_bytes': self.metric('jvm.memory.used', 'area:heap').get('VALUE'),
            'gc_pause_count': gc.get('COUNT', 0.0),
            'gc_pause_total_s': gc.get('TOTAL_TIME', 0.0),
        })

    def sample_docker(self, t):
        out = sh(['docker', 'stats', '--no-stream', '--format', '{{json .}}', APP_CONTAINER, DB_CONTAINER]).stdout
        row = {'t': t}
        for line in out.splitlines():
            stat = json.loads(line)
            prefix = 'app' if stat['Name'] == APP_CONTAINER else 'db'
            row[f'{prefix}_cpu_pct'] = float(stat['CPUPerc'].rstrip('%')) / CPU_LIMIT
            row[f'{prefix}_mem_mib'] = to_mib(stat['MemUsage'].split('/')[0].strip())
        self.docker.append(row)

    def sample_db(self, t):
        names = ', '.join(f"'{name}'" for name in LOCK_STATUS)
        rows = mysql(f'SHOW GLOBAL STATUS WHERE Variable_name IN ({names})')
        self.db.append({'t': t, **{name: float(value) for name, value in rows}})


def to_mib(text):
    units = {'B': 1 / 2 ** 20, 'KiB': 1 / 1024, 'MiB': 1, 'GiB': 1024, 'kB': 1000 / 2 ** 20, 'MB': 1e6 / 2 ** 20, 'GB': 1e9 / 2 ** 20}
    for unit in sorted(units, key=len, reverse=True):
        if text.endswith(unit):
            return float(text[:-len(unit)]) * units[unit]
    return float('nan')


def read_k6(run_dir):
    frame = pd.read_csv(run_dir / 'k6.csv', usecols=['metric_name', 'timestamp', 'metric_value', 'name', 'status'],
                        dtype={'name': str, 'status': str}, low_memory=False)
    if frame['timestamp'].max() > 1e11:
        frame['timestamp'] = frame['timestamp'] / 1000.0
    return frame


def integrity():
    duplicate_seats = mysql(f"""
        SELECT COUNT(*) FROM (
            SELECT hs.seat_id FROM hold_seat hs JOIN hold h ON h.id = hs.hold_id
            WHERE h.showtime_id = {MAIN_SHOWTIME} AND h.status IN ('HELD', 'RESERVED')
            GROUP BY hs.seat_id HAVING COUNT(*) > 1) d""")[0][0]
    duplicate_members = mysql(f"""
        SELECT COUNT(*) FROM (
            SELECT member_id FROM hold
            WHERE showtime_id = {MAIN_SHOWTIME} AND status = 'HELD'
            GROUP BY member_id HAVING COUNT(*) > 1) d""")[0][0]
    holds, held_seats = mysql(f"""
        SELECT COUNT(DISTINCT h.id), COUNT(hs.id) FROM hold h JOIN hold_seat hs ON hs.hold_id = h.id
        WHERE h.showtime_id = {MAIN_SHOWTIME} AND h.status = 'HELD'""")[0]
    return {'dup_seats': int(duplicate_seats), 'dup_members': int(duplicate_members),
            'db_holds': int(holds), 'db_held_seats': int(held_seats)}


def query_digests():
    rows = mysql(f"""
        SELECT REPLACE(REPLACE(DIGEST_TEXT, '\\t', ' '), '\\n', ' '), COUNT_STAR,
               ROUND(AVG_TIMER_WAIT / 1e9, 3), ROUND(MAX_TIMER_WAIT / 1e9, 3),
               ROUND(SUM_TIMER_WAIT / 1e9, 1), ROUND(SUM_LOCK_TIME / 1e9, 1), SUM_ROWS_EXAMINED
        FROM performance_schema.events_statements_summary_by_digest
        WHERE SCHEMA_NAME = '{ENV["MYSQL_DATABASE"]}' AND DIGEST_TEXT IS NOT NULL
        ORDER BY SUM_TIMER_WAIT DESC LIMIT 12""")
    columns = ['digest', 'count', 'avg_ms', 'max_ms', 'total_ms', 'lock_ms', 'rows_examined']
    digests = [dict(zip(columns, row)) for row in rows]
    for digest in digests:
        if float(digest['max_ms']) > 1e9:
            digest['max_ms'] = ''
    return digests


def percentile(series, q):
    return float(np.percentile(series, q)) if len(series) else float('nan')


def summarize(run_dir, collector, started, ended):
    k6_frame = read_k6(run_dir)
    durations = k6_frame[k6_frame.metric_name == 'http_req_duration']
    seats = durations[durations.name == 'seats'].metric_value
    holds = durations[durations.name == 'hold']
    window = max(durations.timestamp.max() - durations.timestamp.min(), 1.0)
    counter = lambda metric: float(k6_frame[k6_frame.metric_name == metric].metric_value.sum())
    scenario = k6_frame[k6_frame.metric_name == 'scenario_success'].metric_value
    requests = len(durations)

    summary = {
        'requests': requests,
        'window_s': round(window, 1),
        'seats_requests': len(seats),
        'hold_requests': len(holds),
        'seats_p50_ms': percentile(seats, 50), 'seats_p95_ms': percentile(seats, 95), 'seats_p99_ms': percentile(seats, 99),
        'hold_p50_ms': percentile(holds.metric_value, 50), 'hold_p95_ms': percentile(holds.metric_value, 95),
        'hold_p99_ms': percentile(holds.metric_value, 99),
        'rps': requests / window,
        'hold_success': int(counter('hold_success')),
        'hold_conflict': int(counter('hold_conflict')),
        'hold_tps': counter('hold_success') / window,
        'system_errors': int(counter('system_errors')),
        'error_rate': counter('system_errors') / requests if requests else float('nan'),
        'status_5xx': int(durations.status.str.startswith('5').sum()),
        'status_0': int((durations.status == '0').sum()),
        'scenarios': len(scenario),
        'scenario_success_rate': float(scenario.mean()) if len(scenario) else float('nan'),
    }
    summary.update(integrity())
    summary['integrity_ok'] = (summary['dup_seats'] == 0 and summary['dup_members'] == 0
                               and summary['db_held_seats'] == 2 * summary['db_holds'])
    summary['lost_hold_responses'] = summary['db_holds'] - summary['hold_success']
    summary.update(resource_summary(collector, started, ended))

    timeline = per_second(k6_frame, collector, started)
    timeline.to_csv(run_dir / 'timeline.csv', index=False)
    pd.DataFrame(query_digests()).to_csv(run_dir / 'queries.csv', index=False)
    for name in ('app', 'docker', 'db'):
        pd.DataFrame(getattr(collector, name)).to_csv(run_dir / f'collector_{name}.csv', index=False)
    (run_dir / 'summary.json').write_text(json.dumps(summary, indent=2, default=float), encoding='utf-8')
    return summary


def resource_summary(collector, started, ended):
    within = lambda rows: pd.DataFrame([r for r in rows if started <= r['t'] <= ended])
    app, docker, db = within(collector.app), within(collector.docker), within(collector.db)
    result = {}
    if not docker.empty:
        for prefix in ('app', 'db'):
            result[f'{prefix}_cpu_avg_pct'] = docker[f'{prefix}_cpu_pct'].mean()
            result[f'{prefix}_cpu_max_pct'] = docker[f'{prefix}_cpu_pct'].max()
            result[f'{prefix}_mem_max_mib'] = docker[f'{prefix}_mem_mib'].max()
    if not app.empty:
        result['hikari_pending_avg'] = app.hikari_pending.mean()
        result['hikari_pending_max'] = app.hikari_pending.max()
        result['hikari_active_max'] = app.hikari_active.max()
        acquire_count = app.hikari_acquire_count.iloc[-1] - app.hikari_acquire_count.iloc[0]
        acquire_total = app.hikari_acquire_total_s.iloc[-1] - app.hikari_acquire_total_s.iloc[0]
        result['hikari_acquire_avg_ms'] = acquire_total / acquire_count * 1000 if acquire_count else float('nan')
        result['heap_max_mib'] = app.heap_used_bytes.max() / 2 ** 20
        result['gc_pause_count'] = app.gc_pause_count.iloc[-1] - app.gc_pause_count.iloc[0]
        result['gc_pause_total_ms'] = (app.gc_pause_total_s.iloc[-1] - app.gc_pause_total_s.iloc[0]) * 1000
        result['app_samples'] = len(app)
    if not db.empty:
        result['db_threads_running_max'] = db.Threads_running.max()
        result['db_qps'] = (db.Questions.iloc[-1] - db.Questions.iloc[0]) / max(db.t.iloc[-1] - db.t.iloc[0], 1)
        waits = db.Innodb_row_lock_waits.iloc[-1] - db.Innodb_row_lock_waits.iloc[0]
        lock_ms = db.Innodb_row_lock_time.iloc[-1] - db.Innodb_row_lock_time.iloc[0]
        result['db_row_lock_waits'] = waits
        result['db_row_lock_time_ms'] = lock_ms
        result['db_row_lock_avg_ms'] = lock_ms / waits if waits else 0.0
    return result


def per_second(k6_frame, collector, started):
    reqs = k6_frame[k6_frame.metric_name == 'http_reqs']
    rps = reqs.groupby((reqs.timestamp - started).astype(int)).size().rename('rps')
    holds = k6_frame[k6_frame.metric_name == 'hold_success']
    tps = holds.groupby((holds.timestamp - started).astype(int)).metric_value.sum().rename('hold_tps')
    frames = [rps, tps]
    for rows in (collector.app, collector.docker, collector.db):
        frame = pd.DataFrame(rows)
        if frame.empty:
            continue
        frame['sec'] = (frame.pop('t') - started).astype(int)
        frames.append(frame.groupby('sec').mean())
    timeline = pd.concat(frames, axis=1).sort_index()
    timeline.index.name = 'sec'
    timeline = timeline[timeline.index >= 0].reset_index()
    timeline[['rps', 'hold_tps']] = timeline[['rps', 'hold_tps']].fillna(0)
    if 'gc_pause_total_s' in timeline:
        timeline['gc_pause_ms_per_s'] = timeline.gc_pause_total_s.ffill().diff().clip(lower=0) * 1000
    return timeline


def measure(run_dir, script, env):
    run_dir.mkdir(parents=True, exist_ok=True)
    reset()
    warmup(run_dir)
    with Collector() as collector:
        time.sleep(2)
        started = time.time()
        exit_code = k6(script, run_dir, env)
        ended = time.time()
        time.sleep(2)
    summary = summarize(run_dir, collector, started, ended)
    summary['k6_exit_code'] = exit_code
    return summary


def steady(session):
    rows = []
    for vus in STEADY_VUS:
        log(f'정상 부하 VU {vus} 시작 ({STEADY_DURATION})')
        summary = measure(session / f'steady-vu{vus:03d}', 'steady.js',
                          {'VUS': vus, 'DURATION': STEADY_DURATION, 'SHOWTIME_ID': MAIN_SHOWTIME})
        summary['vus'] = vus
        summary['meets_spec'] = (summary['seats_p95_ms'] <= STEADY_SEATS_P95_TARGET
                                 and summary['hold_p95_ms'] <= STEADY_HOLD_P95_TARGET
                                 and summary['error_rate'] < STEADY_ERROR_RATE_TARGET and summary['integrity_ok'])
        summary['meets_adr'] = (summary['seats_p95_ms'] <= PEAK_P95_TARGET and summary['hold_p95_ms'] <= PEAK_P95_TARGET
                                and summary['system_errors'] == 0 and summary['integrity_ok'])
        log(f"  조회 p95 {summary['seats_p95_ms']:.0f}ms, 선점 p95 {summary['hold_p95_ms']:.0f}ms, "
            f"RPS {summary['rps']:.1f}, 오류 {summary['system_errors']}, 정합성 {summary['integrity_ok']}")
        rows.append(summary)
    export_steady(session, rows)


def export_steady(session, rows):
    RESULTS.mkdir(parents=True, exist_ok=True)
    frame = pd.DataFrame(rows)
    frame.insert(0, 'vus', frame.pop('vus'))
    frame.to_csv(RESULTS / 'steady_summary.csv', index=False, float_format='%.4f')
    timelines, queries = [], []
    for vus in frame.vus:
        run_dir = session / f'steady-vu{vus:03d}'
        timelines.append(pd.read_csv(run_dir / 'timeline.csv').assign(vus=vus))
        queries.append(pd.read_csv(run_dir / 'queries.csv').assign(vus=vus))
    pd.concat(timelines).to_csv(RESULTS / 'steady_timeseries.csv', index=False, float_format='%.4f')
    pd.concat(queries).to_csv(RESULTS / 'steady_queries.csv', index=False)


def peak(session):
    trials = []

    def trial(search, n):
        log(f'오픈 피크 탐색 {search}: 활성 회원 {n}명')
        summary = measure(session / f'peak-s{search}-n{n:04d}', 'peak.js',
                          {'ACTIVE_MEMBERS': n, 'SHOWTIME_ID': MAIN_SHOWTIME})
        summary.update({'search': search, 'active_members': n})
        summary['passed'] = bool(summary['seats_p95_ms'] <= PEAK_P95_TARGET and summary['hold_p95_ms'] <= PEAK_P95_TARGET
                                 and summary['system_errors'] == 0 and summary['integrity_ok'])
        log(f"  조회 p95 {summary['seats_p95_ms']:.0f}ms, 선점 p95 {summary['hold_p95_ms']:.0f}ms, "
            f"오류 {summary['system_errors']}, 정합성 {summary['integrity_ok']} → {'통과' if summary['passed'] else '실패'}")
        trials.append(summary)
        pd.DataFrame(trials).to_csv(RESULTS / 'peak_trials.csv', index=False, float_format='%.4f')
        return summary['passed']

    def find_n_max(search, low, high):
        passed, failed = 0, None
        if trial(search, low):
            passed, n = low, high or low * 2
            while failed is None and n <= PEAK_CAP:
                if trial(search, n):
                    passed, n = n, n * 2
                else:
                    failed = n
            if failed is None:
                return passed, False
        else:
            failed, n = low, low // 2
            while n >= PEAK_RESOLUTION:
                if trial(search, n):
                    passed = n
                    break
                failed, n = n, n // 2
        while failed - passed > PEAK_RESOLUTION:
            mid = (passed + failed) // 2
            if trial(search, mid):
                passed = mid
            else:
                failed = mid
        return passed, True

    RESULTS.mkdir(parents=True, exist_ok=True)
    first, bounded = find_n_max(1, PEAK_START, None)
    results = [{'search': 1, 'n_max': first, 'bounded': bounded}]
    for search in range(2, PEAK_SEARCHES + 1):
        n_max, bounded = find_n_max(search, max(PEAK_RESOLUTION, round(first * 0.8)), round(first * 1.2))
        results.append({'search': search, 'n_max': n_max, 'bounded': bounded})
    values = [r['n_max'] for r in results]
    spread = (max(values) - min(values)) / min(values) if min(values) else float('nan')
    report = {'searches': results, 'spread': spread, 'admission': int(min(values) * (1 - spread)) if min(values) else 0}
    (RESULTS / 'peak_result.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    log(f'N_max {values}, 편차 {spread:.1%}, 입장 인원 {report["admission"]}')


def keep_awake():
    if os.name == 'nt':
        import ctypes
        es_continuous, es_system_required = 0x80000000, 0x00000001
        ctypes.windll.kernel32.SetThreadExecutionState(es_continuous | es_system_required)


def main():
    keep_awake()
    parser = argparse.ArgumentParser(description='TicketFlow 부하 테스트를 실행하고 결과를 docs/performance/에 저장한다')
    parser.add_argument('scenario', choices=['steady', 'peak', 'all'])
    parser.add_argument('--skip-build', action='store_true')
    args = parser.parse_args()

    session = OUT / datetime.now().strftime('%Y%m%d-%H%M%S')
    session.mkdir(parents=True, exist_ok=True)
    if not args.skip_build:
        build()
    if args.scenario in ('steady', 'all'):
        steady(session)
    if args.scenario in ('peak', 'all'):
        peak(session)
    sh(COMPOSE + ['down', '-v'], cwd=ROOT)
    log(f'완료: 원본 {session}, 집계 {RESULTS}')


if __name__ == '__main__':
    sys.exit(main())
