import type { Hold } from './api';
import { parseLocalDateTime } from './format';

function keyOf(memberId: number, showtimeId: string): string {
  return `ticketflow.hold.${memberId}.${showtimeId}`;
}

export function loadStoredHold(memberId: number | null, showtimeId: string, now: number): Hold | null {
  if (memberId === null) {
    return null;
  }
  try {
    const value = sessionStorage.getItem(keyOf(memberId, showtimeId));
    if (!value) {
      return null;
    }
    const hold = JSON.parse(value) as Hold;
    return parseLocalDateTime(hold.expiresAt).getTime() > now ? hold : null;
  } catch {
    return null;
  }
}

export function storeHold(memberId: number | null, showtimeId: string, hold: Hold) {
  if (memberId === null) {
    return;
  }
  try {
    sessionStorage.setItem(keyOf(memberId, showtimeId), JSON.stringify(hold));
  } catch {
    return;
  }
}

export function removeStoredHold(memberId: number | null, showtimeId: string) {
  if (memberId === null) {
    return;
  }
  try {
    sessionStorage.removeItem(keyOf(memberId, showtimeId));
  } catch {
    return;
  }
}
