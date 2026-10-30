import { Link, Outlet } from 'react-router-dom';
import { useMember } from '../member';

function DevMemberSwitch() {
  const { members, memberId, currentMember, memberError, ready, failed, selectMember } = useMember();

  return (
    <div className="flex flex-wrap items-center justify-end gap-2">
    {memberId !== null && <span className="text-xs text-gray-600">{memberError ? '현재 회원 조회 실패' : currentMember ? `현재 회원: ${currentMember.name} (#${currentMember.id})` : '현재 회원 확인 중...'}</span>}
    <label className="flex items-center gap-2 rounded-full border border-dashed border-amber-400 bg-amber-50 px-3 py-1 text-xs text-amber-800">
      <span className="font-semibold">개발용 · 회원 전환</span>
      <select
        className="rounded bg-white px-1 py-0.5 text-gray-900 outline-none"
        value={memberId ?? ''}
        disabled={!ready || members.length === 0}
        onChange={event => selectMember(Number(event.target.value))}
      >
        {!ready && <option value="">불러오는 중...</option>}
        {ready && failed && <option value="">불러오기 실패</option>}
        {ready && !failed && members.length === 0 && <option value="">회원 없음</option>}
        {members.map(member => (
          <option key={member.id} value={member.id}>{member.name}</option>
        ))}
      </select>
    </label>
    </div>
  );
}

export default function Layout() {
  return (
    <div className="min-h-screen">
      <header className="border-b border-gray-200 bg-white">
        <div className="mx-auto flex max-w-4xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-5">
            <Link to="/" className="text-lg font-bold text-violet-700">TicketFlow</Link>
            <Link to="/reservations" className="text-sm text-gray-600 hover:text-violet-700">내 예매</Link>
          </div>
          <DevMemberSwitch />
        </div>
      </header>
      <main className="mx-auto max-w-4xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
