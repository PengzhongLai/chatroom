package com.chatroom.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 令牌的生成、校验与解析。
 *
 * 令牌结构为 header.payload.signature：
 * - payload 是 Base64 编码，**任何人都能读取**，所以不放敏感信息
 * - signature 用服务端密钥计算，用于防篡改（改一个字符签名就对不上）
 *
 * payload 只包含 userId（放在 subject）和 username，不含频道角色等信息。
 */
@Component
public class JwtTokenProvider {

    /** HMAC-SHA 签名密钥，由配置的 Base64 字符串解码而来。密钥只在服务端，泄露即可伪造任意用户 */
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

    /** 生成 JWT 令牌。subject 放 userId，额外带 username、签发时间和过期时间 */
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

    /** 从令牌中解析出用户 ID。解析时会验签，令牌无效会抛 JwtException */
    public Long getUserId(String token) {
        return Long.parseLong(
                Jwts.parser().verifyWith(key).build()
                        .parseSignedClaims(token)
                        .getPayload().getSubject()
        );
    }

    /** 从令牌中解析出用户名（当前实现未被调用，保留供扩展用） */
    public String getUsername(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload().get("username", String.class);
    }

    /** 校验令牌是否有效。签名错误、已过期、格式非法都返回 false，不抛异常 */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
