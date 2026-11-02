package com.ticketflow.queue.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    SHOWTIME_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회차입니다."),

    BOOKING_NOT_OPEN(HttpStatus.BAD_REQUEST, "아직 예매 오픈 시각이 되지 않았습니다."),
    NOT_IN_QUEUE(HttpStatus.NOT_FOUND, "대기열에 서 있지 않습니다."),
    ALREADY_IN_QUEUE(HttpStatus.CONFLICT, "다른 회차의 대기열에 서 있습니다. 취소한 뒤 다시 줄을 서 주세요."),

    INTERNAL_SECRET_INVALID(HttpStatus.UNAUTHORIZED, "내부 요청 비밀값이 올바르지 않습니다."),
    AUTH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다. 다시 로그인해 주세요."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요.");

    private final HttpStatus status;
    private final String message;
}
