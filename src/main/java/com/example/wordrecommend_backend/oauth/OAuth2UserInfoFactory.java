package com.example.wordrecommend_backend.oauth;


public class OAuth2UserInfoFactory {
    public static OAuth2UserInfo from(String registrationId, java.util.Map<String,Object> attributes){
        if ("google".equalsIgnoreCase(registrationId)) return new GoogleOAuth2UserInfo(attributes);
        if ("github".equalsIgnoreCase(registrationId)) return new GithubOAuth2UserInfo(attributes);
        throw new IllegalArgumentException("Unsupported provider: " + registrationId);
    }
}