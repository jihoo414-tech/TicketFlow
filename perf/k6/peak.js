import exec from 'k6/execution';
import { holdWithRetry, lookupAdjacentPairs, pickZone, scenarioSuccess } from './lib.js';

const SHOWTIME_ID = Number(__ENV.SHOWTIME_ID || 1);
const ACTIVE_MEMBERS = Number(__ENV.ACTIVE_MEMBERS || 50);

export const options = {
    scenarios: {
        peak: { executor: 'per-vu-iterations', vus: ACTIVE_MEMBERS, iterations: 1, maxDuration: '2m' },
    },
    thresholds: {
        'http_req_duration{name:seats}': ['p(95)<500'],
        'http_req_duration{name:hold}': ['p(95)<500'],
        system_errors: ['count==0'],
    },
};

export default function () {
    const memberId = exec.vu.idInTest;
    const zoneId = pickZone(SHOWTIME_ID, memberId);
    const available = zoneId === null ? null : lookupAdjacentPairs(zoneId, memberId);
    if (available === null) {
        scenarioSuccess.add(false);
        return;
    }
    scenarioSuccess.add(holdWithRetry(SHOWTIME_ID, zoneId, memberId, available));
}
