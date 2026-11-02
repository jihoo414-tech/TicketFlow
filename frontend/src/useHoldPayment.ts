import { useCallback, useEffect, useState } from 'react';
import { api, type Reservation } from './api';
import { parseLocalDateTime } from './format';
import { loadStoredHold, removeStoredHold, type StoredHold } from './holdStorage';
import { useMember } from './member';
import { toApiError } from './useLoad';
import { useNow } from './useSeatBooking';

export function useHoldPayment(showtimeId: string) {
  const now = useNow(1000);
  const { memberId } = useMember();

  const [hold, setHold] = useState<StoredHold | null>(null);
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const [paying, setPaying] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelled, setCancelled] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const dismissNotice = useCallback(() => setNotice(null), []);

  useEffect(() => {
    setHold(loadStoredHold(memberId, showtimeId, Date.now()));
  }, [memberId, showtimeId]);

  const holdExpired = hold !== null && reservation === null && parseLocalDateTime(hold.expiresAt).getTime() <= now;
  useEffect(() => {
    if (holdExpired) {
      removeStoredHold(memberId, showtimeId);
    }
  }, [holdExpired, memberId, showtimeId]);

  const submitPay = async () => {
    if (!hold) {
      return;
    }
    setPaying(true);
    try {
      setReservation(await api.pay(hold.id));
      removeStoredHold(memberId, showtimeId);
    } catch (error) {
      setNotice(toApiError(error).message);
    } finally {
      setPaying(false);
    }
  };

  const cancelHold = async () => {
    if (!hold || paying || cancelling) return;
    setCancelling(true);
    try {
      await api.cancelHold(hold.id);
      removeStoredHold(memberId, showtimeId);
      setCancelled(true);
    } catch (error) {
      const apiError = toApiError(error);
      setNotice(apiError.message);
      if (apiError.code === 'HOLD_EXPIRED' || apiError.code === 'HOLD_ALREADY_CANCELLED') {
        removeStoredHold(memberId, showtimeId);
        setCancelled(true);
      }
    } finally {
      setCancelling(false);
    }
  };

  return { now, hold, holdExpired, reservation, paying, cancelling, cancelled, notice, dismissNotice, submitPay, cancelHold };
}
