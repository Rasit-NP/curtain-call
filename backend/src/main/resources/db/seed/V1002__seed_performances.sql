-- 공연과 공연 좌석 시드(spec 2.5, 결정 0049). local·e2e 프로필에서만 적용한다. 공연장 시드(V1001) 뒤에 실행된다.
-- 값은 샘플 데이터다. 코드와 테스트는 이 값에 기대지 않는다. E2E도 id를 가정하지 않고 공연 목록·상세 API로 찾는다(spec 6.9).
-- starts_at은 UTC다. 화면에는 한국 시간(UTC+9)으로 보인다: 1월 15일 19:00, 1월 16일 14:00, 1월 22일 20:00.

INSERT INTO performances (title, venue_id, starts_at, price)
SELECT p.title, v.id, p.starts_at, p.price
FROM (VALUES
        ('작은 방의 햄릿',   '대학로 커튼홀', TIMESTAMPTZ '2027-01-15 10:00:00+00', 30000, 1),
        ('작은 방의 햄릿',   '대학로 커튼홀', TIMESTAMPTZ '2027-01-16 05:00:00+00', 30000, 2),
        ('다락방의 갈매기', '혜화 다락극장', TIMESTAMPTZ '2027-01-22 11:00:00+00', 25000, 3)
     ) AS p (title, venue_name, starts_at, price, seq)
JOIN venues v ON v.name = p.venue_name
ORDER BY p.seq;

-- 공연 좌석은 그 공연장 배치를 복사한다(결정 0048). 모두 빈 좌석(AVAILABLE)으로 시작한다.
-- 선점·예매가 없는 좌석을 HELD·SOLD로 두면 좌석을 잡은 선점(holdId)이 없는 상태가 되므로 넣지 않는다.
INSERT INTO seats (performance_id, seat_row, seat_number, grid_row, grid_column, status)
SELECT p.id, vs.seat_row, vs.seat_number, vs.grid_row, vs.grid_column, 'AVAILABLE'
FROM performances p
JOIN venue_seats vs ON vs.venue_id = p.venue_id
ORDER BY p.id, vs.seat_row, vs.seat_number;
