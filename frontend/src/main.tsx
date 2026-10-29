import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { HashRouter, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import { MemberProvider, useMember } from './member';
import PerformanceListPage from './pages/PerformanceListPage';
import SeatPage from './pages/SeatPage';
import ShowtimeListPage from './pages/ShowtimeListPage';
import './index.css';

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
