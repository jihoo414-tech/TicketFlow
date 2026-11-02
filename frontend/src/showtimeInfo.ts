import { api } from './api';

export async function loadShowtime(performanceId: string, showtimeId: string) {
  const [performances, showtimes] = await Promise.all([api.performances(), api.showtimes(performanceId)]);
  const performance = performances.find(candidate => String(candidate.id) === performanceId);
  const showtime = showtimes.find(candidate => String(candidate.id) === showtimeId);
  return { title: performance?.title ?? '공연', showtime };
}

export async function loadShowtimeWithZones(performanceId: string, showtimeId: string) {
  const [info, zones] = await Promise.all([loadShowtime(performanceId, showtimeId), api.zones(showtimeId)]);
  return { ...info, zones };
}
