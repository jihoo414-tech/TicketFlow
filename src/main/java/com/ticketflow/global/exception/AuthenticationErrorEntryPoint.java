package com.ticketflow.global.exception;

import com.ticketflow.global.config.SecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthenticationErrorEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = toErrorCode(authException);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getMessage());
        problemDetail.setProperty("code", errorCode.name());

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), problemDetail);
    }

    private ErrorCode toErrorCode(AuthenticationException authException) {
        if (isExpired(authException)) {
            return ErrorCode.AUTH_TOKEN_EXPIRED;
        }
        if (authException instanceof OAuth2AuthenticationException) {
            return ErrorCode.AUTH_TOKEN_INVALID;
        }
        return ErrorCode.AUTH_TOKEN_MISSING;
    }

    private boolean isExpired(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof JwtValidationException validation
                    && validation.getErrors().stream().anyMatch(error -> SecurityConfig.TOKEN_EXPIRED.equals(error.getErrorCode()))) {
                return true;
            }
        }
        return false;
    }
}
