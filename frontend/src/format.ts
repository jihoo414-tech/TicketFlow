export function parseLocalDateTime(value: string): Date {
  return new Date(value.slice(0, 19));
}

const dateTimeFormat = new Intl.DateTimeFormat('ko-KR', {
  month: 'long',
  day: 'numeric',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

export function formatDateTime(value: string): string {
  return dateTimeFormat.format(parseLocalDateTime(value));
}

export function formatPrice(price: number): string {
  return price.toLocaleString('ko-KR') + '원';
}

export function formatSeat(seat: { row: string; number: number }): string {
  return `${seat.row}${seat.number}`;
}

export function formatRemaining(ms: number): string {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return `${minutes}:${seconds}`;
}

export function isBookingOpen(showtime: { bookingOpenAt: string }, now: number): boolean {
  return now >= parseLocalDateTime(showtime.bookingOpenAt).getTime();
}
