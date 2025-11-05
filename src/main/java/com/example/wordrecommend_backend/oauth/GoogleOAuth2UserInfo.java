package com.example.wordrecommend_backend.oauth;

import java.util.Map;

public class GoogleOAuth2UserInfo implements OAuth2UserInfo {
    private final Map<String,Object> a;
    public GoogleOAuth2UserInfo(Map<String,Object> attrs){ this.a = attrs; }

    @Override public String getId() { return (String) a.get("sub"); }
    @Override public String getName() { return (String) a.getOrDefault("name", ""); }
    @Override public String getEmail() { return (String) a.getOrDefault("email", ""); }
    @Override public String getImageUrl() { return (String) a.getOrDefault("picture", ""); }
    @Override public Boolean getEmailVerified() {
        Object v = a.get("email_verified");
        return (v instanceof Boolean) ? (Boolean) v : null;
    }
}