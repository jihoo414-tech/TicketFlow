import { Link, useParams } from 'react-router-dom';
import { api, type Seat } from '../api';
import HoldPanel from '../components/HoldPanel';
import SeatMap from '../components/SeatMap';
import StatusMessage from '../components/StatusMessage';
import Toast from '../components/Toast';
import { formatDateTime, isBookingOpen } from '../format';
import { useLoad } from '../useLoad';
import { useSeatBooking } from '../useSeatBooking';

async function loadShowtime(performanceId: string, showtimeId: string) {
  const [performances, showtimes] = await Promise.all([api.performances(), api.showtimes(performanceId)]);
  const performance = performances.find(candidate => String(candidate.id) === performanceId);
  const showtime = showtimes.find(candidate => String(candidate.id) === showtimeId);
  return { title: performance?.title ?? '공연', showtime };
}

function sortSeats(seats: Seat[]): Seat[] {
  return [...seats].sort((a, b) => a.row.localeCompare(b.row) || a.number - b.number);
}

export default function SeatPage() {
  const { performanceId = '', showtimeId = '' } = useParams();
  const info = useLoad(() => loadShowtime(performanceId, showtimeId), [performanceId, showtimeId]);
  const { now, holdExpired, seats, seatError, selectedIds, myHoldSeatIds, hold, holding, reservation, paying, cancelling, notice,
    dismissNotice, reloadSeats, toggleSeat, submitHold, submitPay, cancelHold, resetHold } = useSeatBooking(showtimeId);

  if (info.status === 'loading') {
    return <StatusMessage>불러오는 중...</StatusMessage>;
  }
  if (info.status === 'error' || !info.data.showtime) {
    return <StatusMessage error>존재하지 않는 회차입니다.</StatusMessage>;
  }

  const { title, showtime } = info.data;
  const bookingOpen = isBookingOpen(showtime, now);
  const selectedSeats = sortSeats((seats ?? []).filter(seat => selectedIds.has(seat.id)));

  return (
    <section className="pb-32">
      <Toast message={notice} onDismiss={dismissNotice} />
      <Link to={`/performances/${performanceId}`} className="text-sm text-gray-500 hover:text-violet-700">← 회차 목록</Link>
      <div className="mt-2 mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">{title}</h1>
          <p className="mt-1 flex items-center gap-2 text-gray-600">
            {formatDateTime(showtime.startsAt)}
            {!bookingOpen && <span className="rounded-full bg-gray-100 px-3 py-0.5 text-xs font-semibold text-gray-600">오픈 전</span>}
          </p>
        </div>
        <button
          type="button"
          onClick={reloadSeats}
          className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:border-violet-400 hover:text-violet-700"
        >
          좌석 새로고침
        </button>
      </div>

      {seatError && <StatusMessage error>{seatError}</StatusMessage>}
      {!seatError && seats === null && <StatusMessage>불러오는 중...</StatusMessage>}
      {!seatError && seats?.length === 0 && <StatusMessage>등록된 좌석이 없습니다.</StatusMessage>}
      {!seatError && seats && seats.length > 0 && (
        <SeatMap seats={seats} selectedIds={selectedIds} myHoldSeatIds={myHoldSeatIds} locked={hold !== null} onToggle={toggleSeat} />
      )}

      <HoldPanel
        hold={hold}
        holdExpired={holdExpired}
        reservation={reservation}
        now={now}
        selectedSeats={selectedSeats}
        bookingOpen={bookingOpen}
        bookingOpenAt={showtime.bookingOpenAt}
        holding={holding}
        paying={paying}
        cancelling={cancelling}
        onHold={submitHold}
        onPay={submitPay}
        onCancel={cancelHold}
        onReset={resetHold}
      />
    </section>
  );
}
