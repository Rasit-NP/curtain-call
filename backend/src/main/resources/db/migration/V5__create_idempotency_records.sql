-- 멱등 기록(spec 2.1, 6.7.1, R5, 결정 0150).
--
-- 검증(spec 6.7 오류 표 2번)을 통과한 결제 요청마다 하나 생기고, 결과(201, 402, 404, 409)의 응답(상태 코드와 본문)을 저장한다.
-- 같은 사용자가 같은 키로 다시 보내면 다시 처리하지 않고 저장한 응답을 돌려준다. 지우지 않는다(보관 기간 제한 없음).
--
-- 결제는 사용자 행을 잠근 바로 뒤에 INSERT ... ON CONFLICT DO NOTHING으로 이 행을 먼저 넣어 키를 잡고, 같은 트랜잭션에서 결과를 처리한 뒤
-- 응답을 채워 함께 커밋한다(결정 0151). 그래서 응답 열은 그 트랜잭션 안에서만 잠시 비어 있고, 커밋된 행에는 언제나 응답이 있다.
-- 결과를 저장하지 못하고 끝난 요청(500)은 트랜잭션이 되돌려져 행이 남지 않는다.
CREATE TABLE idempotency_records (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT                   NOT NULL REFERENCES users (id),
    idempotency_key VARCHAR(100)             NOT NULL,
    -- 요청의 holdId. 없는 선점으로 끝난 요청(404)도 저장하므로 외래 키를 두지 않는다.
    hold_id         BIGINT                   NOT NULL,
    response_status INTEGER,
    -- 201: 응답 본문(JSON) 그대로. 402·404·409: 오류 본문을 만드는 code와 detail(JSON). 오류 본문은 공통 처리기가 만든다(결정 0150).
    response_body   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    -- 키는 사용자별이다(R5). 같은 사용자·같은 키의 요청이 동시에 오면 나중 요청의 INSERT가 이 제약에서 먼저 요청의 커밋을 기다린다.
    CONSTRAINT uk_idempotency_records_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_idempotency_records_response_together CHECK ((response_status IS NULL) = (response_body IS NULL)),
    -- 400(검증 실패, 같은 키·다른 선점)과 500은 저장하지 않는다.
    CONSTRAINT ck_idempotency_records_response_status CHECK (response_status IN (201, 402, 404, 409))
);
