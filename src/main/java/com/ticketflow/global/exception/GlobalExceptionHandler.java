package com.ticketflow.global.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// 스프링이 내는 오류(잘못된 id 형식, 헤더 누락 등)는 spring.mvc.problemdetails.enabled 설정으로 같은 ProblemDetail 모양이 된다.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Map<String, ErrorCode> CONSTRAINT_ERROR_CODES = new LinkedHashMap<>();

    static {
        CONSTRAINT_ERROR_CODES.put("NotEmpty", ErrorCode.INVALID_SEAT_COUNT);
        CONSTRAINT_ERROR_CODES.put("NotNull", ErrorCode.INVALID_SEAT_ID);
        CONSTRAINT_ERROR_CODES.put("UniqueElements", ErrorCode.DUPLICATE_SEAT);
    }

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
        return CONSTRAINT_ERROR_CODES.entrySet().stream()
                .filter(entry -> e.getBindingResult().getFieldErrors().stream()
                        .map(FieldError::getCode)
                        .anyMatch(entry.getKey()::equals))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private ProblemDetail toProblemDetail(ErrorCode errorCode) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getMessage());
        problemDetail.setProperty("code", errorCode.name());
        return problemDetail;
    }
}
