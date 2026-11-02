import { useEffect } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { releaseEntryPasses, showtimeInFlow } from '../entryPassRelease';
import { useMember } from '../member';

function MemberMenu() {
  const { memberId, currentMember, ready, logout } = useMember();
  const navigate = useNavigate();

  if (!ready) {
    return null;
  }
  if (!currentMember) {
    return (
      <div className="flex items-center gap-3 text-sm">
        <Link to="/login" className="text-gray-600 hover:text-violet-700">로그인</Link>
        <Link to="/signup" className="rounded-lg bg-violet-600 px-3 py-1.5 font-semibold text-white hover:bg-violet-700">회원가입</Link>
      </div>
    );
  }
  return (
    <div className="flex items-center gap-3 text-sm">
      <span className="text-gray-600">{currentMember.name}님</span>
      <button
        type="button"
        onClick={() => { releaseEntryPasses(memberId); logout(); navigate('/'); }}
        className="text-gray-500 hover:text-violet-700"
      >
        로그아웃
      </button>
    </div>
  );
}

function useReleaseEntryPassesOutsideFlow() {
  const { memberId } = useMember();
  const { pathname } = useLocation();

  useEffect(() => {
    releaseEntryPasses(memberId, showtimeInFlow(pathname));
  }, [memberId, pathname]);
}

export default function Layout() {
  useReleaseEntryPassesOutsideFlow();

  return (
    <div className="min-h-screen">
      <header className="border-b border-gray-200 bg-white">
        <div className="mx-auto flex max-w-4xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-5">
            <Link to="/" className="text-lg font-bold text-violet-700">TicketFlow</Link>
            <Link to="/reservations" className="text-sm text-gray-600 hover:text-violet-700">내 예매</Link>
          </div>
          <MemberMenu />
        </div>
      </header>
      <main className="mx-auto max-w-4xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
