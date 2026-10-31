import exec from 'k6/execution';
import { holdWithRetry, lookupAvailableSeats } from './lib.js';

const SHOWTIME_ID = Number(__ENV.SHOWTIME_ID || 2);
const MEMBER_OFFSET = Number(__ENV.MEMBER_OFFSET || 9000);

export const options = {
    scenarios: {
        warmup: { executor: 'shared-iterations', vus: 10, iterations: 800, maxDuration: '3m' },
    },
};

export default function () {
    const memberId = MEMBER_OFFSET + exec.scenario.iterationInTest + 1;
    holdWithRetry(SHOWTIME_ID, memberId, lookupAvailableSeats(SHOWTIME_ID, memberId));
}
