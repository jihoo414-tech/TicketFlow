import type { ReactNode } from 'react';
import type { Hold, Reservation, Seat } from '../api';
import { formatDateTime, formatPrice, formatRemaining, formatSeat, parseLocalDateTime } from '../format';

export const MAX_SEATS = 4;
const URGENT_MS = 60_000;

type Props = {
  hold: Hold | null;
  reservation: Reservation | null;
  now: number;
  selectedSeats: Seat[];
  bookingOpen: boolean;
  bookingOpenAt: string;
  holding: boolean;
  paying: boolean;
  onHold: () => void;
  onPay: () => void;
  onReset: () => void;
};

function PanelFrame({ children }: { children: ReactNode }) {
  return (
    <div className="fixed inset-x-0 bottom-0 border-t border-gray-200 bg-white shadow-[0_-4px_12px_rgba(0,0,0,0.04)]">
      <div className="mx-auto flex max-w-4xl items-center justify-between gap-4 px-4 py-4">{children}</div>
    </div>
  );
}

export default function HoldPanel({
  hold, reservation, now, selectedSeats, bookingOpen, bookingOpenAt, holding, paying, onHold, onPay, onReset,
}: Props) {
  if (hold) {
    const remainingMs = parseLocalDateTime(hold.expiresAt).getTime() - now;
    const seatsText = hold.seats.map(formatSeat).join(', ');

    if (reservation) {
      return (
        <PanelFrame>
          <div>
            <p className="font-semibold text-gray-900">예매 완료 · {seatsText}</p>
            <p className="mt-1 text-sm text-gray-500">예매 번호 {reservation.id}</p>
          </div>
          <button type="button" onClick={onReset} className="rounded-lg bg-violet-600 px-5 py-2.5 font-semibold text-white hover:bg-violet-700">
            다른 좌석 고르기
          </button>
        </PanelFrame>
      );
    }

    if (remainingMs <= 0) {
      return (
        <PanelFrame>
          <div>
            <p className="font-semibold text-gray-900">선점이 만료되었습니다.</p>
            <p className="mt-1 text-sm text-gray-500">{seatsText} 좌석이 다시 열렸습니다.</p>
          </div>
          <button type="button" onClick={onReset} className="rounded-lg bg-violet-600 px-5 py-2.5 font-semibold text-white hover:bg-violet-700">
            다시 고르기
          </button>
        </PanelFrame>
      );
    }

    return (
      <PanelFrame>
        <div>
          <p className="font-semibold text-gray-900">선점 완료 · {seatsText}</p>
          <p className="mt-1 text-sm text-gray-500">시간 안에 결제하지 않으면 좌석이 다시 열립니다.</p>
        </div>
        <div className="ml-auto text-right">
          <p className="text-xs text-gray-500">남은 시간</p>
          <p className={`text-2xl font-bold tabular-nums ${remainingMs <= URGENT_MS ? 'text-red-600' : 'text-violet-700'}`}>
            {formatRemaining(remainingMs)}
          </p>
        </div>
        <button
          type="button"
          onClick={onPay}
          disabled={paying}
          className="rounded-lg bg-violet-600 px-5 py-2.5 font-semibold text-white hover:bg-violet-700 disabled:cursor-not-allowed disabled:bg-gray-300"
        >
          {paying ? '결제 중...' : '결제하기'}
        </button>
      </PanelFrame>
    );
  }

  const total = selectedSeats.reduce((sum, seat) => sum + seat.price, 0);

  return (
    <PanelFrame>
      <div>
        {selectedSeats.length === 0 ? (
          <p className="text-gray-500">좌석을 선택해 주세요. (최대 {MAX_SEATS}석)</p>
        ) : (
          <>
            <p className="font-semibold text-gray-900">
              {selectedSeats.map(formatSeat).join(', ')}
              <span className="ml-2 text-sm font-normal text-gray-500">{selectedSeats.length}/{MAX_SEATS}석</span>
            </p>
            <p className="mt-1 text-sm text-gray-500">{formatPrice(total)}</p>
          </>
        )}
        {!bookingOpen && <p className="mt-1 text-sm text-gray-500">{formatDateTime(bookingOpenAt)}에 예매가 열립니다.</p>}
      </div>
      <button
        type="button"
        onClick={onHold}
        disabled={!bookingOpen || selectedSeats.length === 0 || holding}
        className="rounded-lg bg-violet-600 px-5 py-2.5 font-semibold text-white hover:bg-violet-700 disabled:cursor-not-allowed disabled:bg-gray-300"
      >
        {!bookingOpen ? '오픈 전' : holding ? '선점 중...' : '선점하기'}
      </button>
    </PanelFrame>
  );
}
