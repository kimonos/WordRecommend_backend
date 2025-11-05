package com.example.wordrecommend_backend.util;

import jakarta.servlet.http.HttpServletResponse;

public class CookieUtil {
    public static String buildSetCookieHeader(
            String name, String value, int maxAgeSeconds,
            boolean secure, String sameSite
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(value == null ? "" : value).append("; Path=/");
        if (maxAgeSeconds >= 0) sb.append("; Max-Age=").append(maxAgeSeconds);
        sb.append("; HttpOnly");
        if (secure) sb.append("; Secure");
        if (sameSite != null && !sameSite.isBlank()) sb.append("; SameSite=").append(sameSite);
        return sb.toString();
    }
    public static String buildSetCookieHeader(
            String name, String value, int maxAgeSeconds,
            boolean secure, String sameSite, String path
    ) {
        String p = (path == null || path.isBlank()) ? "/" : path;
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(value == null ? "" : value);
        sb.append("; Path=").append(p);
        if (maxAgeSeconds >= 0) sb.append("; Max-Age=").append(maxAgeSeconds);
        sb.append("; HttpOnly");
        if (secure) sb.append("; Secure");
        if (sameSite != null && !sameSite.isBlank()) sb.append("; SameSite=").append(sameSite);
        return sb.toString();
    }

    // 舊版（相容）：固定 Path=/
    public static void writeCookie(HttpServletResponse res,
                                   String name, String value, int maxAgeSeconds,
                                   boolean secure, String sameSite) {
        res.addHeader("Set-Cookie",
                buildSetCookieHeader(name, value, maxAgeSeconds, secure, sameSite, "/"));
    }

    // 新版（推薦）：可指定 Path
    public static void writeCookie(HttpServletResponse res,
                                   String name, String value, int maxAgeSeconds,
                                   boolean secure, String sameSite, String path) {
        res.addHeader("Set-Cookie",
                buildSetCookieHeader(name, value, maxAgeSeconds, secure, sameSite, path));
    }


    public static void clearCookie(HttpServletResponse res,
                                   String name, boolean secure, String sameSite) {
        writeCookie(res, name, "", 0, secure, sameSite, "/");
    }

    // 新版（推薦）：可指定 Path（清除時 Path 必須一致）
    public static void clearCookie(HttpServletResponse res,
                                   String name, boolean secure, String sameSite, String path) {
        writeCookie(res, name, "", 0, secure, sameSite, path);
    }
}
