import { formatRemaining } from '../format';

const URGENT_MS = 60_000;

export default function EntryPassTimer({ remainingMs }: { remainingMs: number }) {
  return (
    <span className={`rounded-full px-3 py-1 text-xs font-semibold tabular-nums ${remainingMs <= URGENT_MS ? 'bg-red-50 text-red-600' : 'bg-violet-50 text-violet-700'}`}>
      남은 입장 시간 {formatRemaining(remainingMs)}
    </span>
  );
}
