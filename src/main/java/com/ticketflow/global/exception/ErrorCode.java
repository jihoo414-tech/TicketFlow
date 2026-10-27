package com.ticketflow.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    PERFORMANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 공연입니다.");

    private final HttpStatus status;
    private final String message;
}
