import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ENTRY_PASS_ERROR_CODES } from './api';
import { entryPassExpiresAt, loadEntryPass, removeEntryPass } from './entryPassStorage';
import { loadStoredHold, removeStoredHold } from './holdStorage';
import { useMember } from './member';
import { toApiError } from './useLoad';
import { useNow } from './useSeatBooking';

export function useEntryPass(performanceId: string, showtimeId: string) {
  const { memberId } = useMember();
  const navigate = useNavigate();
  const now = useNow(1000);
  const [returnedHold] = useState(() => loadStoredHold(memberId, showtimeId, Date.now()));
  const [pass] = useState(() => loadEntryPass(memberId, showtimeId, Date.now()));
  const remainingMs = pass ? entryPassExpiresAt(pass) - now : 0;
  const expired = pass !== null && remainingMs <= 0;

  const leave = useCallback((notice: string) => {
    removeEntryPass(memberId, showtimeId);
    navigate(`/performances/${performanceId}`, { replace: true, state: { notice } });
  }, [memberId, navigate, performanceId, showtimeId]);

  useEffect(() => {
    if (returnedHold === null) {
      return;
    }
    api.cancelHold(returnedHold.id)
      .catch(() => undefined)
      .finally(() => {
        removeStoredHold(memberId, showtimeId);
        leave('선점을 취소했습니다. 다시 예매하려면 줄을 서 주세요.');
      });
  }, [returnedHold, memberId, showtimeId, leave]);

  useEffect(() => {
    if (returnedHold !== null) {
      return;
    }
    if (pass === null) {
      leave('입장이 끝났습니다. 다시 예매하려면 줄을 서 주세요.');
    } else if (expired) {
      leave('입장 시간이 끝났습니다. 다시 예매하려면 줄을 서 주세요.');
    }
  }, [returnedHold, pass, expired, leave]);

  const handleEntryPassError = useCallback((error: unknown) => {
    const apiError = toApiError(error);
    if (apiError.code === undefined || !ENTRY_PASS_ERROR_CODES.has(apiError.code)) {
      return false;
    }
    if (returnedHold === null && pass !== null) {
      leave(apiError.message);
    }
    return true;
  }, [returnedHold, pass, leave]);

  const entryPass = returnedHold === null ? pass?.entryPass ?? null : null;
  return { entryPass, remainingMs, handleEntryPassError };
}
