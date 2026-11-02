import { useCallback, useEffect, useState } from 'react';
import { api, type Seat } from './api';
import { loadStoredHold, storeHold, type StoredHold } from './holdStorage';
import { useMember } from './member';
import { toApiError } from './useLoad';

export const MAX_SEATS = 4;

export function useNow(intervalMs: number) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}

function seatLimitNotice(seatLimit: number) {
  if (seatLimit === 0) {
    return `이 회차는 이미 ${MAX_SEATS}석을 예매해 더 선택할 수 없습니다.`;
  }
  return `남은 매수는 ${seatLimit}석입니다.`;
}

export function useSeatBooking(showtimeId: string, zoneId: string, zoneName: string) {
  const now = useNow(1000);
  const { memberId } = useMember();

  const [seats, setSeats] = useState<Seat[] | null>(null);
  const [seatError, setSeatError] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [hold, setHold] = useState<StoredHold | null>(null);
  const [holding, setHolding] = useState(false);
  const [seatLimit, setSeatLimit] = useState(MAX_SEATS);
  const [notice, setNotice] = useState<string | null>(null);
  const dismissNotice = useCallback(() => setNotice(null), []);

  const reloadSeats = useCallback(async () => {
    try {
      const loaded = await api.zoneSeats(zoneId);
      const available = new Set(loaded.filter(seat => seat.status === 'AVAILABLE').map(seat => seat.id));
      setSeats(loaded);
      setSeatError(null);
      setSelectedIds(prev => new Set([...prev].filter(id => available.has(id))));
    } catch (error) {
      setSeatError(toApiError(error).status === 404 ? '존재하지 않는 구역입니다.' : '좌석 배치도를 불러오지 못했습니다.');
    }
  }, [zoneId]);

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
  }, [memberId, showtimeId]);

  const activeHold = hold !== null && loadStoredHold(memberId, showtimeId, now) !== null ? hold : null;
  const myHoldSeatIds = new Set(activeHold ? activeHold.seats.map(seat => seat.id) : []);

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

  const submitHold = async (): Promise<boolean> => {
    if (memberId === null) {
      setNotice('로그인 후 좌석을 선택할 수 있습니다.');
      return false;
    }
    setHolding(true);
    try {
      const created = await api.hold(showtimeId, [...selectedIds]);
      storeHold(memberId, showtimeId, { ...created, zoneName });
      setSelectedIds(new Set());
      return true;
    } catch (error) {
      const apiError = toApiError(error);
      setNotice(apiError.message);
      if (apiError.code === 'SEAT_ALREADY_HELD') {
        await reloadSeats();
      }
      if (apiError.code === 'SEAT_LIMIT_EXCEEDED') {
        await reloadSeatLimit();
      }
      return false;
    } finally {
      setHolding(false);
    }
  };

  return { now, seats, seatError, selectedIds, seatLimit, activeHold, myHoldSeatIds, holding, notice,
    dismissNotice, reloadSeats, toggleSeat, submitHold };
}
