import { useCallback, useState } from 'react';
import { api, type MyReservation } from '../api';
import StatusMessage from '../components/StatusMessage';
import Toast from '../components/Toast';
import { formatDateTime, formatPrice, formatSeat, parseLocalDateTime } from '../format';
import { toApiError, useLoad } from '../useLoad';

function isCancellable(reservation: MyReservation, cancelled: boolean, now: number): boolean {
  return !cancelled && reservation.status === 'RESERVED' && now < parseLocalDateTime(reservation.startsAt).getTime();
}

export default function ReservationListPage() {
  const [reloadKey, setReloadKey] = useState(0);
  const state = useLoad(api.myReservations, [reloadKey]);
  const [cancellingId, setCancellingId] = useState<number | null>(null);
  const [cancelledIds, setCancelledIds] = useState<ReadonlySet<number>>(new Set());
  const [notice, setNotice] = useState<string | null>(null);
  const dismissNotice = useCallback(() => setNotice(null), []);
  const now = Date.now();

  const cancelReservation = async (reservationId: number) => {
    if (cancellingId !== null) return;
    setCancellingId(reservationId);
    try {
      await api.cancelReservation(reservationId);
      setCancelledIds(ids => new Set(ids).add(reservationId));
      setNotice('예매를 취소했습니다.');
    } catch (error) {
      setNotice(toApiError(error).message);
    } finally {
      setCancellingId(null);
      setReloadKey(key => key + 1);
    }
  };

  return (
    <section>
      <Toast message={notice} onDismiss={dismissNotice} />
      <h1 className="mb-6 text-2xl font-bold">내 예매</h1>
      {state.status === 'loading' && <StatusMessage>불러오는 중...</StatusMessage>}
      {state.status === 'error' && <StatusMessage error>예매 목록을 불러오지 못했습니다.</StatusMessage>}
      {state.status === 'done' && state.data.length === 0 && <StatusMessage>예매 내역이 없습니다.</StatusMessage>}
      {state.status === 'done' && state.data.length > 0 && (
        <ul className="space-y-3">
          {state.data.map(reservation => {
            const cancelled = reservation.status === 'RESERVATION_CANCELLED' || cancelledIds.has(reservation.id);
            return (
              <li
                key={reservation.id}
                className={`flex flex-wrap items-center justify-between gap-4 rounded-xl border border-gray-200 bg-white px-5 py-4 ${cancelled ? 'opacity-60' : ''}`}
              >
                <div>
                  <p className="flex items-center gap-2 font-semibold">
                    {reservation.performanceTitle}
                    {cancelled ? (
                      <span className="rounded-full bg-gray-100 px-3 py-0.5 text-xs font-semibold text-gray-600">취소됨</span>
                    ) : (
                      <span className="rounded-full bg-violet-100 px-3 py-0.5 text-xs font-semibold text-violet-700">예매됨</span>
                    )}
                  </p>
                  <p className="mt-1 text-sm text-gray-600">{formatDateTime(reservation.startsAt)}</p>
                  <p className="mt-1 text-sm text-gray-500">
                    {reservation.seats.map(formatSeat).join(', ')} · {formatPrice(reservation.totalPrice)}
                  </p>
                </div>
                {isCancellable(reservation, cancelled, now) && (
                  <button
                    type="button"
                    onClick={() => cancelReservation(reservation.id)}
                    disabled={cancellingId !== null}
                    className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-700 hover:border-violet-400 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {cancellingId === reservation.id ? '취소 중...' : '예매 취소'}
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
