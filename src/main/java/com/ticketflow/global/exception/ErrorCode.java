package com.ticketflow.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    PERFORMANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 공연입니다."),
    SHOWTIME_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회차입니다."),
    HOLD_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 선점입니다."),



    BOOKING_OPEN_NOT_BEFORE_START(HttpStatus.BAD_REQUEST, "예매 오픈 시각은 회차 시작 시각보다 이전이어야 합니다."),

    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "이미 선점된 좌석이 포함되어 있습니다."),
    SEAT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "한 회차에서는 선점과 예매를 합쳐 4석까지만 가능합니다."),
    HOLD_ALREADY_EXISTS(HttpStatus.CONFLICT, "이 회차에 이미 선점이 있습니다. 기존 선점을 먼저 취소해 주세요."),
    BOOKING_NOT_OPEN(HttpStatus.BAD_REQUEST, "아직 예매 오픈 시각이 되지 않았습니다."),
    INVALID_SEAT_COUNT(HttpStatus.BAD_REQUEST, "좌석은 1석 이상 4석 이하로 선택해 주세요."),
    DUPLICATE_SEAT(HttpStatus.BAD_REQUEST, "같은 좌석이 중복으로 포함되어 있습니다."),
    SEAT_NOT_IN_SHOWTIME(HttpStatus.BAD_REQUEST, "이 회차의 좌석이 아닌 좌석이 포함되어 있습니다."),
    HOLD_NOT_OWNED(HttpStatus.FORBIDDEN, "본인의 선점만 결제할 수 있습니다."),
    HOLD_EXPIRED(HttpStatus.CONFLICT, "선점이 만료되어 결제할 수 없습니다."),


    MEMBER_ID_MISSING(HttpStatus.UNAUTHORIZED, "회원을 선택해 주세요."),
    MEMBER_ID_INVALID(HttpStatus.UNAUTHORIZED, "회원 식별 값이 올바르지 않습니다."),
    MEMBER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "존재하지 않는 회원입니다.");

    private final HttpStatus status;
    private final String message;
}
