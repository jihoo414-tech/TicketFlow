import json
from pathlib import Path

import matplotlib

matplotlib.use('Agg')
import matplotlib.pyplot as plt
import pandas as pd
from matplotlib import font_manager

RESULTS = Path(__file__).resolve().parent.parent / 'docs' / 'performance'

SURFACE = '#fcfcfb'
TEXT = '#0b0b0b'
TEXT_SECONDARY = '#52514e'
GRID = '#e4e3df'
REFERENCE = '#8a8984'
SERIES = ['#2a78d6', '#eb6834', '#1baf7a']

STEADY_CONDITION = ('측정 조건: 로컬 Docker · 앱 2 vCPU/850 MiB(-Xmx512m) · MySQL 8.4 2 vCPU/1 GiB · HikariCP 10 · '
                    '회차 1개 6,000석 · VU별 3분 유지 · 조회 3회(4~6초 간격) 후 2석 선점 · 단계마다 DB 초기화와 워밍업')
PEAK_CONDITION = ('측정 조건: 로컬 Docker · 앱 2 vCPU/850 MiB · MySQL 2 vCPU/1 GiB · HikariCP 10 · 6,000석 · '
                  '활성 회원 N명 동시 출발 → 조회 → 0~0.5초 → 2석 선점(409면 재조회, 최대 3회) · 실행마다 DB 초기화와 워밍업')


def setup_font():
    for name in ('Malgun Gothic', 'AppleGothic', 'NanumGothic', 'Noto Sans CJK KR'):
        if any(font.name == name for font in font_manager.fontManager.ttflist):
            plt.rcParams['font.family'] = name
            break
    plt.rcParams.update({
        'axes.unicode_minus': False,
        'figure.facecolor': SURFACE, 'axes.facecolor': SURFACE, 'savefig.facecolor': SURFACE,
        'text.color': TEXT, 'axes.labelcolor': TEXT_SECONDARY, 'axes.edgecolor': GRID,
        'xtick.color': TEXT_SECONDARY, 'ytick.color': TEXT_SECONDARY,
        'axes.grid': True, 'grid.color': GRID, 'grid.linewidth': 0.8, 'axes.axisbelow': True,
        'axes.spines.top': False, 'axes.spines.right': False,
        'font.size': 10.5, 'axes.titlesize': 12, 'axes.titleweight': 'bold', 'legend.frameon': False,
    })


def figure(title, condition, ncols=1, nrows=1, size=(12, 6), **kwargs):
    fig, axes = plt.subplots(nrows, ncols, figsize=size, **kwargs)
    fig.suptitle(title, x=0.01, ha='left', fontsize=15, fontweight='bold')
    fig.text(0.01, 0.005, condition, fontsize=8, color=TEXT_SECONDARY, ha='left', va='bottom', wrap=True)
    return fig, axes


def save(fig, name):
    fig.tight_layout(rect=(0, 0.05, 1, 0.95))
    fig.savefig(RESULTS / name, dpi=200)
    plt.close(fig)
    print(f'saved {RESULTS / name}')


def reference_line(ax, y, label):
    ax.axhline(y, color=REFERENCE, linestyle='--', linewidth=1.2, zorder=1)
    ax.annotate(label, xy=(1, y), xycoords=('axes fraction', 'data'), xytext=(-4, 4), textcoords='offset points',
                ha='right', va='bottom', fontsize=9, color=TEXT_SECONDARY)


def vu_axis(ax, vus):
    ax.set_xticks(vus)
    ax.set_xlabel('동시 VU (명)')


def label_points(ax, xs, ys, fmt, offset=(0, 7), ha='center', va='bottom'):
    for x, y in zip(xs, ys):
        ax.annotate(fmt.format(y), (x, y), xytext=offset, textcoords='offset points', ha=ha, va=va, fontsize=8.5,
                    color=TEXT)


def plot_latency(steady):
    fig, ax = figure('VU별 좌석 조회·선점 응답 시간 p95', STEADY_CONDITION)
    for column, label, color, offset, ha, va in (('seats_p95_ms', '좌석 조회 p95', SERIES[0], (-6, 7), 'right', 'bottom'),
                                                 ('hold_p95_ms', '선점 p95', SERIES[1], (8, -2), 'left', 'top')):
        ax.plot(steady.vus, steady[column], marker='o', markersize=7, linewidth=2, color=color, label=label)
        label_points(ax, steady.vus, steady[column], '{:.0f}', offset, ha, va)
    reference_line(ax, 300, '조회 목표 300ms')
    reference_line(ax, 500, '선점 목표 500ms')
    vu_axis(ax, steady.vus)
    ax.set_ylabel('응답 시간 p95 (ms)')
    ax.set_ylim(0, max(600, steady[['seats_p95_ms', 'hold_p95_ms']].max().max() * 1.15))
    ax.legend(loc='upper left')
    save(fig, '1-latency-p95.png')


def plot_throughput(steady):
    fig, (left, right) = figure('VU별 처리량', STEADY_CONDITION, ncols=2)
    saturated = (steady.error_rate > 0.1).tolist()
    for ax, column, title, unit, color, note in (
            (left, 'rps', '전체 요청 처리량', 'RPS (요청/초)', SERIES[0], '재시도 폭주 포함\n(처리 능력 아님)'),
            (right, 'hold_tps', '성공 선점 처리량', 'TPS (성공 선점/초)', SERIES[2], '포화로 급감')):
        ax.bar(steady.vus.astype(str), steady[column], color=color, width=0.6, edgecolor=SURFACE, linewidth=2)
        for i, value in enumerate(steady[column]):
            text = f'{note}\n{value:.1f}' if saturated[i] else f'{value:.1f}'
            ax.annotate(text, (i, value), xytext=(0, 4), textcoords='offset points', ha='center', fontsize=9)
        ax.set_ylim(0, steady[column].max() * 1.25)
        ax.set_title(title, loc='left')
        ax.set_xlabel('동시 VU (명)')
        ax.set_ylabel(unit)
        ax.grid(axis='x', visible=False)
    save(fig, '2-throughput.png')


def plot_cpu(steady):
    fig, ax = figure('VU별 서버·DB CPU 사용률', STEADY_CONDITION)
    positions = range(len(steady))
    width = 0.36
    for offset, prefix, label, color in ((-width / 2, 'app', '서버(Spring Boot)', SERIES[0]),
                                         (width / 2, 'db', 'DB(MySQL)', SERIES[1])):
        xs = [p + offset for p in positions]
        ax.bar(xs, steady[f'{prefix}_cpu_avg_pct'], width=width, color=color, edgecolor=SURFACE, linewidth=2,
               label=f'{label} 평균')
        ax.scatter(xs, steady[f'{prefix}_cpu_max_pct'], marker='_', s=260, linewidths=2.5, color=TEXT, zorder=3)
        for x, value in zip(xs, steady[f'{prefix}_cpu_avg_pct']):
            ax.annotate(f'{value:.0f}%', (x, value), xytext=(0, 3), textcoords='offset points', ha='center', fontsize=8.5)
    ax.scatter([], [], marker='_', s=260, linewidths=2.5, color=TEXT, label='구간 최대(약 2초 샘플)')
    reference_line(ax, 100, '컨테이너 한도 2 vCPU')
    ax.set_xticks(list(positions), steady.vus.astype(str))
    ax.set_xlabel('동시 VU (명)')
    ax.set_ylabel('CPU 사용률 (컨테이너 한도 2 vCPU 대비 %)')
    ax.set_ylim(0, max(110, steady[['app_cpu_max_pct', 'db_cpu_max_pct']].max().max() * 1.1))
    ax.grid(axis='x', visible=False)
    ax.legend(loc='upper left')
    save(fig, '3-cpu.png')


def plot_timeseries(timeline):
    fig, (memory, pool, gc) = figure('시간별 메모리·HikariCP 대기·GC 변화', STEADY_CONDITION, nrows=3, size=(12, 9),
                                     sharex=True)
    offset, boundaries = 0, []
    parts = []
    for vus, part in timeline.groupby('vus', sort=True):
        part = part.assign(x=part.sec + offset)
        parts.append(part)
        boundaries.append((offset, part.x.max(), vus))
        offset = part.x.max() + 15
    data = pd.concat(parts)

    for column, label, color, scale in (('app_mem_mib', '서버 컨테이너 메모리', SERIES[0], 1),
                                        ('heap_used_bytes', 'JVM 힙 사용량', SERIES[2], 1 / 2 ** 20),
                                        ('db_mem_mib', 'DB 컨테이너 메모리', SERIES[1], 1)):
        for _, part in data.groupby('vus'):
            part = part.dropna(subset=[column])
            memory.plot(part.x, part[column] * scale, color=color, linewidth=1.6)
        memory.plot([], [], color=color, linewidth=2, label=label)
    memory.set_ylabel('메모리 (MiB)')
    memory.legend(loc='upper left', ncols=3)

    for column, label, color in (('hikari_pending', '커넥션 대기 스레드(pending)', SERIES[1]),
                                 ('hikari_active', '사용 중 커넥션(active)', SERIES[0])):
        for _, part in data.groupby('vus'):
            part = part.dropna(subset=[column])
            pool.plot(part.x, part[column], color=color, linewidth=1.6)
        pool.plot([], [], color=color, linewidth=2, label=label)
    pool.axhline(10, color=REFERENCE, linestyle='--', linewidth=1)
    pool.annotate('풀 크기 10', xy=(0, 10), xycoords=('axes fraction', 'data'), xytext=(4, 3),
                  textcoords='offset points', fontsize=8.5, color=TEXT_SECONDARY)
    pool.set_ylabel('개수')
    pool.legend(loc='upper left', ncols=2)

    for _, part in data.groupby('vus'):
        gc.bar(part.x, part.gc_pause_ms_per_s.fillna(0), width=1.0, color=SERIES[0])
    gc.set_ylabel('GC 정지 시간 (ms/초)')
    gc.set_xlabel('경과 시간 (초, VU 단계를 순서대로 이어 붙임)')

    for _, part in data.groupby('vus'):
        sampled = part.dropna(subset=['hikari_pending'])
        if not sampled.empty and sampled.x.max() < part.x.max() - 10:
            for ax in (pool, gc):
                ax.axvline(sampled.x.max(), color=REFERENCE, linestyle=':', linewidth=1.2)
                ax.annotate('이후 Actuator 응답 없음\n(앱 포화)', xy=(sampled.x.max(), 0.95),
                            xycoords=('data', 'axes fraction'), xytext=(5, 0), textcoords='offset points',
                            va='top', fontsize=8.5, color=TEXT_SECONDARY)

    for start, end, vus in boundaries:
        for ax in (memory, pool, gc):
            ax.axvspan(start, end, color='#f1f0ec', zorder=0)
        memory.annotate(f'VU {vus}', xy=((start + end) / 2, 1.02), xycoords=('data', 'axes fraction'),
                        ha='center', fontsize=9, color=TEXT_SECONDARY)
    save(fig, '4-timeseries.png')


def plot_errors(steady):
    fig, (left, right) = figure('VU별 오류율·시나리오 성공률', STEADY_CONDITION, ncols=2)
    error_pct = steady.error_rate * 100
    left.bar(steady.vus.astype(str), error_pct, color=SERIES[1], width=0.6)
    for i, value in enumerate(error_pct):
        text = f'재시도 폭주 포함\n{value:.2f}%' if steady.error_rate.iloc[i] > 0.1 else f'{value:.2f}%'
        left.annotate(text, (i, value), xytext=(0, 4), textcoords='offset points', ha='center', fontsize=9)
    left.axhline(0.1, color=REFERENCE, linestyle='--', linewidth=1.2, zorder=1)
    left.annotate('목표 0.1% 미만(바닥의 점선)', xy=(0, 0.5), xycoords=('axes fraction', 'axes fraction'), xytext=(6, 0),
                  textcoords='offset points', fontsize=9, color=TEXT_SECONDARY)
    left.set_ylim(0, max(0.5, error_pct.max() * 1.3))
    left.set_title('시스템 오류율 (5xx·2초 타임아웃·예상 밖 4xx)', loc='left', fontsize=10.5)
    left.set_xlabel('동시 VU (명)')
    left.set_ylabel('오류율 (%)')
    left.grid(axis='x', visible=False)

    success_pct = steady.scenario_success_rate * 100
    right.plot(steady.vus.astype(str), success_pct, marker='o', markersize=7, linewidth=2, color=SERIES[0])
    for i, value in enumerate(success_pct):
        right.annotate(f'{value:.1f}%', (i, value), xytext=(0, -14), textcoords='offset points', ha='center',
                       fontsize=9)
    right.set_ylim(min(80, success_pct.min() - 5), 101)
    right.set_title('시나리오 성공률 (선점 성공 회원 / 시작한 회원)', loc='left', fontsize=10.5)
    right.set_xlabel('동시 VU (명)')
    right.set_ylabel('성공률 (%)')
    save(fig, '5-errors-success.png')


def plot_peak(trials, result):
    fig, ax = figure('오픈 피크: 활성 회원 수별 응답 시간 p95와 판정', PEAK_CONDITION)
    for column, label, color in (('seats_p95_ms', '좌석 조회 p95', SERIES[0]), ('hold_p95_ms', '선점 p95', SERIES[1])):
        for passed, marker in ((True, 'o'), (False, 'X')):
            part = trials[trials.passed == passed]
            ax.scatter(part.active_members, part[column], color=color, marker=marker, s=60, edgecolor=SURFACE,
                       linewidth=1.5, zorder=3)
        ax.plot([], [], color=color, marker='o', linestyle='', label=label)
    ax.scatter([], [], color=TEXT_SECONDARY, marker='o', label='통과')
    ax.scatter([], [], color=TEXT_SECONDARY, marker='X', label='실패(p95 초과·오류)')
    reference_line(ax, 500, '기준 p95 500ms')
    ax.set_xscale('log', base=2)
    ticks = sorted(trials.active_members.unique())
    ax.set_xticks(ticks, [str(t) for t in ticks], rotation=45)
    ax.minorticks_off()
    ax.set_xlabel('활성 회원 수 N (명, 로그 축)')
    ax.set_ylabel('응답 시간 p95 (ms)')
    values = ', '.join(str(s['n_max']) for s in result['searches'])
    ax.set_title(f'탐색별 N_max: {values} → 입장 인원 {result["admission"]}명 (편차 {result["spread"]:.1%})',
                 loc='left', fontsize=10.5, fontweight='normal', color=TEXT_SECONDARY)
    ax.legend(loc='upper left', ncols=2)
    save(fig, '6-peak-search.png')


def main():
    setup_font()
    if (RESULTS / 'steady_summary.csv').exists():
        steady = pd.read_csv(RESULTS / 'steady_summary.csv').sort_values('vus')
        plot_latency(steady)
        plot_throughput(steady)
        plot_cpu(steady)
        plot_errors(steady)
        plot_timeseries(pd.read_csv(RESULTS / 'steady_timeseries.csv'))
    if (RESULTS / 'peak_result.json').exists():
        plot_peak(pd.read_csv(RESULTS / 'peak_trials.csv'),
                  json.loads((RESULTS / 'peak_result.json').read_text(encoding='utf-8')))


if __name__ == '__main__':
    main()
