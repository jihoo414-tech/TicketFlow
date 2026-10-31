import { sleep } from 'k6';
import exec from 'k6/execution';
import { holdWithRetry, lookupAvailableSeats, scenarioSuccess } from './lib.js';

const SHOWTIME_ID = Number(__ENV.SHOWTIME_ID || 1);
const VUS = Number(__ENV.VUS || 10);
const DURATION = __ENV.DURATION || '3m';
const LOOKUPS = 3;

export const options = {
    scenarios: {
        steady: { executor: 'constant-vus', vus: VUS, duration: DURATION, gracefulStop: '30s' },
    },
    thresholds: {
        'http_req_duration{name:seats}': ['p(95)<300'],
        'http_req_duration{name:hold}': ['p(95)<500'],
        system_error_rate: ['rate<0.001'],
    },
};

function think() {
    sleep(4 + Math.random() * 2);
}

export default function () {
    if (exec.vu.iterationInScenario === 0) {
        sleep(Math.random() * 5);
    }
    const memberId = exec.scenario.iterationInTest + 1;

    let available = null;
    for (let i = 0; i < LOOKUPS; i++) {
        available = lookupAvailableSeats(SHOWTIME_ID, memberId);
        if (available === null) {
            scenarioSuccess.add(false);
            return;
        }
        think();
    }
    scenarioSuccess.add(holdWithRetry(SHOWTIME_ID, memberId, available));
}
