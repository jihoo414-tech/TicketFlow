DROP TABLE IF EXISTS seat;
DROP TABLE IF EXISTS showtime;
DROP TABLE IF EXISTS performance;
DROP TABLE IF EXISTS member;

CREATE TABLE member (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE performance (
    id    BIGINT       NOT NULL AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE showtime (
    id              BIGINT   NOT NULL AUTO_INCREMENT,
    performance_id  BIGINT   NOT NULL,
    starts_at       DATETIME NOT NULL,
    booking_open_at DATETIME NOT NULL,
    price           INT      NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (performance_id) REFERENCES performance (id)
);

CREATE TABLE seat (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    showtime_id BIGINT      NOT NULL,
    seat_row    VARCHAR(10) NOT NULL,
    seat_no     INT         NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (showtime_id) REFERENCES showtime (id),
    UNIQUE KEY uk_seat_showtime_row_no (showtime_id, seat_row, seat_no)
);
