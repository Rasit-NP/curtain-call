-- 공연장, 공연장 좌석 배치, 공연, 공연 좌석(spec 2.1, 결정 0048).
--
-- 좌석 위치: grid_row·grid_column은 배치 격자의 줄·칸이고 1부터 센다. 좌석이 없는 칸이 통로나 빈자리다.
-- seat_row·seat_number는 표시 이름({row}-{number}, 예: A-3)이다. row는 SQL 키워드라 seat_ 접두어를 붙인다.
-- seat_row는 COLLATE "C"로 둔다. 좌석 목록의 row 오름차순(spec 5)이 DB 로캘과 상관없이 코드 포인트 순서가 되게 한다.
--
-- 공연 좌석(seats)은 공연을 만들 때 그 공연장 배치(venue_seats)를 복사한다(스냅샷). 좌석은 공연마다 선점·판매 상태를 가지므로
-- 공연마다 있어야 하고, 같은 공연장의 공연은 같은 배치에서 시작한다. 이미 판매한 공연의 좌석은 공연장 배치가 나중에 바뀌어도
-- 그대로여야 하므로 배치를 참조하지 않고 값을 가진다. 제약도 공연 좌석에 직접 건다.

CREATE TABLE venues (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_venues_name UNIQUE (name),
    CONSTRAINT ck_venues_name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE venue_seats (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id    BIGINT      NOT NULL REFERENCES venues (id),
    seat_row    VARCHAR(10) COLLATE "C" NOT NULL,
    seat_number INTEGER     NOT NULL,
    grid_row    INTEGER     NOT NULL,
    grid_column INTEGER     NOT NULL,
    CONSTRAINT uk_venue_seats_name UNIQUE (venue_id, seat_row, seat_number),
    CONSTRAINT uk_venue_seats_grid UNIQUE (venue_id, grid_row, grid_column),
    CONSTRAINT ck_venue_seats_row_not_blank CHECK (seat_row <> ''),
    CONSTRAINT ck_venue_seats_positive CHECK (seat_number >= 1 AND grid_row >= 1 AND grid_column >= 1)
);

CREATE TABLE performances (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title     VARCHAR(200)             NOT NULL,
    venue_id  BIGINT                   NOT NULL REFERENCES venues (id),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- 1석 가격(원). 전석이 같다(spec 2.1).
    price     INTEGER                  NOT NULL,
    CONSTRAINT ck_performances_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_performances_price_not_negative CHECK (price >= 0)
);

-- 공연 목록(spec 6.2)의 정렬: starts_at 오름차순, 같으면 id 오름차순.
CREATE INDEX ix_performances_starts_at_id ON performances (starts_at, id);
CREATE INDEX ix_performances_venue_id ON performances (venue_id);

CREATE TABLE seats (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    performance_id BIGINT      NOT NULL REFERENCES performances (id),
    seat_row       VARCHAR(10) COLLATE "C" NOT NULL,
    seat_number    INTEGER     NOT NULL,
    grid_row       INTEGER     NOT NULL,
    grid_column    INTEGER     NOT NULL,
    -- 저장 상태. API 상태(만료 반영, spec 2.2)는 선점 Issue에서 hold_id와 함께 계산한다.
    status         VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT uk_seats_name UNIQUE (performance_id, seat_row, seat_number),
    CONSTRAINT uk_seats_grid UNIQUE (performance_id, grid_row, grid_column),
    CONSTRAINT ck_seats_row_not_blank CHECK (seat_row <> ''),
    CONSTRAINT ck_seats_positive CHECK (seat_number >= 1 AND grid_row >= 1 AND grid_column >= 1),
    CONSTRAINT ck_seats_status CHECK (status IN ('AVAILABLE', 'HELD', 'SOLD'))
);
