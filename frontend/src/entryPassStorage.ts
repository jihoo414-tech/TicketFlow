export type StoredEntryPass = { entryPass: string; expiresAt: string };

function prefixOf(memberId: number): string {
  return `ticketflow.entryPass.${memberId}.`;
}

function keyOf(memberId: number, showtimeId: string): string {
  return prefixOf(memberId) + showtimeId;
}

export function storedEntryPassShowtimes(memberId: number | null): string[] {
  if (memberId === null) {
    return [];
  }
  try {
    return Object.keys(sessionStorage)
      .filter(key => key.startsWith(prefixOf(memberId)))
      .map(key => key.slice(prefixOf(memberId).length));
  } catch {
    return [];
  }
}

export function entryPassExpiresAt(pass: StoredEntryPass): number {
  return new Date(pass.expiresAt).getTime();
}

export function loadEntryPass(memberId: number | null, showtimeId: string, now: number): StoredEntryPass | null {
  if (memberId === null) {
    return null;
  }
  try {
    const value = sessionStorage.getItem(keyOf(memberId, showtimeId));
    if (!value) {
      return null;
    }
    const pass = JSON.parse(value) as StoredEntryPass;
    return entryPassExpiresAt(pass) > now ? pass : null;
  } catch {
    return null;
  }
}

export function storeEntryPass(memberId: number | null, showtimeId: string, pass: StoredEntryPass) {
  if (memberId === null) {
    return;
  }
  try {
    sessionStorage.setItem(keyOf(memberId, showtimeId), JSON.stringify(pass));
  } catch {
    return;
  }
}

export function removeEntryPass(memberId: number | null, showtimeId: string) {
  if (memberId === null) {
    return;
  }
  try {
    sessionStorage.removeItem(keyOf(memberId, showtimeId));
  } catch {
    return;
  }
}
