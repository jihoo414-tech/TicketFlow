import { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import type { Seat } from '../api';
import EntryPassTimer from '../components/EntryPassTimer';
import SeatMap from '../components/SeatMap';
import SelectionPanel from '../components/SelectionPanel';
import StatusMessage from '../components/StatusMessage';
import Toast from '../components/Toast';
import { formatDateTime, isBookingOpen } from '../format';
import { loadShowtimeWithZones } from '../showtimeInfo';
import { useEntryPass } from '../useEntryPass';
import { useLoad } from '../useLoad';
import { useSeatBooking } from '../useSeatBooking';

function sortSeats(seats: Seat[]): Seat[] {
  return [...seats].sort((a, b) => Number(a.row) - Number(b.row) || a.number - b.number);
}

export default function SeatPage() {
  const { performanceId = '', showtimeId = '', zoneId = '' } = useParams();
  const navigate = useNavigate();
  const { entryPass, remainingMs, handleEntryPassError } = useEntryPass(performanceId, showtimeId);
  const info = useLoad(() => loadShowtimeWithZones(performanceId, showtimeId, entryPass ?? ''), [performanceId, showtimeId, entryPass]);
  const zoneName = info.status === 'done' ? info.data.zones.find(zone => String(zone.id) === zoneId)?.name ?? '' : '';
  const { now, seats, seatError, selectedIds, seatLimit, activeHold, myHoldSeatIds, holding, notice,
    dismissNotice, reloadSeats, toggleSeat, submitHold } = useSeatBooking(showtimeId, zoneId, zoneName, entryPass ?? '', handleEntryPassError);

  useEffect(() => {
    if (info.status === 'error') {
      handleEntryPassError(info.error);
    }
  }, [info, handleEntryPassError]);

  if (entryPass === null) {
    return null;
  }
  if (info.status === 'loading') {
    return <StatusMessage>불러오는 중...</StatusMessage>;
  }
  if (info.status === 'error' || !info.data.showtime) {
    return <StatusMessage error>존재하지 않는 회차입니다.</StatusMessage>;
  }

  const { title, showtime } = info.data;
  const base = `/performances/${performanceId}/showtimes/${showtimeId}`;
  const bookingOpen = isBookingOpen(showtime, now);
  const selectedSeats = sortSeats((seats ?? []).filter(seat => selectedIds.has(seat.id)));

  const next = async () => {
    if (await submitHold()) {
      navigate(`${base}/payment`);
    }
  };

  return (
    <section className="pb-32">
      <Toast message={notice} onDismiss={dismissNotice} />
      <Link to={base} className="text-sm text-gray-500 hover:text-violet-700">← 다른 구역</Link>
      <div className="mt-2 mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">{title} · {zoneName}</h1>
          <p className="mt-1 flex items-center gap-2 text-gray-600">
            {formatDateTime(showtime.startsAt)}
            {!bookingOpen && <span className="rounded-full bg-gray-100 px-3 py-0.5 text-xs font-semibold text-gray-600">오픈 전</span>}
            <EntryPassTimer remainingMs={remainingMs} />
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
        <SeatMap seats={seats} selectedIds={selectedIds} myHoldSeatIds={myHoldSeatIds} locked={activeHold !== null} onToggle={toggleSeat} />
      )}

      <SelectionPanel
        hasHold={activeHold !== null}
        selectedSeats={selectedSeats}
        seatLimit={seatLimit}
        bookingOpen={bookingOpen}
        bookingOpenAt={showtime.bookingOpenAt}
        holding={holding}
        onNext={next}
        onGoPayment={() => navigate(`${base}/payment`)}
      />
    </section>
  );
}
