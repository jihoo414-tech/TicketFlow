import http from 'k6/http';
import { check, sleep } from 'k6';

export default function () {
    // 실제 존재하는 회차 ID로 변경하세요.
    const showtimeId = 1;

    const response = http.get(
        `http://localhost:8080/api/showtimes/${showtimeId}/seats`
    );

    check(response, {
        '좌석 조회 응답이 200이다': (res) => res.status === 200,
    });

    // 각 가상 사용자는 요청이 끝난 뒤 1초 기다립니다.
    sleep(1);
}