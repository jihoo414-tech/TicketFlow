DROP TABLE IF EXISTS reservation;
DROP TABLE IF EXISTS hold_seat;
DROP TABLE IF EXISTS hold;
DROP TABLE IF EXISTS seat;
DROP TABLE IF EXISTS zone;
DROP TABLE IF EXISTS showtime;
DROP TABLE IF EXISTS performance;
DROP TABLE IF EXISTS member;

CREATE TABLE member (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_email (email)
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

CREATE TABLE zone (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    showtime_id   BIGINT      NOT NULL,
    name          VARCHAR(20) NOT NULL,
    display_order INT         NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (showtime_id) REFERENCES showtime (id),
    UNIQUE KEY uk_zone_showtime_name (showtime_id, name)
);

CREATE TABLE seat (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    showtime_id BIGINT      NOT NULL,
    zone_id     BIGINT      NOT NULL,
    seat_row    VARCHAR(10) NOT NULL,
    seat_no     INT         NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (showtime_id) REFERENCES showtime (id),
    FOREIGN KEY (zone_id) REFERENCES zone (id),
    UNIQUE KEY uk_seat_showtime_row_no (showtime_id, seat_row, seat_no)
);

CREATE TABLE hold (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    member_id   BIGINT      NOT NULL,
    showtime_id BIGINT      NOT NULL,
    status      VARCHAR(30) NOT NULL,
    expires_at  DATETIME    NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (member_id) REFERENCES member (id),
    FOREIGN KEY (showtime_id) REFERENCES showtime (id),
    KEY idx_hold_showtime_status_expires (showtime_id, status, expires_at)
);

CREATE TABLE hold_seat (
    id      BIGINT NOT NULL AUTO_INCREMENT,
    hold_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (hold_id) REFERENCES hold (id),
    FOREIGN KEY (seat_id) REFERENCES seat (id)
);

CREATE TABLE reservation (
    id        BIGINT NOT NULL AUTO_INCREMENT,
    hold_id   BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (hold_id) REFERENCES hold (id),
    FOREIGN KEY (member_id) REFERENCES member (id),
    UNIQUE KEY uk_reservation_hold (hold_id)
);
