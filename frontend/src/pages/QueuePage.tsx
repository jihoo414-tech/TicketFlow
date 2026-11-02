import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, type QueueStatus } from '../api';
import StatusMessage from '../components/StatusMessage';
import { loadEntryPass, storeEntryPass } from '../entryPassStorage';
import { formatDateTime } from '../format';
import { useMember } from '../member';
import { loadShowtime } from '../showtimeInfo';
import { toApiError, useLoad } from '../useLoad';

const RETRY_AFTER_ERROR_MS = 3000;

type QueueView =
  | { status: 'joining' }
  | { status: 'waiting'; position: number }
  | { status: 'error'; message: string; canRejoin: boolean };

function joinErrorMessage(code: string | undefined, fallback: string): string {
  if (code === 'BOOKING_NOT_OPEN') return '아직 예매가 열리지 않았습니다.';
  if (code === 'ALREADY_IN_QUEUE') return '다른 회차의 대기열에 서 있습니다. 그 회차에서 취소한 뒤 다시 줄을 서 주세요.';
  return fallback;
}

export default function QueuePage() {
  const { performanceId = '', showtimeId = '' } = useParams();
  const navigate = useNavigate();
  const { memberId } = useMember();
  const info = useLoad(() => loadShowtime(performanceId, showtimeId), [performanceId, showtimeId]);
  const [view, setView] = useState<QueueView>({ status: 'joining' });
  const [attempt, setAttempt] = useState(0);
  const stopPolling = useRef<() => void>(() => {});
  const zonesPath = `/performances/${performanceId}/showtimes/${showtimeId}`;

  useEffect(() => {
    if (loadEntryPass(memberId, showtimeId, Date.now()) !== null) {
      navigate(zonesPath, { replace: true });
      return;
    }

    let active = true;
    let timer: number | undefined;
    const schedule = (next: () => void, delayMs: number) => {
      if (active) {
        timer = window.setTimeout(next, delayMs);
      }
    };

    const follow = (status: QueueStatus) => {
      if (!active) {
        return;
      }
      if (status.status === 'ADMITTED') {
        storeEntryPass(memberId, showtimeId, { entryPass: status.entryPass, expiresAt: status.expiresAt });
        navigate(zonesPath, { replace: true });
        return;
      }
      setView({ status: 'waiting', position: status.position });
      schedule(poll, status.retryAfterSeconds * 1000);
    };

    const poll = async () => {
      try {
        follow(await api.queueStatus(showtimeId));
      } catch (error) {
        if (!active) {
          return;
        }
        if (toApiError(error).code === 'NOT_IN_QUEUE') {
          setView({ status: 'error', message: '대기열에서 빠졌습니다. 다시 줄을 서 주세요.', canRejoin: true });
          return;
        }
        schedule(poll, RETRY_AFTER_ERROR_MS);
      }
    };

    const join = async () => {
      setView({ status: 'joining' });
      try {
        follow(await api.joinQueue(showtimeId));
      } catch (error) {
        const apiError = toApiError(error);
        if (active) {
          setView({ status: 'error', message: joinErrorMessage(apiError.code, apiError.message), canRejoin: false });
        }
      }
    };

    const stop = () => {
      active = false;
      window.clearTimeout(timer);
    };
    stopPolling.current = stop;
    join();
    return stop;
  }, [attempt, memberId, navigate, showtimeId, zonesPath]);

  const cancel = async () => {
    stopPolling.current();
    try {
      await api.leaveQueue(showtimeId);
    } finally {
      navigate(`/performances/${performanceId}`);
    }
  };

  const title = info.status === 'done' ? info.data.title : '';
  const startsAt = info.status === 'done' && info.data.showtime ? formatDateTime(info.data.showtime.startsAt) : '';

  return (
    <section className="mx-auto max-w-lg">
      <Link to={`/performances/${performanceId}`} className="text-sm text-gray-500 hover:text-violet-700">← 회차 목록</Link>
      <h1 className="mt-2 text-2xl font-bold">{title}</h1>
      <p className="mt-1 mb-6 text-gray-600">{startsAt}</p>

      {view.status === 'joining' && <StatusMessage>대기열에 들어가는 중...</StatusMessage>}

      {view.status === 'error' && (
        <div className="space-y-4">
          <StatusMessage error>{view.message}</StatusMessage>
          {view.canRejoin && (
            <button type="button" onClick={() => setAttempt(previous => previous + 1)} className="w-full rounded-lg bg-violet-600 px-5 py-2.5 font-semibold text-white hover:bg-violet-700">
              다시 줄 서기
            </button>
          )}
        </div>
      )}

      {view.status === 'waiting' && (
        <div className="rounded-xl border border-gray-200 bg-white p-8 text-center">
          <p className="text-sm text-gray-500">내 앞에 대기 중</p>
          <p className="mt-2 text-5xl font-bold tabular-nums text-violet-700">{view.position.toLocaleString('ko-KR')}명</p>
          <p className="mt-6 text-sm text-gray-600">차례가 되면 자동으로 입장합니다.</p>
          <p className="mt-1 text-sm text-gray-500">화면을 닫으면 순서를 잃을 수 있습니다.</p>
          <button
            type="button"
            onClick={cancel}
            className="mt-8 rounded-lg border border-gray-300 bg-white px-6 py-2 text-sm text-gray-700 hover:border-red-300 hover:text-red-600"
          >
            취소
          </button>
        </div>
      )}
    </section>
  );
}
