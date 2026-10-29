package com.ticketflow.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    PERFORMANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 공연입니다."),
    SHOWTIME_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회차입니다."),



    BOOKING_OPEN_NOT_BEFORE_START(HttpStatus.BAD_REQUEST, "예매 오픈 시각은 회차 시작 시각보다 이전이어야 합니다."),

    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "이미 선점된 좌석이 포함되어 있습니다."),
    BOOKING_NOT_OPEN(HttpStatus.BAD_REQUEST, "아직 예매 오픈 시각이 되지 않았습니다."),


    MEMBER_ID_MISSING(HttpStatus.UNAUTHORIZED, "회원을 선택해 주세요."),
    MEMBER_ID_INVALID(HttpStatus.UNAUTHORIZED, "회원 식별 값이 올바르지 않습니다."),
    MEMBER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "존재하지 않는 회원입니다.");

    private final HttpStatus status;
    private final String message;
}
