package com.ticketflow.global.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.Optional;

// 스프링이 내는 오류(잘못된 id 형식, 헤더 누락 등)는 spring.mvc.problemdetails.enabled 설정으로 같은 ProblemDetail 모양이 된다.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException e) {
        return toProblemDetail(e.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return findErrorCode(e)
                .map(this::toProblemDetail)
                .orElseGet(() -> ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."));
    }

    private Optional<ErrorCode> findErrorCode(MethodArgumentNotValidException e) {
        return e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .flatMap(message -> errorCodeNamed(message).stream())
                .findFirst();
    }

    private Optional<ErrorCode> errorCodeNamed(String name) {
        return Arrays.stream(ErrorCode.values())
                .filter(errorCode -> errorCode.name().equals(name))
                .findFirst();
    }

    private ProblemDetail toProblemDetail(ErrorCode errorCode) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getMessage());
        problemDetail.setProperty("code", errorCode.name());
        return problemDetail;
    }
}
