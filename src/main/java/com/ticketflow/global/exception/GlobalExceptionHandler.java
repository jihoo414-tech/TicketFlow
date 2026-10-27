package com.ticketflow.global.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 스프링이 내는 오류(잘못된 id 형식, 헤더 누락 등)는 spring.mvc.problemdetails.enabled 설정으로 같은 ProblemDetail 모양이 된다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getMessage());
        problemDetail.setProperty("code", errorCode.name());
        return problemDetail;
    }
}
