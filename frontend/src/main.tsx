import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import type { ReactNode } from 'react';
import { HashRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import Layout from './components/Layout';
import { MemberProvider, useMember } from './member';
import AuthPage from './pages/AuthPage';
import PerformanceListPage from './pages/PerformanceListPage';
import ReservationListPage from './pages/ReservationListPage';
import SeatPage from './pages/SeatPage';
import ShowtimeListPage from './pages/ShowtimeListPage';
import './index.css';

function RequireLogin({ children }: { children: ReactNode }) {
  const { memberId } = useMember();
  const location = useLocation();
  if (memberId === null) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return children;
}

function AppRoutes() {
  const { memberId, ready } = useMember();
  if (!ready) {
    return null;
  }

  return (
    <Routes key={memberId ?? 'none'}>
      <Route element={<Layout />}>
        <Route index element={<PerformanceListPage />} />
        <Route path="performances/:performanceId" element={<ShowtimeListPage />} />
        <Route path="performances/:performanceId/showtimes/:showtimeId" element={<SeatPage />} />
        <Route path="reservations" element={<RequireLogin><ReservationListPage /></RequireLogin>} />
        <Route path="login" element={<AuthPage mode="login" />} />
        <Route path="signup" element={<AuthPage mode="signup" />} />
      </Route>
    </Routes>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MemberProvider>
      <HashRouter>
        <AppRoutes />
      </HashRouter>
    </MemberProvider>
  </StrictMode>,
);
