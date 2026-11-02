import { useEffect, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import StatusMessage from '../components/StatusMessage';
import Toast from '../components/Toast';
import { formatDateTime, formatRemaining, formatSeat, parseLocalDateTime } from '../format';
import { loadShowtime } from '../showtimeInfo';
import { useHoldPayment } from '../useHoldPayment';
import { useLoad } from '../useLoad';

const URGENT_MS = 60_000;
const primaryButton = 'rounded-lg bg-violet-600 px-5 py-2.5 font-semibold text-white hover:bg-violet-700 disabled:cursor-not-allowed disabled:bg-gray-300';

export default function PaymentPage() {
  const { performanceId = '', showtimeId = '' } = useParams();
  const info = useLoad(() => loadShowtime(performanceId, showtimeId), [performanceId, showtimeId]);
  const { now, hold, holdExpired, reservation, paying, cancelling, cancelled, notice, dismissNotice, submitPay, cancelHold } =
    useHoldPayment(showtimeId);
  const navigate = useNavigate();
  const showtimesPath = `/performances/${performanceId}`;

  useEffect(() => {
    if (cancelled) {
      navigate(showtimesPath, { replace: true, state: { notice: '선점을 취소했습니다. 다시 예매하려면 줄을 서 주세요.' } });
    }
  }, [cancelled, navigate, showtimesPath]);

  if (info.status === 'loading') {
    return <StatusMessage>불러오는 중...</StatusMessage>;
  }
  if (info.status === 'error' || !info.data.showtime) {
    return <StatusMessage error>존재하지 않는 회차입니다.</StatusMessage>;
  }

  const { title, showtime } = info.data;
  const seatsText = hold ? hold.seats.map(seat => `${hold.zoneName ? `${hold.zoneName} ` : ''}${formatSeat(seat)}`).join(', ') : '';
  const remainingMs = hold ? parseLocalDateTime(hold.expiresAt).getTime() - now : 0;

  const card = (body: ReactNode) => (
    <section className="mx-auto max-w-lg">
      <Toast message={notice} onDismiss={dismissNotice} />
      <h1 className="text-2xl font-bold">{title}</h1>
      <p className="mt-1 mb-6 text-gray-600">{formatDateTime(showtime.startsAt)}</p>
      <div className="rounded-xl border border-gray-200 bg-white p-6">{body}</div>
    </section>
  );

  if (reservation) {
    return card(
      <>
        <p className="text-lg font-semibold">예매가 완료되었습니다.</p>
        <p className="mt-2 text-gray-700">{seatsText}</p>
        <p className="mt-1 text-sm text-gray-500">예매 번호 {reservation.id}</p>
        <div className="mt-6 flex gap-3">
          <Link to="/reservations" className={primaryButton}>내 예매 보기</Link>
          <Link to={showtimesPath} className="rounded-lg border border-gray-300 px-4 py-2.5 font-semibold text-gray-700">회차 목록</Link>
        </div>
      </>,
    );
  }

  if (!hold || cancelled || holdExpired) {
    const message = holdExpired ? '결제 시간이 지나 좌석이 다시 열렸습니다.' : '선택한 좌석이 없습니다.';
    return card(
      <>
        <p className="text-gray-700">{message}</p>
        <Link to={showtimesPath} className={`mt-6 inline-block ${primaryButton}`}>회차 목록</Link>
      </>,
    );
  }

  return card(
    <>
      <p className="text-sm text-gray-500">선택 좌석</p>
      <p className="mt-1 text-lg font-semibold">{seatsText}</p>
      <div className="mt-6 flex items-end justify-between">
        <p className="text-sm text-gray-500">시간 안에 결제하지 않으면 좌석이 다시 열립니다.</p>
        <div className="text-right">
          <p className="text-xs text-gray-500">남은 시간</p>
          <p className={`text-2xl font-bold tabular-nums ${remainingMs <= URGENT_MS ? 'text-red-600' : 'text-violet-700'}`}>
            {formatRemaining(remainingMs)}
          </p>
        </div>
      </div>
      <div className="mt-6 flex justify-end gap-3">
        <button
          type="button"
          onClick={cancelHold}
          disabled={paying || cancelling}
          className="rounded-lg border border-gray-300 px-4 py-2.5 font-semibold text-gray-700 hover:border-violet-400 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {cancelling ? '취소 중...' : '취소'}
        </button>
        <button type="button" onClick={submitPay} disabled={paying || cancelling} className={primaryButton}>
          {paying ? '결제 중...' : '결제하기'}
        </button>
      </div>
    </>,
  );
}
