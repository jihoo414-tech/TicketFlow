import { useCallback, useEffect, useState } from 'react';
import { api, type Hold, type Reservation, type Seat } from './api';
import { parseLocalDateTime } from './format';
import { loadStoredHold, removeStoredHold, storeHold } from './holdStorage';
import { useMember } from './member';
import { toApiError } from './useLoad';

export const MAX_SEATS = 4;

function seatLimitNotice(seatLimit: number) {
  if (seatLimit === 0) {
    return `이 회차는 이미 ${MAX_SEATS}석을 예매해 더 선택할 수 없습니다.`;
  }
  if (seatLimit < MAX_SEATS) {
    return `이미 예매한 좌석이 있어 이 회차는 ${seatLimit}석까지 선택할 수 있습니다.`;
  }
  return `좌석은 최대 ${MAX_SEATS}석까지 선택할 수 있습니다.`;
}

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
  const { memberId } = useMember();

  const [seats, setSeats] = useState<Seat[] | null>(null);
  const [seatError, setSeatError] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [hold, setHold] = useState<Hold | null>(null);
  const [holding, setHolding] = useState(false);
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const [paying, setPaying] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [seatLimit, setSeatLimit] = useState(MAX_SEATS);
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

  const reloadSeatLimit = useCallback(async () => {
    if (memberId === null) {
      setSeatLimit(MAX_SEATS);
      return;
    }
    try {
      setSeatLimit((await api.remainingSeatCount(showtimeId)).remainingSeatCount);
    } catch {
      setSeatLimit(MAX_SEATS);
    }
  }, [memberId, showtimeId]);

  useEffect(() => {
    reloadSeatLimit();
  }, [reloadSeatLimit]);

  useEffect(() => {
    setHold(loadStoredHold(memberId, showtimeId, Date.now()));
    setReservation(null);
  }, [memberId, showtimeId]);

  const holdExpired = hold !== null && reservation === null && parseLocalDateTime(hold.expiresAt).getTime() <= now;
  useEffect(() => {
    if (holdExpired) {
      removeStoredHold(memberId, showtimeId);
      reloadSeats();
    }
  }, [holdExpired, memberId, showtimeId, reloadSeats]);

  const myHoldSeatIds = new Set(hold && !holdExpired ? hold.seats.map(seat => seat.id) : []);

  const toggleSeat = (seat: Seat) => {
    const next = new Set(selectedIds);
    if (next.has(seat.id)) {
      next.delete(seat.id);
    } else if (next.size >= seatLimit) {
      setNotice(seatLimitNotice(seatLimit));
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
      storeHold(memberId, showtimeId, created);
      setSelectedIds(new Set());
      await reloadSeats();
    } catch (error) {
      const apiError = toApiError(error);
      setNotice(apiError.message);
      if (apiError.code === 'SEAT_ALREADY_HELD') {
        await reloadSeats();
      }
      if (apiError.code === 'SEAT_LIMIT_EXCEEDED') {
        await reloadSeatLimit();
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
      removeStoredHold(memberId, showtimeId);
      await Promise.all([reloadSeats(), reloadSeatLimit()]);
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
      removeStoredHold(memberId, showtimeId);
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
    removeStoredHold(memberId, showtimeId);
    setHold(null);
    setReservation(null);
  };

  return { now, holdExpired, seats, seatError, selectedIds, seatLimit, myHoldSeatIds, hold, holding, reservation, paying, cancelling, notice,
    dismissNotice, reloadSeats, toggleSeat, submitHold, submitPay, cancelHold, resetHold };
}
