-- 운영(prod) 시연 계정(Issue #71, 결정 0211·0213). prod 프로필의 Flyway 위치(db/demo)에서만 적용한다. local·e2e·test는 읽지 않는다.
-- 시연 전용 계정이라 비밀번호를 저장소에 둔다(루트 CLAUDE.md '금지 사항'의 예외, README 안내). 실제 서비스 계정이 아니다.
--   demo1 ~ demo5 / demo1234
-- E2E 계정(e2e-user1, e2e-user2)과 로컬 시드의 demo는 넣지 않는다.
-- 해시는 PasswordEncoderFactories.createDelegatingPasswordEncoder()로 계정마다 따로 만들었다(BCrypt, strength 10).
--
-- 반복 마이그레이션(R__)이다. 배포 서버의 DB는 초기화하지 않으므로(결정 0211), 이 파일이 바뀌면 Flyway가 다음 배포에서 다시 실행한다.
-- 그래서 없는 계정만 넣고 있는 계정은 건드리지 않는다(ON CONFLICT DO NOTHING). 있는 계정의 비밀번호를 바꾸려면 새 버전 마이그레이션으로 한다.
INSERT INTO users (username, password_hash) VALUES
    ('demo1', '{bcrypt}$2a$10$pukdYzAwKA1fNPpEmms8YOGZASqbFKztqyT2ga6HCmv6rBpNOv0iK'),
    ('demo2', '{bcrypt}$2a$10$BPEVS/jGIsnaNwRcMZNf6.X6Jl.87ipStRaaiaCuxXTKzm/6zjnnK'),
    ('demo3', '{bcrypt}$2a$10$0VRxzgfG3bSO9l3.sKC/7O5pvopIn8HghrdLl3SnszoJUcIgHKEm2'),
    ('demo4', '{bcrypt}$2a$10$qCRZTFGPLpI1EqJBcYhaFujSPo7VpBDkxuGRiYWJkFdUPtzJasTHq'),
    ('demo5', '{bcrypt}$2a$10$Gdf9yqP3lKCcZjKxRghple1PFUwi7DcPFZalcXk7MU12AVht1upBa')
ON CONFLICT (username) DO NOTHING;
