-- 테스트 계정 시드(spec 2.5). local·e2e 프로필에서만 적용한다(test·prod는 db/seed를 읽지 않는다).
-- 시연·E2E 전용 계정이라 비밀번호를 저장소에 둔다(루트 CLAUDE.md '금지 사항'의 예외). 실제 서비스 계정이 아니다.
--   demo      / demo1234   시연용(README 안내)
--   e2e-user1 / e2e-pass1  E2E용(시나리오 1~4의 로그인 사용자)
--   e2e-user2 / e2e-pass2  E2E용(시나리오 2의 '다른 사용자')
-- 해시는 PasswordEncoderFactories.createDelegatingPasswordEncoder()로 만들었다(BCrypt, strength 10).
INSERT INTO users (username, password_hash) VALUES
    ('demo',      '{bcrypt}$2a$10$JQWcidklhxadsKXooIIuVOjvbEcfom2Xyjmab0bc8rtdwGvLqtaZi'),
    ('e2e-user1', '{bcrypt}$2a$10$GAAB1OvRIf5VaOg0Ao7bcOHOhRBfNfGI4bF6WGEdcm5eyfZGuaPT6'),
    ('e2e-user2', '{bcrypt}$2a$10$Z75mL2dRMMzpXGOA/RHm/Oep5t9EgjDxeJTPEaRo4AhNObCaQ6Ssi');
