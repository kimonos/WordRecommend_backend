package com.example.wordrecommend_backend.oauth;

import java.util.Map;

public class GithubOAuth2UserInfo implements OAuth2UserInfo {
    private final Map<String,Object> a;
    public GithubOAuth2UserInfo(Map<String,Object> attrs){ this.a = attrs; }

    @Override public String getId() { return String.valueOf(a.get("id")); }
    @Override public String getName() {
        Object name = a.get("name");
        return name != null ? name.toString() : String.valueOf(a.getOrDefault("login",""));
    }
    @Override public String getEmail() {
        Object email = a.get("email"); // 可能為 null
        return email != null ? email.toString() : "";
    }
    @Override public String getImageUrl() { return (String) a.getOrDefault("avatar_url", ""); }
    @Override public Boolean getEmailVerified() { return null; } // GitHub 無此欄; }
}