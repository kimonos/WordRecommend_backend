package com.example.wordrecommend_backend.config;

import com.example.wordrecommend_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsConfig implements UserDetailsService {

    private final UserRepository userRepository;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsConfig.class);

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String id = identifier == null ? "" : identifier.trim();
        log.info("loadUserByUsername called with identifier='{}'", id);

        // 1) 先以 username 查找
        var byUsername = userRepository.findByUsernameIgnoreCase(id);
        if (byUsername.isPresent()) {
            log.debug("Found user by username: {}", byUsername.get().getUsername());
            return byUsername.get();
        }

        // 2) 再以 email 查找
        var byEmail = userRepository.findByEmailIgnoreCase(id);
        if (byEmail.isPresent()) {
            log.debug("Found user by email: {}", byEmail.get().getEmail());
            return byEmail.get();
        }

        // 3) 最後嘗試當作數字 id 查找（若 identifier 可以 parse 成 Long）
        try {
            long numericId = Long.parseLong(id);
            var byId = userRepository.findById(numericId);
            if (byId.isPresent()) {
                log.debug("Found user by id: {}", numericId);
                return byId.get();
            }
        } catch (NumberFormatException ignored) {
            // 不是數字就忽略
        }

        log.warn("User not found for identifier='{}' (tried username, email, id)", id);
        throw new UsernameNotFoundException("User not found for identifier: " + identifier);
    }
}