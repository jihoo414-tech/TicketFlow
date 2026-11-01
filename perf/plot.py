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
    if (RESULTS / 'peak_result.json').exists():
        plot_peak(pd.read_csv(RESULTS / 'peak_trials.csv'),
                  json.loads((RESULTS / 'peak_result.json').read_text(encoding='utf-8')))


if __name__ == '__main__':
    main()
