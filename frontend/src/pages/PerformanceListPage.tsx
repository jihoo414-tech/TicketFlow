import { Link } from 'react-router-dom';
import { api } from '../api';
import StatusMessage from '../components/StatusMessage';
import { useLoad } from '../useLoad';

export default function PerformanceListPage() {
  const state = useLoad(api.performances, []);

  return (
    <section>
      <h1 className="mb-6 text-2xl font-bold">공연</h1>
      {state.status === 'loading' && <StatusMessage>불러오는 중...</StatusMessage>}
      {state.status === 'error' && <StatusMessage error>공연 목록을 불러오지 못했습니다.</StatusMessage>}
      {state.status === 'done' && state.data.length === 0 && <StatusMessage>등록된 공연이 없습니다.</StatusMessage>}
      {state.status === 'done' && state.data.length > 0 && (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {state.data.map(performance => (
            <li key={performance.id}>
              <Link
                to={`/performances/${performance.id}`}
                className="flex h-32 items-end rounded-xl border border-gray-200 bg-white p-5 text-lg font-semibold transition hover:border-violet-400 hover:shadow-sm"
              >
                {performance.title}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
