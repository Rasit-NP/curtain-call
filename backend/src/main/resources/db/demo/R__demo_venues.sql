-- 운영(prod) 공연장과 좌석 배치(Issue #71, 결정 0211·0213). prod 프로필의 Flyway 위치(db/demo)에서만 적용한다.
-- 값은 샘플 데이터이고 local·e2e 시드(db/seed/V1001__seed_venues.sql)와 같은 두 공연장이다. 코드와 테스트는 이 값에 기대지 않는다.
-- 공연과 공연 좌석은 넣지 않는다. 운영의 공연은 서버가 주기적으로 만든다(#73).
-- grid_row·grid_column은 배치 격자의 줄·칸(1부터)이고, 좌석이 없는 칸이 통로나 빈자리다. 격자 1번째 줄이 무대 쪽이다.
--
-- 반복 마이그레이션(R__)이다. 배포 서버의 DB는 초기화하지 않으므로(결정 0211), 이 파일이 바뀌면 Flyway가 다음 배포에서 다시 실행한다.
-- 그래서 없는 공연장·좌석만 넣고 있는 행은 바꾸거나 지우지 않는다(ON CONFLICT DO NOTHING).
-- 있는 공연장의 배치를 바꾸거나 좌석을 빼려면 새 버전 마이그레이션으로 한다. 이미 만든 공연의 좌석은 배치를 복사해 두었으므로 바뀌지 않는다(결정 0048).

INSERT INTO venues (name) VALUES
    ('대학로 커튼홀'),
    ('혜화 다락극장')
ON CONFLICT (name) DO NOTHING;

-- 대학로 커튼홀: A~E 5줄, 줄마다 8석(40석). 4번과 5번 좌석 사이(격자 5번째 칸)가 세로 통로다.
--   칸:  1  2  3  4  5  6  7  8  9
--   A    1  2  3  4  .  5  6  7  8
--   ...  (B~E 같음)
INSERT INTO venue_seats (venue_id, seat_row, seat_number, grid_row, grid_column)
SELECT v.id, r.seat_row, n.seat_number, r.grid_row,
       CASE WHEN n.seat_number <= 4 THEN n.seat_number ELSE n.seat_number + 1 END
FROM venues v
CROSS JOIN (VALUES ('A', 1), ('B', 2), ('C', 3), ('D', 4), ('E', 5)) AS r (seat_row, grid_row)
CROSS JOIN generate_series(1, 8) AS n (seat_number)
WHERE v.name = '대학로 커튼홀'
ORDER BY r.grid_row, n.seat_number
ON CONFLICT DO NOTHING;

-- 혜화 다락극장: 4줄 30석. A줄은 6석이라 양 끝(격자 1·8번째 칸)이 빈자리이고, B줄과 C줄 사이(격자 3번째 줄)가 가로 통로다.
--   칸:  1  2  3  4  5  6  7  8
--   A    .  1  2  3  4  5  6  .
--   B    1  2  3  4  5  6  7  8
--   (통로)
--   C    1  2  3  4  5  6  7  8
--   D    1  2  3  4  5  6  7  8
INSERT INTO venue_seats (venue_id, seat_row, seat_number, grid_row, grid_column)
SELECT v.id, 'A', n.seat_number, 1, n.seat_number + 1
FROM venues v
CROSS JOIN generate_series(1, 6) AS n (seat_number)
WHERE v.name = '혜화 다락극장'
ORDER BY n.seat_number
ON CONFLICT DO NOTHING;

INSERT INTO venue_seats (venue_id, seat_row, seat_number, grid_row, grid_column)
SELECT v.id, r.seat_row, n.seat_number, r.grid_row, n.seat_number
FROM venues v
CROSS JOIN (VALUES ('B', 2), ('C', 4), ('D', 5)) AS r (seat_row, grid_row)
CROSS JOIN generate_series(1, 8) AS n (seat_number)
WHERE v.name = '혜화 다락극장'
ORDER BY r.grid_row, n.seat_number
ON CONFLICT DO NOTHING;
