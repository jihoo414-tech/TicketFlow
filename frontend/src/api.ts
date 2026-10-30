export type Member = { id: number; name: string };
export type Performance = { id: number; title: string };
export type Showtime = { id: number; startsAt: string; bookingOpenAt: string; price: number };
export type SeatStatus = 'AVAILABLE' | 'HELD' | 'RESERVED';
export type Seat = { id: number; row: string; number: number; price: number; status: SeatStatus };
export type HoldSeat = { id: number; row: string; number: number };
export type Hold = { id: number; seats: HoldSeat[]; expiresAt: string };
export type HoldCancellation = { id: number; status: 'CANCELLED' };
export type Reservation = { id: number; holdId: number; memberId: number };

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, code: string | undefined, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

let memberId: number | null = null;

export function setApiMemberId(id: number | null) {
  memberId = id;
}

async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (memberId !== null) {
    headers.set('X-Member-Id', String(memberId));
  }
  if (init.body) {
    headers.set('Content-Type', 'application/json');
  }

  let response: Response;
  try {
    response = await fetch(url, { ...init, headers });
  } catch {
    throw new ApiError(0, undefined, '서버에 연결하지 못했습니다.');
  }

  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(response.status, body?.code, body?.detail ?? `요청에 실패했습니다. (${response.status})`);
  }
  return body as T;
}

let performancesInFlight: Promise<Performance[]> | null = null;

function loadPerformances(): Promise<Performance[]> {
  if (!performancesInFlight) {
    performancesInFlight = request<Performance[]>('/api/performances').finally(() => {
      performancesInFlight = null;
    });
  }
  return performancesInFlight;
}

export const api = {
  members: () => request<Member[]>('/api/members'),
  me: () => request<Member>('/api/members/me'),
  performances: loadPerformances,
  showtimes: (performanceId: string) => request<Showtime[]>(`/api/performances/${performanceId}/showtimes`),
  seats: (showtimeId: string) => request<Seat[]>(`/api/showtimes/${showtimeId}/seats`),
  hold: (showtimeId: string, seatIds: number[]) =>
    request<Hold>(`/api/showtimes/${showtimeId}/holds`, {
      method: 'POST',
      body: JSON.stringify({ seatIds }),
    }),
  cancelHold: (holdId: number) => request<HoldCancellation>(`/api/holds/${holdId}/cancellation`, { method: 'POST' }),
  pay: (holdId: number) => request<Reservation>(`/api/holds/${holdId}/payment`, { method: 'POST' }),
};
