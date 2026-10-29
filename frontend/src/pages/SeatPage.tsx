import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, type Hold, type Seat } from '../api';
import HoldPanel, { MAX_SEATS } from '../components/HoldPanel';
import SeatMap from '../components/SeatMap';
import StatusMessage from '../components/StatusMessage';
import Toast from '../components/Toast';
import { formatDateTime, isBookingOpen, parseLocalDateTime } from '../format';
import { toApiError, useLoad } from '../useLoad';

async function loadShowtime(performanceId: string, showtimeId: string) {
  const [performances, showtimes] = await Promise.all([api.performances(), api.showtimes(performanceId)]);
  const performance = performances.find(candidate => String(candidate.id) === performanceId);
  const showtime = showtimes.find(candidate => String(candidate.id) === showtimeId);
  return { title: performance?.title ?? '공연', showtime };
}

function useNow(intervalMs: number) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}

function sortSeats(seats: Seat[]): Seat[] {
  return [...seats].sort((a, b) => a.row.localeCompare(b.row) || a.number - b.number);
}

export default function SeatPage() {
  const { performanceId = '', showtimeId = '' } = useParams();
  const info = useLoad(() => loadShowtime(performanceId, showtimeId), [performanceId, showtimeId]);
  const now = useNow(1000);

  const [seats, setSeats] = useState<Seat[] | null>(null);
  const [seatError, setSeatError] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [hold, setHold] = useState<Hold | null>(null);
  const [holding, setHolding] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const dismissNotice = useCallback(() => setNotice(null), []);

  const reloadSeats = useCallback(async () => {
    try {
      const loaded = await api.seats(showtimeId);
      const available = new Set(loaded.filter(seat => seat.status === 'AVAILABLE').map(seat => seat.id));
      setSeats(loaded);
      setSeatError(null);
      setSelectedIds(prev => new Set([...prev].filter(id => available.has(id))));
    } catch (error) {
      setSeatError(toApiError(error).status === 404 ? '존재하지 않는 회차입니다.' : '좌석 배치도를 불러오지 못했습니다.');
    }
  }, [showtimeId]);

  useEffect(() => {
    reloadSeats();
  }, [reloadSeats]);

  const holdExpired = hold !== null && parseLocalDateTime(hold.expiresAt).getTime() <= now;
  useEffect(() => {
    if (holdExpired) {
      reloadSeats();
    }
  }, [holdExpired, reloadSeats]);

  const toggleSeat = (seat: Seat) => {
    const next = new Set(selectedIds);
    if (next.has(seat.id)) {
      next.delete(seat.id);
    } else if (next.size >= MAX_SEATS) {
      setNotice(`좌석은 최대 ${MAX_SEATS}석까지 선택할 수 있습니다.`);
      return;
    } else {
      next.add(seat.id);
    }
    setSelectedIds(next);
  };

  const submitHold = async () => {
    setHolding(true);
    try {
      const created = await api.hold(showtimeId, [...selectedIds]);
      setHold(created);
      setSelectedIds(new Set());
      await reloadSeats();
    } catch (error) {
      const apiError = toApiError(error);
      setNotice(apiError.message);
      if (apiError.code === 'SEAT_ALREADY_HELD') {
        await reloadSeats();
      }
    } finally {
      setHolding(false);
    }
  };

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
        <SeatMap seats={seats} selectedIds={selectedIds} locked={hold !== null} onToggle={toggleSeat} />
      )}

      <HoldPanel
        hold={hold}
        now={now}
        selectedSeats={selectedSeats}
        bookingOpen={bookingOpen}
        bookingOpenAt={showtime.bookingOpenAt}
        holding={holding}
        onHold={submitHold}
        onReset={() => setHold(null)}
      />
    </section>
  );
}
