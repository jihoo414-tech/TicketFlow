import { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import EntryPassTimer from '../components/EntryPassTimer';
import StatusMessage from '../components/StatusMessage';
import { formatDateTime } from '../format';
import { loadShowtimeWithZones } from '../showtimeInfo';
import { useEntryPass } from '../useEntryPass';
import { useLoad } from '../useLoad';

export default function ZonePage() {
  const { performanceId = '', showtimeId = '' } = useParams();
  const { entryPass, remainingMs, handleEntryPassError } = useEntryPass(performanceId, showtimeId);
  const info = useLoad(() => loadShowtimeWithZones(performanceId, showtimeId, entryPass ?? ''), [performanceId, showtimeId, entryPass]);

  useEffect(() => {
    if (info.status === 'error') {
      handleEntryPassError(info.error);
    }
  }, [info, handleEntryPassError]);

  if (entryPass === null) {
    return null;
  }
  if (info.status === 'loading') {
    return <StatusMessage>불러오는 중...</StatusMessage>;
  }
  if (info.status === 'error' || !info.data.showtime) {
    return <StatusMessage error>존재하지 않는 회차입니다.</StatusMessage>;
  }

  const { title, showtime, zones } = info.data;
  const base = `/performances/${performanceId}/showtimes/${showtimeId}`;

  return (
    <section>
      <Link to={`/performances/${performanceId}`} className="text-sm text-gray-500 hover:text-violet-700">← 회차 목록</Link>
      <h1 className="mt-2 text-2xl font-bold">{title}</h1>
      <p className="mt-1 mb-6 flex items-center gap-3 text-gray-600">
        {formatDateTime(showtime.startsAt)}
        <EntryPassTimer remainingMs={remainingMs} />
      </p>

      <div className="rounded-xl border border-gray-200 bg-white p-6">
        <div className="mx-auto mb-8 max-w-md rounded-md bg-gray-100 py-3 text-center text-sm tracking-widest text-gray-500">무대</div>
        {zones.length === 0 ? (
          <StatusMessage>등록된 구역이 없습니다.</StatusMessage>
        ) : (
          <div className="flex justify-center gap-6">
            {zones.map(zone => (
              <Link
                key={zone.id}
                to={`${base}/zones/${zone.id}`}
                state={{ zoneName: zone.name }}
                className="flex h-28 w-36 items-center justify-center rounded-lg border-2 border-violet-300 bg-violet-50 text-lg font-semibold text-violet-700 hover:border-violet-600 hover:bg-violet-100"
              >
                {zone.name}
              </Link>
            ))}
          </div>
        )}
        <p className="mt-6 text-center text-sm text-gray-500">구역을 선택하면 좌석이 나타납니다.</p>
      </div>
    </section>
  );
}
