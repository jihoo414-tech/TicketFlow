package com.ticketflow.domain.admission.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.ticketflow.domain.admission.client.QueueClient;
import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class EntryPasses {

    public static final String ENTRY_TOKEN = "X-Entry-Token";
    private static final String PASS_EXPIRED = "entry_pass_expired";
    private static final Duration PASS_DURATION = Duration.ofMinutes(5);
    private static final int MAXIMUM_USED_PASSES = 100_000;

    private final JwtDecoder decoder;
    private final Cache<String, Boolean> usedPasses;
    private final QueueClient queueClient;
    private final TaskExecutor taskExecutor;

    public EntryPasses(@Value("${entry-pass.secret}") String secret, Clock clock, Ticker ticker,
                       QueueClient queueClient, TaskExecutor taskExecutor) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(notExpired(clock));
        this.decoder = decoder;
        this.usedPasses = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfterWrite(PASS_DURATION)
                .maximumSize(MAXIMUM_USED_PASSES)
                .build();
        this.queueClient = queueClient;
        this.taskExecutor = taskExecutor;
    }

    public EntryPass verify(String token, Long memberId, Long showtimeId) {
        if (token == null) {
            throw new BusinessException(ErrorCode.ENTRY_PASS_REQUIRED);
        }
        EntryPass pass = decode(token);
        validateFor(pass, memberId, showtimeId);
        validateNotUsed(pass);
        return pass;
    }

    public void complete(EntryPass pass) {
        usedPasses.put(pass.id(), Boolean.TRUE);
        taskExecutor.execute(() -> notifyRelease(pass));
    }

    private void notifyRelease(EntryPass pass) {
        try {
            queueClient.release(pass.id());
        } catch (RuntimeException e) {
            log.warn("대기열 서버에 자리 비움을 알리지 못했다. 입장권 만료로 자리가 빈다. passId={}", pass.id(), e);
        }
    }

    private EntryPass decode(String token) {
        try {
            return EntryPass.from(decoder.decode(token));
        } catch (JwtValidationException e) {
            throw new BusinessException(isExpired(e) ? ErrorCode.ENTRY_PASS_EXPIRED : ErrorCode.ENTRY_PASS_INVALID);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.ENTRY_PASS_INVALID);
        }
    }

    private boolean isExpired(JwtValidationException e) {
        return e.getErrors().stream().anyMatch(error -> PASS_EXPIRED.equals(error.getErrorCode()));
    }

    private void validateFor(EntryPass pass, Long memberId, Long showtimeId) {
        if (!pass.memberId().equals(memberId) || !pass.showtimeId().equals(showtimeId)) {
            throw new BusinessException(ErrorCode.ENTRY_PASS_INVALID);
        }
    }

    private void validateNotUsed(EntryPass pass) {
        if (usedPasses.getIfPresent(pass.id()) != null) {
            throw new BusinessException(ErrorCode.ENTRY_PASS_USED);
        }
    }

    private OAuth2TokenValidator<Jwt> notExpired(Clock clock) {
        return jwt -> {
            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt == null || !Instant.now(clock).isBefore(expiresAt)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(PASS_EXPIRED));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }
}
