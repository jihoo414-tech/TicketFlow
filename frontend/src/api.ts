export type Member = { id: number; name: string };
export type Performance = { id: number; title: string };
export type Showtime = { id: number; startsAt: string; bookingOpenAt: string; price: number };
export type SeatStatus = 'AVAILABLE' | 'HELD' | 'RESERVED';
export type Seat = { id: number; row: string; number: number; price: number; status: SeatStatus };
export type HoldSeat = { id: number; row: string; number: number };
export type Hold = { id: number; seats: HoldSeat[]; expiresAt: string };
export type HoldCancellation = { id: number; status: 'CANCELLED' };
export type Reservation = { id: number; holdId: number; memberId: number };
export type MyReservationStatus = 'RESERVED' | 'RESERVATION_CANCELLED';
export type MyReservation = {
  id: number;
  performanceTitle: string;
  showtimeId: number;
  startsAt: string;
  seats: HoldSeat[];
  totalPrice: number;
  status: MyReservationStatus;
};
export type ReservationCancellation = { id: number; status: 'RESERVATION_CANCELLED' };
export type RemainingSeatCount = { remainingSeatCount: number };

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

const pendingReads = new Map<string, Promise<unknown>>();

function read<T>(url: string): Promise<T> {
  const key = JSON.stringify([memberId, url]);
  const existing = pendingReads.get(key);
  if (existing) return existing as Promise<T>;

  const pending = request<T>(url).finally(() => {
    if (pendingReads.get(key) === pending) pendingReads.delete(key);
  });
  pendingReads.set(key, pending);
  return pending;
}

async function write<T>(url: string, init: RequestInit): Promise<T> {
  pendingReads.clear();
  try {
    return await request<T>(url, init);
  } finally {
    pendingReads.clear();
  }
}

export const api = {
  members: () => read<Member[]>('/api/members'),
  me: () => read<Member>('/api/members/me'),
  performances: () => read<Performance[]>('/api/performances'),
  showtimes: (performanceId: string) => read<Showtime[]>(`/api/performances/${performanceId}/showtimes`),
  seats: (showtimeId: string) => read<Seat[]>(`/api/showtimes/${showtimeId}/seats`),
  hold: (showtimeId: string, seatIds: number[]) =>
    write<Hold>(`/api/showtimes/${showtimeId}/holds`, {
      method: 'POST',
      body: JSON.stringify({ seatIds }),
    }),
  cancelHold: (holdId: number) => write<HoldCancellation>(`/api/holds/${holdId}/cancellation`, { method: 'POST' }),
  pay: (holdId: number) => write<Reservation>(`/api/holds/${holdId}/payment`, { method: 'POST' }),
  remainingSeatCount: (showtimeId: string) =>
    read<RemainingSeatCount>(`/api/members/me/showtimes/${showtimeId}/remaining-seat-count`),
  myReservations: () => read<MyReservation[]>('/api/members/me/reservations'),
  cancelReservation: (reservationId: number) =>
    write<ReservationCancellation>(`/api/reservations/${reservationId}/cancellation`, { method: 'POST' }),
};
