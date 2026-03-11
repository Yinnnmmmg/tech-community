package com.ying.tech.community.core.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JWTUtils {
    // 密钥 (实际开发中应该放在配置文件里，这里为了简单直接写死)
    // 必须足够长，否则会报错
    private static final String SECRET = "TechCommunitySecretKeyForJwtTokenGeneration2026";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes());

    // 过期时间：1天 (毫秒)
    private static final long EXPIRATION = 24 * 60 * 60 * 1000L;

    /**
     * 生成 Token
     * @param userId 用户ID
     * @return 加密后的 Token 字符串
     */
    public static String createToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);

        return Jwts.builder()
                .claims(claims)
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(KEY)
                .compact();
    }

    /**
     * 解析 Token 获取用户ID
     */
    public static Long getUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.parseLong(claims.get("userId").toString());
        } catch (Exception e) {
            return null; // 解析失败（比如过期或被篡改）
        }
    }
}
