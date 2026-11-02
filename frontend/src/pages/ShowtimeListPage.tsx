import { useCallback, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { api } from '../api';
import StatusMessage from '../components/StatusMessage';
import Toast from '../components/Toast';
import { formatDateTime, formatPrice, isBookingOpen } from '../format';
import { useLoad } from '../useLoad';

async function loadPerformanceShowtimes(performanceId: string) {
  const [performances, showtimes] = await Promise.all([api.performances(), api.showtimes(performanceId)]);
  const performance = performances.find(candidate => String(candidate.id) === performanceId);
  return { title: performance?.title ?? '공연', showtimes };
}

export default function ShowtimeListPage() {
  const { performanceId = '' } = useParams();
  const state = useLoad(() => loadPerformanceShowtimes(performanceId), [performanceId]);
  const now = Date.now();
  const location = useLocation();
  const [notice, setNotice] = useState<string | null>((location.state as { notice?: string } | null)?.notice ?? null);
  const dismissNotice = useCallback(() => setNotice(null), []);

  return (
    <section>
      <Toast message={notice} onDismiss={dismissNotice} />
      <Link to="/" className="text-sm text-gray-500 hover:text-violet-700">← 공연 목록</Link>
      {state.status === 'loading' && <div className="mt-4"><StatusMessage>불러오는 중...</StatusMessage></div>}
      {state.status === 'error' && (
        <div className="mt-4">
          <StatusMessage error>
            {state.error.status === 404 ? '존재하지 않는 공연입니다.' : '회차 목록을 불러오지 못했습니다.'}
          </StatusMessage>
        </div>
      )}
      {state.status === 'done' && (
        <>
          <h1 className="mt-2 mb-6 text-2xl font-bold">{state.data.title}</h1>
          {state.data.showtimes.length === 0 && <StatusMessage>예정된 회차가 없습니다.</StatusMessage>}
          <ul className="space-y-3">
            {state.data.showtimes.map(showtime => {
              const open = isBookingOpen(showtime, now);
              return (
                <li key={showtime.id}>
                  <Link
                    to={`/performances/${performanceId}/showtimes/${showtime.id}/queue`}
                    className="flex items-center justify-between rounded-xl border border-gray-200 bg-white px-5 py-4 transition hover:border-violet-400 hover:shadow-sm"
                  >
                    <div>
                      <p className="font-semibold">{formatDateTime(showtime.startsAt)}</p>
                      <p className="mt-1 text-sm text-gray-500">{formatPrice(showtime.price)}</p>
                    </div>
                    {open ? (
                      <span className="rounded-full bg-violet-100 px-3 py-1 text-xs font-semibold text-violet-700">예매 가능</span>
                    ) : (
                      <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-semibold text-gray-600">
                        오픈 전 · {formatDateTime(showtime.bookingOpenAt)}
                      </span>
                    )}
                  </Link>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </section>
  );
}
