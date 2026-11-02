package com.ticketflow.fixture;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jwt.SignedJWT;
import com.ticketflow.TestClock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.util.UUID;

import static com.ticketflow.domain.admission.service.EntryPasses.ENTRY_TOKEN;

@Component
@RequiredArgsConstructor
public class EntryPassTokens {

    private static final String SECRET = "test-only-entry-pass-secret-at-least-32-bytes";
    private static final String FORGED_SECRET = "someone-elses-secret-at-least-32-bytes!!";

    private static final Long VIEWER_ID = 999_999L;

    private final TestClock testClock;
    private final AuthHeaders authHeaders;

    public String issue(Long memberId, Long showtimeId) {
        return sign(SECRET, memberId, showtimeId);
    }

    public RequestPostProcessor viewer(Long showtimeId) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, authHeaders.bearer(VIEWER_ID));
            request.addHeader(ENTRY_TOKEN, issue(VIEWER_ID, showtimeId));
            return request;
        };
    }

    public String forge(Long memberId, Long showtimeId) {
        return sign(FORGED_SECRET, memberId, showtimeId);
    }

    public static String idOf(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet().getJWTID();
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String sign(String secret, Long memberId, Long showtimeId) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(memberId))
                .claim("showtimeId", showtimeId)
                .expiresAt(testClock.instant().plus(Duration.ofMinutes(5)))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
