import type { ReactNode } from 'react';
import type { Seat } from '../api';
import { formatDateTime, formatPrice, formatSeat } from '../format';
import { MAX_SEATS } from '../useSeatBooking';

type Props = {
  hasHold: boolean;
  selectedSeats: Seat[];
  seatLimit: number;
  bookingOpen: boolean;
  bookingOpenAt: string;
  holding: boolean;
  onNext: () => void;
  onGoPayment: () => void;
};

export function PanelFrame({ children }: { children: ReactNode }) {
  return (
    <div className="fixed inset-x-0 bottom-0 border-t border-gray-200 bg-white shadow-[0_-4px_12px_rgba(0,0,0,0.04)]">
      <div className="mx-auto flex max-w-4xl items-center justify-between gap-4 px-4 py-4">{children}</div>
    </div>
  );
}

const primaryButton = 'rounded-lg bg-violet-600 px-5 py-2.5 font-semibold text-white hover:bg-violet-700 disabled:cursor-not-allowed disabled:bg-gray-300';

export default function SelectionPanel({ hasHold, selectedSeats, seatLimit, bookingOpen, bookingOpenAt, holding, onNext, onGoPayment }: Props) {
  if (hasHold) {
    return (
      <PanelFrame>
        <p className="text-gray-700">선택한 좌석이 있습니다. 시간 안에 결제해 주세요.</p>
        <button type="button" onClick={onGoPayment} className={primaryButton}>결제로 이동</button>
      </PanelFrame>
    );
  }

  const total = selectedSeats.reduce((sum, seat) => sum + seat.price, 0);

  return (
    <PanelFrame>
      <div>
        {seatLimit === 0 ? (
          <p className="text-gray-500">이 회차는 이미 {MAX_SEATS}석을 예매했습니다.</p>
        ) : selectedSeats.length === 0 ? (
          <p className="text-gray-500">좌석을 선택해 주세요. <span className="ml-1 text-sm">남은 매수 {seatLimit}석</span></p>
        ) : (
          <>
            <p className="font-semibold text-gray-900">
              {selectedSeats.map(formatSeat).join(', ')}
              <span className="ml-2 text-sm font-normal text-gray-500">남은 매수 {seatLimit}석</span>
            </p>
            <p className="mt-1 text-sm text-gray-500">{formatPrice(total)}</p>
          </>
        )}
        {!bookingOpen && <p className="mt-1 text-sm text-gray-500">{formatDateTime(bookingOpenAt)}에 예매가 열립니다.</p>}
      </div>
      <button
        type="button"
        onClick={onNext}
        disabled={!bookingOpen || seatLimit === 0 || selectedSeats.length === 0 || holding}
        className={primaryButton}
      >
        {!bookingOpen ? '오픈 전' : holding ? '처리 중...' : '다음'}
      </button>
    </PanelFrame>
  );
}
