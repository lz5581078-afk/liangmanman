package com.liangmanman.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.liangmanman.config.AppProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class TokenService {

    private static final String ISSUER = "liangmanman";

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final int days;

    public TokenService(AppProperties props) {
        if (props.jwtSecret() == null || props.jwtSecret().length() < 32) {
            throw new IllegalStateException("app.jwt-secret 至少 32 个字符");
        }
        this.algorithm = Algorithm.HMAC256(props.jwtSecret());
        this.verifier = JWT.require(algorithm).withIssuer(ISSUER).build();
        this.days = props.jwtDays();
    }

    public String issue(Long userId) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(String.valueOf(userId))
                .withIssuedAt(now)
                .withExpiresAt(now.plus(days, ChronoUnit.DAYS))
                .sign(algorithm);
    }

    private static final String ADMIN_PREFIX = "admin:";

    /** 运营后台 token：subject 带 admin: 前缀，与用户 token 互不通用；有效期 12 小时 */
    public String issueAdmin(Long adminId) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(ADMIN_PREFIX + adminId)
                .withIssuedAt(now)
                .withExpiresAt(now.plus(12, ChronoUnit.HOURS))
                .sign(algorithm);
    }

    public Long parseAdmin(String token) {
        try {
            String sub = verifier.verify(token).getSubject();
            return sub != null && sub.startsWith(ADMIN_PREFIX) ? Long.valueOf(sub.substring(ADMIN_PREFIX.length())) : null;
        } catch (JWTVerificationException | NumberFormatException e) {
            return null;
        }
    }

    /** 无效或过期返回 null（运营后台 token 在这里也返回 null） */
    public Long parse(String token) {
        try {
            return Long.valueOf(verifier.verify(token).getSubject());
        } catch (JWTVerificationException | NumberFormatException e) {
            return null;
        }
    }
}
