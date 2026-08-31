package com.chatroom.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 令牌管理组件。
 * 生成登录令牌、校验令牌有效性、从令牌中提取用户信息。
 * WebSocket 和 HTTP 请求共用同一套 JWT 认证体系。
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    /** 令牌过期时间（毫秒），默认 7 天 */
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        // 从 Base64 配置密钥生成 HMAC-SHA 签名密钥
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    /** 生成 JWT 令牌，payload 包含 userId（subject）和 username */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** 从令牌中解析出用户 ID */
    public Long getUserId(String token) {
        return Long.parseLong(
                Jwts.parser().verifyWith(key).build()
                        .parseSignedClaims(token)
                        .getPayload().getSubject()
        );
    }

    /** 从令牌中解析出用户名 */
    public String getUsername(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload().get("username", String.class);
    }

    /** 校验令牌是否有效（过期、签名错误等返回 false） */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
