-- 공연장과 좌석 배치 시드(spec 2.5, 결정 0049). local·e2e 프로필에서만 적용한다(test·prod는 db/seed를 읽지 않는다).
-- 값은 샘플 데이터다. 코드와 테스트는 이 값에 기대지 않는다.
-- grid_row·grid_column은 배치 격자의 줄·칸(1부터)이고, 좌석이 없는 칸이 통로나 빈자리다. 격자 1번째 줄이 무대 쪽이다.

INSERT INTO venues (name) VALUES
    ('대학로 커튼홀'),
    ('혜화 다락극장');

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
ORDER BY r.grid_row, n.seat_number;

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
ORDER BY n.seat_number;

INSERT INTO venue_seats (venue_id, seat_row, seat_number, grid_row, grid_column)
SELECT v.id, r.seat_row, n.seat_number, r.grid_row, n.seat_number
FROM venues v
CROSS JOIN (VALUES ('B', 2), ('C', 4), ('D', 5)) AS r (seat_row, grid_row)
CROSS JOIN generate_series(1, 8) AS n (seat_number)
WHERE v.name = '혜화 다락극장'
ORDER BY r.grid_row, n.seat_number;
