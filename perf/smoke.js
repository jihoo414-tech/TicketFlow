import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 5,            // 동시에 요청을 보내는 가상 사용자 5명
    duration: '30s',   // 30초 동안
    thresholds: {
        http_req_failed: ['rate<0.01'],    // 에러율 1% 미만
        http_req_duration: ['p(95)<500'],  // 요청 95%가 500ms 안에
    },
};

export default function () {
    const res = http.get('http://localhost:8080/api/members');
    check(res, { 'status is 200': (r) => r.status === 200 });
    sleep(1);          // 각 가상 사용자는 요청 후 1초 쉰다
}