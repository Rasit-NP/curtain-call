-- 사용자(spec 2.1). 회원가입은 없고 시드로만 만든다. username은 고유하다.
-- password_hash는 DelegatingPasswordEncoder 형식({bcrypt}$2a$...)이다.
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);
