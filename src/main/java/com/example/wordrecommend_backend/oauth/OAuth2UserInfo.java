package com.example.wordrecommend_backend.oauth;

public interface OAuth2UserInfo {
    String getId();
    String getName();
    String getEmail();
    String getImageUrl();
    Boolean getEmailVerified();
}