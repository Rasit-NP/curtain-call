package com.curtaincall.support;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.curtaincall.auth.domain.User;
import com.curtaincall.auth.domain.UserRepository;

/**
 * 통합 테스트가 직접 사용자를 만든다(시드에 기대지 않는다). 비밀번호는 운영과 같은 인코더로 해시한다.
 */
public class TestUsers {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 사용자를 만들고 id를 돌려준다. */
    public long create(String username, String password) {
        return userRepository.save(new User(username, passwordEncoder.encode(password))).getId();
    }
}
