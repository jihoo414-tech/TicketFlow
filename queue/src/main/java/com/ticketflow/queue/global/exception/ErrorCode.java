package com.ticketflow.queue.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    ALREADY_IN_QUEUE(HttpStatus.CONFLICT, "다른 회차의 대기열에 서 있습니다. 취소한 뒤 다시 줄을 서 주세요.");

    private final HttpStatus status;
    private final String message;
}
