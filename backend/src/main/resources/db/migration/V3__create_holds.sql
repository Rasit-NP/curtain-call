-- 선점(spec 2.1, 2.3, 결정 0070).
--
-- 선점 1건은 한 공연의 좌석 묶음(1~4석)이다(결정 0003). 만료는 저장하지 않는다(R3 지연 만료). 만료된 선점도 status는 ACTIVE이고,
-- 유효한지는 요청을 처리할 때 현재 시각과 expires_at으로 판정한다. status를 바꾸는 것은 해제(RELEASED)와 결제(PAID)뿐이다.
CREATE TABLE holds (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT                   NOT NULL REFERENCES users (id),
    performance_id BIGINT                   NOT NULL REFERENCES performances (id),
    status         VARCHAR(20)              NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_holds_status CHECK (status IN ('ACTIVE', 'RELEASED', 'PAID'))
);

-- 보유 좌석 수(R2): 같은 사용자·같은 공연의 ACTIVE 선점을 찾는다.
CREATE INDEX ix_holds_user_performance_status ON holds (user_id, performance_id, status);

-- 선점의 좌석 목록(만든 때의 묶음). 만료된 선점의 좌석을 새 선점이 덮어써도 원래 선점의 목록에서 빼지 않는다.
-- 그래서 한 좌석이 여러 선점의 목록에 있을 수 있고, 좌석을 지금 잡은 선점은 seats.hold_id로 판단한다(결정 0009).
CREATE TABLE hold_seats (
    hold_id BIGINT NOT NULL REFERENCES holds (id),
    seat_id BIGINT NOT NULL REFERENCES seats (id),
    CONSTRAINT pk_hold_seats PRIMARY KEY (hold_id, seat_id)
);

-- 좌석을 지금 잡은 선점(spec 2.1). 저장 상태가 HELD일 때만 있다. 해제는 holdId를 비우며 AVAILABLE로, 결제는 비우며 SOLD로 바꾼다.
ALTER TABLE seats ADD COLUMN hold_id BIGINT REFERENCES holds (id);
ALTER TABLE seats ADD CONSTRAINT ck_seats_hold_id_only_when_held CHECK ((status = 'HELD') = (hold_id IS NOT NULL));
