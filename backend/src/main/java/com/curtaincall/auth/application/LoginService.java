package com.curtaincall.auth.application;

import java.util.Collection;
import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.curtaincall.auth.domain.UserRepository;
import com.curtaincall.common.api.ApiException;
import com.curtaincall.common.api.ErrorCode;

/**
 * 로그인(spec 6.1). 아이디·비밀번호를 확인하고 액세스 토큰을 발급한다.
 *
 * <p>비밀번호 확인은 Spring Security의 DaoAuthenticationProvider에 맡긴다. 없는 아이디도 비밀번호를 비교하는 만큼
 * 시간을 써서(타이밍 공격 방지) 틀린 비밀번호와 구분되지 않고, 둘 다 BadCredentialsException이 된다.
 * 이 예외는 여기서 401 INVALID_CREDENTIALS로 바꾼다. 공통 처리기의 catch-all에 걸려 500이 되지 않게 하기 위해서다(PR #8).
 * 그 밖의 인증 예외(예: DB 오류를 감싼 InternalAuthenticationServiceException)는 서버 오류이므로 그대로 500이 된다.
 *
 * <p>provider는 빈으로 등록하지 않는다. 등록하면 Spring Security가 전역 인증 수단으로 쓸 수 있어, JWT 인증 외의 경로가 생긴다.
 */
@Service
public class LoginService {

    private final DaoAuthenticationProvider authenticator;
    private final AccessTokenIssuer tokenIssuer;

    public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder, AccessTokenIssuer tokenIssuer) {
        this.authenticator = new DaoAuthenticationProvider(username -> userRepository.findByUsername(username)
                .map(user -> new LoginUser(user.getId(), user.getUsername(), user.getPasswordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("없는 아이디")));
        this.authenticator.setPasswordEncoder(passwordEncoder);
        this.tokenIssuer = tokenIssuer;
    }

    public AccessToken login(String username, String password) {
        Authentication authentication;
        try {
            authentication = authenticator.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        } catch (BadCredentialsException e) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        LoginUser user = (LoginUser) authentication.getPrincipal();
        return tokenIssuer.issue(user.id());
    }

    /** 로그인 확인에만 쓰는 사용자 정보. 권한은 쓰지 않는다. */
    record LoginUser(long id, String username, String password) implements UserDetails {

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of();
        }

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        public String getUsername() {
            return username;
        }
    }
}
