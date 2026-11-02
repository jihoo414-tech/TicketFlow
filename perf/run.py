import argparse
import csv
import json
import os
import re
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

import target as targets

ROOT = Path(__file__).resolve().parent.parent
PERF = ROOT / 'perf'
OUT = PERF / 'out'
RESULTS = ROOT / 'docs' / 'performance'
MAIN_SHOWTIME = 1
TARGET = None
SEATS_URI = re.compile(r'^/api/zones/\d+/seats$')
HOLD_URI = re.compile(r'^/api/showtimes/\d+/holds$')

WARMUP_SHOWTIME = 2
SEAT_COUNTS = [250, 500, 750, 1000]
SEATS_PER_ROW = 25
ZONES = [('A', 1, 8), ('B', 9, 17), ('C', 18, 25)]
WARMUP_SEATS = 2000

PEAK_START = 10
PEAK_RESOLUTION = 2
PEAK_P95_TARGET = 500
PEAK_SEARCHES = 3

LOCK_STATUS = ['Threads_running', 'Threads_connected', 'Questions',
               'Innodb_row_lock_waits', 'Innodb_row_lock_time', 'Innodb_row_lock_current_waits']


def log(message):
    print(f'[{datetime.now():%H:%M:%S}] {message}', flush=True)


def mysql(sql):
    return TARGET.mysql(sql)


def build():
    log('앱 jar와 이미지를 빌드한다')
    TARGET.build()


def fetch_json(path, timeout=1.0):
    with urllib.request.urlopen(TARGET.app_url + path, timeout=timeout) as res:
        return json.load(res)


def reset(seats):
    TARGET.reset_database_and_app()
    wait_for_app()
    seed_seats(MAIN_SHOWTIME, seats)
    seed_seats(WARMUP_SHOWTIME, WARMUP_SEATS)
    mysql('ANALYZE TABLE member, performance, showtime, seat, hold, hold_seat, reservation')


def wait_for_app():
    deadline = time.time() + 120
    while time.time() < deadline:
        try:
            if fetch_json('/actuator/health').get('status') == 'UP':
                return
        except (urllib.error.URLError, ConnectionError, TimeoutError, OSError):
            pass
        time.sleep(1)
    raise RuntimeError('앱이 120초 안에 뜨지 않았다')


def seed_seats(showtime_id, count):
    zones = ', '.join(f"({showtime_id}, '{name}', {order})" for order, (name, _, _) in enumerate(ZONES, start=1))
    zone_of_seat = ' '.join(f"WHEN MOD(n, {SEATS_PER_ROW}) + 1 <= {last} THEN '{name}'" for name, _, last in ZONES)
    mysql(f"""
        INSERT INTO zone (showtime_id, name, display_order) VALUES {zones};
        SET SESSION cte_max_recursion_depth = {count};
        INSERT INTO seat (showtime_id, zone_id, seat_row, seat_no)
        WITH RECURSIVE seq (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n < {count - 1})
        SELECT {showtime_id}, z.id, CAST(FLOOR(n / {SEATS_PER_ROW}) + 1 AS CHAR), MOD(n, {SEATS_PER_ROW}) + 1
        FROM seq JOIN zone z ON z.showtime_id = {showtime_id} AND z.name = CASE {zone_of_seat} END""")


def k6(script, run_dir, env):
    return TARGET.run_k6(script, run_dir, env)


def warmup(run_dir):
    warm_dir = run_dir / 'warmup'
    warm_dir.mkdir(parents=True, exist_ok=True)
    k6('warmup.js', warm_dir, {'SHOWTIME_ID': 2, 'MEMBER_OFFSET': 9000})
    try:
        mysql('TRUNCATE TABLE performance_schema.events_statements_summary_by_digest')
    except Exception as e:
        log(f'  쿼리 기록을 비우지 못했다(performance_schema 확인): {e}')


class Collector:
    def __init__(self):
        self.stop_event = threading.Event()
        self.app, self.docker, self.db = [], [], []
        self.threads = [threading.Thread(target=self.loop, args=(fn,), daemon=True)
                        for fn in (self.sample_app, self.sample_resources, self.sample_db)]

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
        usage = self.metric('hikaricp.connections.usage')
        self.app.append({
            't': t,
            'hikari_pending': self.metric('hikaricp.connections.pending').get('VALUE'),
            'hikari_active': self.metric('hikaricp.connections.active').get('VALUE'),
            'hikari_acquire_count': acquire.get('COUNT'),
            'hikari_acquire_total_s': acquire.get('TOTAL_TIME'),
            'hikari_usage_count': usage.get('COUNT'),
            'hikari_usage_total_s': usage.get('TOTAL_TIME'),
            'heap_used_bytes': self.metric('jvm.memory.used', 'area:heap').get('VALUE'),
            'gc_pause_count': gc.get('COUNT', 0.0),
            'gc_pause_total_s': gc.get('TOTAL_TIME', 0.0),
        })

    def sample_resources(self, t):
        row = TARGET.sample_resources(self.metric)
        if row:
            self.docker.append({'t': t, **row})

    def sample_db(self, t):
        names = ', '.join(f"'{name}'" for name in LOCK_STATUS)
        rows = mysql(f'SHOW GLOBAL STATUS WHERE Variable_name IN ({names})')
        self.db.append({'t': t, **{name: float(value) for name, value in rows}})


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
        WHERE SCHEMA_NAME = '{TARGET.database}' AND DIGEST_TEXT IS NOT NULL
        ORDER BY SUM_TIMER_WAIT DESC LIMIT 12""")
    columns = ['digest', 'count', 'avg_ms', 'max_ms', 'total_ms', 'lock_ms', 'rows_examined']
    digests = [dict(zip(columns, row)) for row in rows]
    for digest in digests:
        if float(digest['max_ms']) > 1e9:
            digest['max_ms'] = ''
    return digests


def percentile(series, q):
    return float(np.percentile(series, q)) if len(series) else float('nan')


def server_latencies(lines):
    seats, holds = [], []
    for line in lines:
        parts = line.split()
        if len(parts) < 5:
            continue
        method, uri, micros = parts[1], parts[2], parts[4]
        if method == 'GET' and SEATS_URI.match(uri):
            seats.append(int(micros) / 1000)
        elif method == 'POST' and HOLD_URI.match(uri):
            holds.append(int(micros) / 1000)
    return {
        'server_seats_requests': len(seats), 'server_hold_requests': len(holds),
        'server_seats_p50_ms': percentile(seats, 50), 'server_seats_p95_ms': percentile(seats, 95),
        'server_seats_p99_ms': percentile(seats, 99),
        'server_hold_p50_ms': percentile(holds, 50), 'server_hold_p95_ms': percentile(holds, 95),
        'server_hold_p99_ms': percentile(holds, 99),
    }


def summarize(run_dir, collector, started, ended, access_lines):
    k6_frame = read_k6(run_dir)
    durations = k6_frame[k6_frame.metric_name == 'http_req_duration']
    seats = durations[durations.name == 'seats'].metric_value
    zones = durations[durations.name == 'zones'].metric_value
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
        'zones_p95_ms': percentile(zones, 95),
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
    summary.update(server_latencies(access_lines))
    summary.update(integrity())
    summary['integrity_ok'] = (summary['dup_seats'] == 0 and summary['dup_members'] == 0
                               and summary['db_held_seats'] == 2 * summary['db_holds'])
    summary['lost_hold_responses'] = summary['db_holds'] - summary['hold_success']
    summary.update(resource_summary(collector, started, ended))
    summary.update(TARGET.cloud_metrics(started, ended))

    timeline = per_second(k6_frame, collector, started)
    timeline.to_csv(run_dir / 'timeline.csv', index=False)
    (run_dir / 'access.log').write_text('\n'.join(access_lines), encoding='utf-8')
    try:
        pd.DataFrame(query_digests()).to_csv(run_dir / 'queries.csv', index=False)
    except Exception as e:
        log(f'  쿼리 기록을 읽지 못했다(performance_schema 확인): {e}')
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
            if f'{prefix}_cpu_pct' in docker:
                result[f'{prefix}_cpu_avg_pct'] = docker[f'{prefix}_cpu_pct'].mean()
                result[f'{prefix}_cpu_max_pct'] = docker[f'{prefix}_cpu_pct'].max()
            if f'{prefix}_mem_mib' in docker:
                result[f'{prefix}_mem_max_mib'] = docker[f'{prefix}_mem_mib'].max()
    if not app.empty:
        result['hikari_pending_avg'] = app.hikari_pending.mean()
        result['hikari_pending_max'] = app.hikari_pending.max()
        result['hikari_active_max'] = app.hikari_active.max()
        acquire_count = app.hikari_acquire_count.iloc[-1] - app.hikari_acquire_count.iloc[0]
        acquire_total = app.hikari_acquire_total_s.iloc[-1] - app.hikari_acquire_total_s.iloc[0]
        result['hikari_acquire_avg_ms'] = acquire_total / acquire_count * 1000 if acquire_count else float('nan')
        usage_count = app.hikari_usage_count.iloc[-1] - app.hikari_usage_count.iloc[0]
        usage_total = app.hikari_usage_total_s.iloc[-1] - app.hikari_usage_total_s.iloc[0]
        result['hikari_usage_avg_ms'] = usage_total / usage_count * 1000 if usage_count else float('nan')
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


def measure(run_dir, script, env, seats):
    run_dir.mkdir(parents=True, exist_ok=True)
    reset(seats)
    warmup(run_dir)
    log_offset = len(TARGET.access_log())
    with Collector() as collector:
        time.sleep(2)
        started = time.time()
        exit_code = k6(script, run_dir, env)
        ended = time.time()
        time.sleep(2)
    summary = summarize(run_dir, collector, started, ended, TARGET.access_log()[log_offset:])
    summary['k6_exit_code'] = exit_code
    summary['seats'] = seats
    return summary


def run_trial(session, seats, label, tag, n, trials, csv_name, **extra):
    log(f'{seats}석 오픈 피크 {label}: 활성 회원 {n}명')
    summary = measure(session / f'peak-seats{seats:04d}-{tag}-n{n:04d}', 'peak.js',
                      {'ACTIVE_MEMBERS': n, 'SHOWTIME_ID': MAIN_SHOWTIME}, seats)
    summary.update({**extra, 'active_members': n})
    summary['passed'] = bool(summary['seats_p95_ms'] <= PEAK_P95_TARGET
                             and summary['hold_p95_ms'] <= PEAK_P95_TARGET
                             and summary['system_errors'] == 0 and summary['integrity_ok'])
    log(f"  k6 p95 조회 {summary['seats_p95_ms']:.0f}ms · 선점 {summary['hold_p95_ms']:.0f}ms "
        f"(서버 {summary['server_seats_p95_ms']:.0f} · {summary['server_hold_p95_ms']:.0f}ms), "
        f"커넥션 대기 {summary.get('hikari_acquire_avg_ms', float('nan')):.1f}ms, "
        f"커넥션 사용 {summary.get('hikari_usage_avg_ms', float('nan')):.1f}ms, "
        f"오류 {summary['system_errors']}, 정합성 {summary['integrity_ok']} → {'통과' if summary['passed'] else '실패'}")
    trials.append(summary)
    RESULTS.mkdir(parents=True, exist_ok=True)
    pd.DataFrame(trials).to_csv(RESULTS / csv_name, index=False, float_format='%.4f')
    return summary['passed']


def ladder(session, seats):
    cap = seats // 2
    trials = []
    for n in ladder_steps(cap):
        run_trial(session, seats, '단계', 'ladder', n, trials, f'peak_ladder_seats{seats:04d}.csv')


def ladder_steps(cap):
    steps, n = [], min(PEAK_START, cap)
    while n < cap:
        steps.append(n)
        n *= 2
    return steps + [cap]


def peak(session, seats):
    cap = seats // 2
    trials = []

    def trial(search, n):
        return run_trial(session, seats, f'탐색 {search}', f's{search}', n, trials,
                         f'peak_trials_seats{seats:04d}.csv', search=search)

    def find_n_max(search, low, high):
        passed, failed = 0, None
        if trial(search, low):
            passed, n = low, min(high or low * 2, cap)
            while failed is None and passed < cap:
                if trial(search, n):
                    passed, n = n, min(n * 2, cap)
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
    first, bounded = find_n_max(1, min(PEAK_START, cap), None)
    results = [{'search': 1, 'n_max': first, 'bounded': bounded}]
    for search in range(2, PEAK_SEARCHES + 1):
        n_max, bounded = find_n_max(search, max(PEAK_RESOLUTION, round(first * 0.8)), min(cap, round(first * 1.2)))
        results.append({'search': search, 'n_max': n_max, 'bounded': bounded})
    values = [r['n_max'] for r in results]
    spread = (max(values) - min(values)) / min(values) if min(values) else float('nan')
    admit_all = not any(r['bounded'] for r in results)
    admission = cap if admit_all else (int(min(values) * (1 - spread)) if min(values) else 0)
    report = {'seats': seats, 'cap': cap, 'searches': results, 'spread': spread,
              'admit_all': admit_all, 'admission': admission}
    (RESULTS / f'peak_result_seats{seats:04d}.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    verdict = f'상한 {cap}명까지 모두 통과 → 모두 들여보내도 된다' if admit_all else f'입장 인원 {admission}'
    log(f'{seats}석: N_max {values}, 편차 {spread:.1%}, {verdict}')


def keep_awake():
    if os.name == 'nt':
        import ctypes
        es_continuous, es_system_required = 0x80000000, 0x00000001
        ctypes.windll.kernel32.SetThreadExecutionState(es_continuous | es_system_required)


def main():
    keep_awake()
    parser = argparse.ArgumentParser(description='TicketFlow 부하 테스트를 실행하고 결과를 docs/performance/에 저장한다')
    parser.add_argument('scenario', choices=['peak', 'ladder'])
    parser.add_argument('--seats', type=int, nargs='+', choices=SEAT_COUNTS, default=SEAT_COUNTS)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--target', choices=['local', 'aws'], default='local')
    args = parser.parse_args()

    global TARGET, RESULTS
    TARGET = targets.create(args.target)
    if args.target == 'aws':
        RESULTS = RESULTS / 'aws'
    session = OUT / datetime.now().strftime(f'%Y%m%d-%H%M%S-{args.target}')
    session.mkdir(parents=True, exist_ok=True)
    if not args.skip_build and args.target == 'local':
        build()
    TARGET.open()
    try:
        for seats in args.seats:
            (ladder if args.scenario == 'ladder' else peak)(session, seats)
    finally:
        TARGET.close()
    log(f'완료: 원본 {session}, 집계 {RESULTS}')


if __name__ == '__main__':
    sys.exit(main())
