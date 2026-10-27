package com.ticketflow.domain.performance.entity;

import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShowtimeTest {

    private static final Performance PERFORMANCE = new Performance("레미제라블");
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 1, 19, 0, 0);

    @Test
    void 예매_오픈_시각이_회차_시작_시각보다_1초_이르면_회차가_만들어진다() {
        Showtime showtime = Showtime.create(PERFORMANCE, STARTS_AT, STARTS_AT.minusSeconds(1), 50000);

        assertThat(showtime.getBookingOpenAt()).isEqualTo(STARTS_AT.minusSeconds(1));
    }

    @Test
    void 예매_오픈_시각이_회차_시작_시각과_같으면_회차를_만들_수_없다() {
        assertThatThrownBy(() -> Showtime.create(PERFORMANCE, STARTS_AT, STARTS_AT, 50000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKING_OPEN_NOT_BEFORE_START);
    }

    @Test
    void 예매_오픈_시각이_회차_시작_시각보다_늦으면_회차를_만들_수_없다() {
        assertThatThrownBy(() -> Showtime.create(PERFORMANCE, STARTS_AT, STARTS_AT.plusSeconds(1), 50000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKING_OPEN_NOT_BEFORE_START);
    }
}
