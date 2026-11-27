-- 예매·결제(spec 2.1, 2.3, 결정 0140).
--
-- 결제 승인: 선점 PAID, 좌석 SOLD(hold_id를 비움), 예매 생성, 결제 SUCCEEDED. 결제 거절: 결제 FAILED만 남고 선점·좌석은 그대로다.
-- 멱등 기록(같은 키의 결과 저장·재사용)은 다음 마이그레이션에서 따로 만든다(#56).

-- 예매: 결제가 승인된 선점 하나로 하나 생긴다. 취소 기능이 없으므로(spec 8) 지우거나 바꾸지 않는다.
CREATE TABLE reservations (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT                   NOT NULL REFERENCES users (id),
    performance_id BIGINT                   NOT NULL REFERENCES performances (id),
    hold_id        BIGINT                   NOT NULL REFERENCES holds (id),
    amount         BIGINT                   NOT NULL,
    reserved_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    -- 한 선점으로 예매는 하나다(같은 선점을 두 번 결제해 예매가 둘 생기는 것을 DB가 막는다).
    CONSTRAINT uk_reservations_hold UNIQUE (hold_id),
    CONSTRAINT ck_reservations_amount_not_negative CHECK (amount >= 0)
);

-- 보유 좌석 수(R2)의 예매 몫: 같은 사용자·같은 공연의 예매 좌석을 센다.
CREATE INDEX ix_reservations_user_performance ON reservations (user_id, performance_id);
-- 내 예매(spec 6.8): reserved_at 내림차순, 같으면 id 내림차순.
CREATE INDEX ix_reservations_user_reserved_at ON reservations (user_id, reserved_at DESC, id DESC);

-- 예매의 좌석(선점의 좌석 묶음을 그대로 옮긴다). 취소가 없으므로 좌석 하나는 예매 하나에만 들어간다(같은 좌석을 두 번 파는 것을 DB가 막는다).
CREATE TABLE reservation_seats (
    reservation_id BIGINT NOT NULL REFERENCES reservations (id),
    seat_id        BIGINT NOT NULL REFERENCES seats (id),
    CONSTRAINT pk_reservation_seats PRIMARY KEY (reservation_id, seat_id),
    CONSTRAINT uk_reservation_seats_seat UNIQUE (seat_id)
);

-- 결제: 모의 결제를 부를 때마다 하나 생긴다(spec 2.1). 승인되면 SUCCEEDED와 예매, 거절되면 FAILED이고 예매가 없다.
-- 만료(409)·없는 선점(404)으로 거부한 요청은 모의 결제를 부르지 않으므로 결제 행이 없다.
CREATE TABLE payments (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT                   NOT NULL REFERENCES users (id),
    hold_id        BIGINT                   NOT NULL REFERENCES holds (id),
    amount         BIGINT                   NOT NULL,
    status         VARCHAR(20)              NOT NULL,
    reservation_id BIGINT                   REFERENCES reservations (id),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_payments_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_payments_reservation_only_when_succeeded CHECK ((status = 'SUCCEEDED') = (reservation_id IS NOT NULL)),
    CONSTRAINT uk_payments_reservation UNIQUE (reservation_id),
    CONSTRAINT ck_payments_amount_not_negative CHECK (amount >= 0)
);
