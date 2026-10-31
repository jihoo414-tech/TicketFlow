import http from 'k6/http';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const SEATS_PER_HOLD = 2;
const MAX_HOLD_ATTEMPTS = 3;

const systemErrors = new Counter('system_errors');
const systemErrorRate = new Rate('system_error_rate');
const holdSuccess = new Counter('hold_success');
const holdConflict = new Counter('hold_conflict');
export const scenarioSuccess = new Rate('scenario_success');

http.setResponseCallback(http.expectedStatuses(200, 201, 409));

function params(memberId, name) {
    return {
        headers: { 'X-Member-Id': String(memberId), 'Content-Type': 'application/json' },
        tags: { name },
        timeout: '2s',
    };
}

function record(name, expected) {
    systemErrorRate.add(!expected, { name });
    if (!expected) {
        systemErrors.add(1, { name });
    }
}

function errorCode(res) {
    try {
        return res.json('code');
    } catch (e) {
        return null;
    }
}

export function lookupAvailableSeats(showtimeId, memberId) {
    const res = http.get(`${BASE_URL}/api/showtimes/${showtimeId}/seats`, params(memberId, 'seats'));
    const ok = res.status === 200;
    record('seats', ok);
    if (!ok) {
        return null;
    }
    return res.json().filter((seat) => seat.status === 'AVAILABLE').map((seat) => seat.id);
}

function pickRandom(seatIds, count) {
    const picked = new Set();
    while (picked.size < count) {
        picked.add(seatIds[Math.floor(Math.random() * seatIds.length)]);
    }
    return [...picked];
}

function hold(showtimeId, memberId, seatIds) {
    const res = http.post(`${BASE_URL}/api/showtimes/${showtimeId}/holds`,
        JSON.stringify({ seatIds }), params(memberId, 'hold'));
    if (res.status === 201) {
        record('hold', true);
        holdSuccess.add(1);
        return 'held';
    }
    if (res.status === 409 && errorCode(res) === 'SEAT_ALREADY_HELD') {
        record('hold', true);
        holdConflict.add(1);
        return 'conflict';
    }
    record('hold', false);
    return 'error';
}

export function holdWithRetry(showtimeId, memberId, availableSeatIds) {
    let available = availableSeatIds;
    for (let attempt = 1; attempt <= MAX_HOLD_ATTEMPTS; attempt++) {
        if (available === null || available.length < SEATS_PER_HOLD) {
            return false;
        }
        const result = hold(showtimeId, memberId, pickRandom(available, SEATS_PER_HOLD));
        if (result !== 'conflict') {
            return result === 'held';
        }
        if (attempt < MAX_HOLD_ATTEMPTS) {
            available = lookupAvailableSeats(showtimeId, memberId);
        }
    }
    return false;
}
