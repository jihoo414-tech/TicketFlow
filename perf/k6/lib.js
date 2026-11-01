import { sleep } from 'k6';
import http from 'k6/http';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
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

export function lookupAdjacentPairs(showtimeId, memberId) {
    const res = http.get(`${BASE_URL}/api/showtimes/${showtimeId}/seats`, params(memberId, 'seats'));
    const ok = res.status === 200;
    record('seats', ok);
    if (!ok) {
        return null;
    }
    return adjacentPairs(res.json().filter((seat) => seat.status === 'AVAILABLE'));
}

function adjacentPairs(availableSeats) {
    const byPosition = new Map(availableSeats.map((seat) => [`${seat.row}:${seat.number}`, seat.id]));
    return availableSeats
        .filter((seat) => byPosition.has(`${seat.row}:${seat.number + 1}`))
        .map((seat) => [seat.id, byPosition.get(`${seat.row}:${seat.number + 1}`)]);
}

function pickRandom(pairs) {
    return pairs[Math.floor(Math.random() * pairs.length)];
}

function choose() {
    sleep(0.5 + Math.random() * 0.5);
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

export function holdWithRetry(showtimeId, memberId, availablePairs) {
    let available = availablePairs;
    for (let attempt = 1; attempt <= MAX_HOLD_ATTEMPTS; attempt++) {
        if (available === null || available.length === 0) {
            return false;
        }
        choose();
        const result = hold(showtimeId, memberId, pickRandom(available));
        if (result !== 'conflict') {
            return result === 'held';
        }
        if (attempt < MAX_HOLD_ATTEMPTS) {
            available = lookupAdjacentPairs(showtimeId, memberId);
        }
    }
    return false;
}
