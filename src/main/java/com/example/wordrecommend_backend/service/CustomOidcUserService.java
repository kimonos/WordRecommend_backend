package com.example.wordrecommend_backend.service;

import com.example.wordrecommend_backend.entity.AuthProvider;
import com.example.wordrecommend_backend.entity.User;
import com.example.wordrecommend_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService implements org.springframework.security.oauth2.client.userinfo.OAuth2UserService<OidcUserRequest, OidcUser> {

    private final UserRepository userRepository;

    // 先用 Spring 內建的 OIDC service 把 claims 拿回來
    private final OidcUserService delegate = new OidcUserService();

    @Override
    public OidcUser loadUser(OidcUserRequest req) throws OAuth2AuthenticationException {
        // 從 Google 取回 OIDC 使用者（含 id_token / userinfo）
        OidcUser oidc = delegate.loadUser(req);
        Map<String, Object> claims = oidc.getClaims();

        // 1) 取 email（OIDC 正常會有），並一律轉小寫
        String email = asStr(claims.get("email"));
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("No email claim from OIDC provider");
        }
        String emailLower = email.toLowerCase(Locale.ROOT);

        // 2) 其他欄位
        String name    = firstNonBlank(asStr(claims.get("name")), usernameFromEmail(emailLower));
        String avatar  = asStr(claims.get("picture"));
        String sub     = asStr(claims.get("sub")); // provider id
        boolean verified = Boolean.TRUE.equals(claims.get("email_verified"));

        // 3) upsert（用 if/else，避免 lambda「有效 final」限制）
        User user = userRepository.findByEmailIgnoreCase(emailLower).orElse(null);

        if (user != null) {
            // 更新舊使用者
            if (name != null && !name.isBlank())   user.setUsername(name);
            if (avatar != null && !avatar.isBlank()) user.setAvatarUrl(avatar);
            user.setProvider(AuthProvider.GOOGLE);
            user.setProviderId(sub);
            if (verified) user.setEmailVerified(true);
        } else {
            // 建立新使用者
            user = new User();
            user.setEmail(emailLower);
            user.setUsername(name);
            user.setAvatarUrl(avatar);
            user.setProvider(AuthProvider.GOOGLE);
            user.setProviderId(sub);
            user.setEmailVerified(verified);
        }

        // 立刻 flush，避免 SuccessHandler 立即查不到剛 upsert 的資料
        userRepository.saveAndFlush(user);

        // 4) 回傳 OIDC 使用者；nameAttributeKey 設 "email" 讓後續 handler 可直接取用
        Collection<? extends GrantedAuthority> authorities =
                oidc.getAuthorities() == null || oidc.getAuthorities().isEmpty()
                        ? List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        : oidc.getAuthorities();

        // 保留原本的 idToken / userInfo，並指定以 "email" 當唯一識別
        return new DefaultOidcUser(authorities, oidc.getIdToken(), oidc.getUserInfo(), "email");
    }

    // ---------- helpers ----------
    private static String asStr(Object o){ return o == null ? null : o.toString(); }

    private static String firstNonBlank(String... ss){
        for (String s : ss) if (s != null && !s.isBlank()) return s;
        return null;
    }

    private static String usernameFromEmail(String email){
        int i = email.indexOf('@');
        return i > 0 ? email.substring(0, i) : email;
    }
}
