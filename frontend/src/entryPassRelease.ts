import { api } from './api';
import { removeEntryPass, storedEntryPassShowtimes } from './entryPassStorage';

const FLOW_PATH = /^\/performances\/[^/]+\/showtimes\/([^/]+)(\/queue|\/zones\/[^/]+)?$/;

export function showtimeInFlow(pathname: string): string | null {
  return FLOW_PATH.exec(pathname)?.[1] ?? null;
}

export function releaseEntryPasses(memberId: number | null, keepShowtimeId: string | null = null) {
  for (const showtimeId of storedEntryPassShowtimes(memberId)) {
    if (showtimeId === keepShowtimeId) {
      continue;
    }
    removeEntryPass(memberId, showtimeId);
    api.leaveQueue(showtimeId).catch(() => undefined);
  }
}
