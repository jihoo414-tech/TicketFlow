import type { Seat } from '../api';
import { formatPrice } from '../format';

type Props = {
  seats: Seat[];
  selectedIds: Set<number>;
  myHoldSeatIds: Set<number>;
  locked: boolean;
  onToggle: (seat: Seat) => void;
};

function seatClass(seat: Seat, selected: boolean, mine: boolean): string {
  if (mine) {
    return 'border-violet-600 bg-violet-100 text-violet-700 cursor-not-allowed';
  }
  if (seat.status === 'RESERVED') {
    return 'border-gray-400 bg-gray-400 text-white cursor-not-allowed';
  }
  if (seat.status === 'HELD') {
    return 'border-gray-200 bg-gray-200 text-gray-400 cursor-not-allowed';
  }
  if (selected) {
    return 'border-violet-600 bg-violet-600 text-white';
  }
  return 'border-violet-400 bg-white text-violet-700 hover:bg-violet-50';
}

export default function SeatMap({ seats, selectedIds, myHoldSeatIds, locked, onToggle }: Props) {
  const rows = Map.groupBy(seats, seat => seat.row);

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-6">
      <div className="overflow-x-auto">
        <div className="mx-auto w-max">
          <div className="mb-8 rounded-md bg-gray-100 py-2 text-center text-xs tracking-widest text-gray-500">무대</div>
          {[...rows].map(([row, rowSeats]) => (
            <div key={row} className="mb-2 flex items-center gap-2">
              <span className="w-6 text-sm font-semibold text-gray-500">{row}</span>
              {rowSeats.map(seat => (
                <button
                  key={seat.id}
                  type="button"
                  title={`${seat.row}열 ${seat.number}번 · ${formatPrice(seat.price)}`}
                  disabled={seat.status !== 'AVAILABLE' || locked || myHoldSeatIds.has(seat.id)}
                  onClick={() => onToggle(seat)}
                  className={`h-9 w-9 rounded-md border text-xs font-medium transition disabled:cursor-not-allowed ${seatClass(seat, selectedIds.has(seat.id), myHoldSeatIds.has(seat.id))}`}
                >
                  {seat.number}
                </button>
              ))}
            </div>
          ))}
        </div>
      </div>
      <div className="mt-6 flex justify-center gap-5 text-xs text-gray-600">
        <span className="flex items-center gap-1.5"><span className="h-4 w-4 rounded border border-violet-400 bg-white" />선점 가능</span>
        <span className="flex items-center gap-1.5"><span className="h-4 w-4 rounded bg-violet-600" />선택한 좌석</span>
        <span className="flex items-center gap-1.5"><span className="h-4 w-4 rounded border border-violet-600 bg-violet-100" />내 선점</span>
        <span className="flex items-center gap-1.5"><span className="h-4 w-4 rounded bg-gray-200" />선점 불가</span>
        <span className="flex items-center gap-1.5"><span className="h-4 w-4 rounded bg-gray-400" />예매 완료</span>
      </div>
    </div>
  );
}
