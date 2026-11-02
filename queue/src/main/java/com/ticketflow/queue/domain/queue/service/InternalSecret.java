package com.ticketflow.queue.domain.queue.service;

import com.ticketflow.queue.global.exception.BusinessException;
import com.ticketflow.queue.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalSecret {

    private final byte[] secret;

    public InternalSecret(@Value("${queue.internal-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public void validate(String given) {
        if (given == null || !MessageDigest.isEqual(secret, given.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.INTERNAL_SECRET_INVALID);
        }
    }
}
