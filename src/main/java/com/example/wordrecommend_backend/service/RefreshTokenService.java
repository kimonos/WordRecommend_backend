package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.entity.RefreshToken;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.repository.RefreshTokenRepository;
import com.example.wordrecommend_backend.repository.UserRepository;
import com.example.wordrecommend_backend.util.JwtUtil;
import com.example.wordrecommend_backend.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RNG = new SecureRandom();

    private final RefreshTokenRepository refreshRepo;
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;

    // === Cookie 與期限設定（application.properties 供應） ===
    @Value("${app.security.jwt-cookie-name:APP_TOKEN}")
    private String atCookieName;

    @Value("${app.security.at-cookie-path:/}")
    private String atCookiePath;

    @Value("${app.security.rt-cookie-name:APP_RT}")
    private String rtCookieName;

    @Value("${app.security.rt-cookie-path:/auth}")
    private String rtCookiePath;

    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.security.cookie-samesite:Lax}")
    private String cookieSameSite;

    @Value("${jwt.expiration:3600000}")
    private long atTtlMs;

    @Value("${app.security.rt-expiration:2592000000}")
    private long rtTtlMs;

    // RT 的 HMAC 祕密（只存 hash，不存原文）
    @Value("${refresh.hmac.secret}")
    private String refreshHmacSecret;

    /* =========================================================
       1) 登入 / OAuth 成功：簽「新家族」的 AT + RT
       ========================================================= */
    @Transactional
    public void issueNewFamilyTokensForEmail(String email,
                                             HttpServletRequest req,
                                             HttpServletResponse res) {
        User user = userRepo.findByEmailIgnoreCase(email).orElseThrow();

        // (A) Access Token（短效）
        String at = jwtUtil.generateTokenFromEmail(user.getEmail());
        CookieUtil.writeCookie(res, atCookieName, at, (int) (atTtlMs / 1000), cookieSecure, cookieSameSite, atCookiePath);

        // (B) Refresh Token（長效；建立新 family）
        UUID jti = UUID.randomUUID();
        UUID family = UUID.randomUUID();
        String rtRaw = randomToken();      // 高熵原文
        String hash = hmac(rtRaw);         // DB 只存 HMAC 雜湊

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setJti(jti);
        rt.setJtiHash(hash);
        rt.setFamilyId(family);
        rt.setParentJti(null);
        rt.setReplacedBy(null);
        rt.setCreatedAt(Instant.now());
        rt.setExpiresAt(Instant.now().plusMillis(rtTtlMs));
        rt.setUserAgent(optional(req.getHeader("User-Agent")));
        rt.setIpAddress(optional(ip(req)));

        refreshRepo.save(rt);

        // 寫 RT Cookie（Path 縮小至 /auth）
        CookieUtil.writeCookie(res, rtCookieName, rtRaw, (int) (rtTtlMs / 1000), cookieSecure, cookieSameSite, rtCookiePath);

        if (log.isDebugEnabled()) {
            log.debug("Issued new family: user={} family={} jti={}", user.getId(), family, jti);
        }
    }

    /* =========================================================
       2) /auth/refresh：驗證 RT + 旋轉（撤銷舊、產生新）
       ========================================================= */
    @Transactional
    public void refreshAndRotate(HttpServletRequest req, HttpServletResponse res) {
        String rtRaw = extractCookie(req, rtCookieName);
        if (rtRaw == null || rtRaw.isBlank()) {
            throw new RuntimeException("No refresh token cookie");
        }

        String hash = hmac(rtRaw);
        RefreshToken current = refreshRepo.findByJtiHash(hash)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        Instant now = Instant.now();

        // 已過期、已撤銷、或已被取代 → 視為「重用」（reuse）
        if (now.isAfter(current.getExpiresAt()) || current.getRevokedAt() != null || current.getReplacedBy() != null) {
            revokeFamily(current.getFamilyId(), now);
            clearBothCookies(res);
            throw new RuntimeException("Refresh token reuse detected; family revoked");
        }

        // 旋轉：撤銷舊、建立新
        current.setRevokedAt(now);

        UUID newJti = UUID.randomUUID();
        String newRtRaw = randomToken();
        String newHash = hmac(newRtRaw);

        RefreshToken next = new RefreshToken();
        next.setUser(current.getUser());
        next.setJti(newJti);
        next.setJtiHash(newHash);
        next.setFamilyId(current.getFamilyId());
        next.setParentJti(current.getJti());
        next.setReplacedBy(null);
        next.setCreatedAt(now);
        next.setExpiresAt(now.plusMillis(rtTtlMs));
        next.setUserAgent(optional(req.getHeader("User-Agent")));
        next.setIpAddress(optional(ip(req)));

        current.setReplacedBy(newJti);
        current.setLastUsedAt(now);

        refreshRepo.save(current);
        refreshRepo.save(next);

        // 簽新 AT + 寫新 RT
        String at = jwtUtil.generateTokenFromEmail(current.getUser().getEmail());
        CookieUtil.writeCookie(res, atCookieName, at, (int) (atTtlMs / 1000), cookieSecure, cookieSameSite, atCookiePath);
        CookieUtil.writeCookie(res, rtCookieName, newRtRaw, (int) (rtTtlMs / 1000), cookieSecure, cookieSameSite, rtCookiePath);

        if (log.isDebugEnabled()) {
            log.debug("Rotated RT: family={} oldJti={} newJti={}", current.getFamilyId(), current.getJti(), newJti);
        }
    }

    /* =========================================================
       3) 登出（單裝置）：若有 RT 就撤銷它
       ========================================================= */
    @Transactional
    public void revokeCurrentRtIfPresent(HttpServletRequest req) {
        String rtRaw = extractCookie(req, rtCookieName);
        if (rtRaw == null || rtRaw.isBlank()) return;

        refreshRepo.findByJtiHash(hmac(rtRaw)).ifPresent(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(Instant.now());
                refreshRepo.save(rt);
                if (log.isDebugEnabled()) {
                    log.debug("Revoked current RT: jti={} family={}", rt.getJti(), rt.getFamilyId());
                }
            }
        });
    }

    /* =========================== Util =========================== */

    private void revokeFamily(UUID family, Instant now) {
        List<RefreshToken> list = refreshRepo.findActiveByFamily(family, now);
        for (RefreshToken rt : list) {
            rt.setRevokedAt(now);
        }
        refreshRepo.saveAll(list);
        if (log.isWarnEnabled()) {
            log.warn("Revoked entire family={} (reuse detected)", family);
        }
    }

    private static String randomToken() {
        byte[] buf = new byte[32]; // 256 bits
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String hmac(String raw) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    refreshHmacSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);  // ✅ 用 JDK 17 內建
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String extractCookie(HttpServletRequest req, String name) {
        if (req.getCookies() == null) return null;
        for (var c : req.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private static String ip(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private static String optional(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    public void clearBothCookies(HttpServletResponse res) {
        CookieUtil.clearCookie(res, atCookieName, cookieSecure, cookieSameSite, atCookiePath);
        CookieUtil.clearCookie(res, rtCookieName, cookieSecure, cookieSameSite, rtCookiePath);
    }
}
