export type Member = { id: number; email: string; name: string };
export type Login = { accessToken: string };
export type Performance = { id: number; title: string };
export type Showtime = { id: number; startsAt: string; bookingOpenAt: string; price: number };
export type Zone = { id: number; name: string };
export type SeatStatus = 'AVAILABLE' | 'HELD' | 'RESERVED';
export type Seat = { id: number; row: string; number: number; price: number; status: SeatStatus };
export type HoldSeat = { id: number; row: string; number: number };
export type ReservedSeat = HoldSeat & { zoneName: string };
export type Hold = { id: number; seats: HoldSeat[]; expiresAt: string };
export type HoldCancellation = { id: number; status: 'CANCELLED' };
export type Reservation = { id: number; holdId: number; memberId: number };
export type MyReservationStatus = 'RESERVED' | 'RESERVATION_CANCELLED';
export type MyReservation = {
  id: number;
  performanceTitle: string;
  showtimeId: number;
  startsAt: string;
  seats: ReservedSeat[];
  totalPrice: number;
  status: MyReservationStatus;
};
export type ReservationCancellation = { id: number; status: 'RESERVATION_CANCELLED' };
export type RemainingSeatCount = { remainingSeatCount: number };
export type QueueStatus =
  | { status: 'WAITING'; position: number; retryAfterSeconds: number }
  | { status: 'ADMITTED'; entryPass: string; expiresAt: string };

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, code: string | undefined, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

const AUTH_ERROR_CODES = new Set(['AUTH_TOKEN_MISSING', 'AUTH_TOKEN_INVALID', 'AUTH_TOKEN_EXPIRED', 'MEMBER_NOT_FOUND']);
export const ENTRY_PASS_ERROR_CODES = new Set(['ENTRY_PASS_REQUIRED', 'ENTRY_PASS_INVALID', 'ENTRY_PASS_EXPIRED', 'ENTRY_PASS_USED']);
const QUEUE_URL = import.meta.env.VITE_QUEUE_URL ?? '';

let accessToken: string | null = null;
let onUnauthorized: () => void = () => {};

export function setApiAccessToken(token: string | null) {
  accessToken = token;
}

export function setOnUnauthorized(handler: () => void) {
  onUnauthorized = handler;
}

async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (accessToken !== null) {
    headers.set('Authorization', `Bearer ${accessToken}`);
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
    if (accessToken !== null && response.status === 401 && AUTH_ERROR_CODES.has(body?.code)) {
      onUnauthorized();
    }
    throw new ApiError(response.status, body?.code, body?.detail ?? `요청에 실패했습니다. (${response.status})`);
  }
  return body as T;
}

const pendingReads = new Map<string, Promise<unknown>>();

function entryPassHeader(entryPass: string): HeadersInit {
  return { 'X-Entry-Token': entryPass };
}

function read<T>(url: string, entryPass?: string): Promise<T> {
  const key = JSON.stringify([accessToken, url, entryPass]);
  const existing = pendingReads.get(key);
  if (existing) return existing as Promise<T>;

  const init = entryPass ? { headers: entryPassHeader(entryPass) } : {};
  const pending = request<T>(url, init).finally(() => {
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
  signup: (email: string, password: string, name: string) =>
    write<Member>('/api/members', { method: 'POST', body: JSON.stringify({ email, password, name }) }),
  login: (email: string, password: string) =>
    write<Login>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  me: () => read<Member>('/api/members/me'),
  performances: () => read<Performance[]>('/api/performances'),
  showtimes: (performanceId: string) => read<Showtime[]>(`/api/performances/${performanceId}/showtimes`),
  zones: (showtimeId: string, entryPass: string) => read<Zone[]>(`/api/showtimes/${showtimeId}/zones`, entryPass),
  zoneSeats: (zoneId: string, entryPass: string) => read<Seat[]>(`/api/zones/${zoneId}/seats`, entryPass),
  hold: (showtimeId: string, seatIds: number[], entryPass: string) =>
    write<Hold>(`/api/showtimes/${showtimeId}/holds`, {
      method: 'POST',
      headers: entryPassHeader(entryPass),
      body: JSON.stringify({ seatIds }),
    }),
  joinQueue: (showtimeId: string) =>
    write<QueueStatus>(`${QUEUE_URL}/api/showtimes/${showtimeId}/queue`, { method: 'POST' }),
  queueStatus: (showtimeId: string) =>
    request<QueueStatus>(`${QUEUE_URL}/api/showtimes/${showtimeId}/queue/me`),
  leaveQueue: (showtimeId: string) =>
    write<void>(`${QUEUE_URL}/api/showtimes/${showtimeId}/queue/me`, { method: 'DELETE' }),
  cancelHold: (holdId: number) => write<HoldCancellation>(`/api/holds/${holdId}/cancellation`, { method: 'POST' }),
  pay: (holdId: number) => write<Reservation>(`/api/holds/${holdId}/payment`, { method: 'POST' }),
  remainingSeatCount: (showtimeId: string) =>
    read<RemainingSeatCount>(`/api/members/me/showtimes/${showtimeId}/remaining-seat-count`),
  myReservations: () => read<MyReservation[]>('/api/members/me/reservations'),
  cancelReservation: (reservationId: number) =>
    write<ReservationCancellation>(`/api/reservations/${reservationId}/cancellation`, { method: 'POST' }),
};
