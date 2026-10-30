import { useCallback, useEffect, useState } from 'react';
import { api, type Hold, type Reservation, type Seat } from './api';
import { parseLocalDateTime } from './format';
import { toApiError } from './useLoad';

export const MAX_SEATS = 4;

function useNow(intervalMs: number) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}

export function useSeatBooking(showtimeId: string) {
  const now = useNow(1000);

  const [seats, setSeats] = useState<Seat[] | null>(null);
  const [seatError, setSeatError] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [hold, setHold] = useState<Hold | null>(null);
  const [holding, setHolding] = useState(false);
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const [paying, setPaying] = useState(false);
  const [cancelling, setCancelling] = useState(false);
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

  const holdExpired = hold !== null && reservation === null && parseLocalDateTime(hold.expiresAt).getTime() <= now;
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

  const submitPay = async () => {
    if (!hold) {
      return;
    }
    setPaying(true);
    try {
      setReservation(await api.pay(hold.id));
      await reloadSeats();
    } catch (error) {
      const apiError = toApiError(error);
      setNotice(apiError.message);
      if (apiError.code === 'HOLD_EXPIRED') {
        await reloadSeats();
      }
    } finally {
      setPaying(false);
    }
  };

  const cancelHold = async () => {
    if (!hold || paying || cancelling) return;
    setCancelling(true);
    try {
      await api.cancelHold(hold.id);
      setHold(null);
      setNotice('선점을 취소했습니다.');
      await reloadSeats();
    } catch (error) {
      const apiError = toApiError(error);
      setNotice(apiError.message);
      if (apiError.code === 'HOLD_EXPIRED' || apiError.code === 'HOLD_ALREADY_CANCELLED') {
        await reloadSeats();
      }
    } finally {
      setCancelling(false);
    }
  };

  const resetHold = () => {
    setHold(null);
    setReservation(null);
  };

  return { now, holdExpired, seats, seatError, selectedIds, hold, holding, reservation, paying, cancelling, notice,
    dismissNotice, reloadSeats, toggleSeat, submitHold, submitPay, cancelHold, resetHold };
}
