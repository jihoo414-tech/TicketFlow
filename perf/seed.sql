SET SESSION cte_max_recursion_depth = 20000;

INSERT INTO member (name)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 10000)
SELECT CONCAT('perf-member-', n) FROM seq;

INSERT INTO performance (title) VALUES ('perf-performance');

INSERT INTO showtime (performance_id, starts_at, booking_open_at, price) VALUES
    (1, '2099-12-31 19:00:00', '2020-01-01 00:00:00', 150000),
    (1, '2099-12-31 19:00:00', '2020-01-01 00:00:00', 150000);

INSERT INTO seat (showtime_id, seat_row, seat_no)
WITH RECURSIVE seq (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n < 5999)
SELECT showtime.id, CONCAT('R', LPAD(FLOOR(seq.n / 50) + 1, 3, '0')), MOD(seq.n, 50) + 1
FROM seq CROSS JOIN showtime;

ANALYZE TABLE member, performance, showtime, seat, hold, hold_seat, reservation;
