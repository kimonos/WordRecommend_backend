package com.example.wordrecommend_backend.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    public String generateTokenFromEmail(String email) {
        Map<String, Object> claims = new HashMap<>();
        // 如需大小寫一致，可統一為小寫：
        return createToken(claims, email == null ? "" : email.trim().toLowerCase());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder().setClaims(claims).setSubject(subject).setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS256, secretKey).compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String sub = extractUsername(token); // 我們把 sub 當 email/identifier
            if (isTokenExpired(token)) return false;

            // 若你的 UserDetails 有 getEmail() / getUsername()
            if (userDetails instanceof com.example.wordrecommend_backend.entity.User u) {
                return sub.equalsIgnoreCase(u.getEmail()) || sub.equalsIgnoreCase(u.getUsername());
            }
            // 一般情況：至少跟 getUsername() 比一次
            return sub.equalsIgnoreCase(userDetails.getUsername());
        } catch (Exception e) {
            return false;
        }
    }
}