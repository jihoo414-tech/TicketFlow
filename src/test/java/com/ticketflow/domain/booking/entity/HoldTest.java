package com.ticketflow.domain.booking.entity;

import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.performance.entity.Performance;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldTest {

    private static final LocalDateTime BOOKING_OPEN_AT = LocalDateTime.of(2026, 10, 1, 10, 0, 0);
    private static final Member MEMBER = new Member("회원1");
    private static final Showtime SHOWTIME = Showtime.create(
            new Performance("레미제라블"),
            LocalDateTime.of(2026, 11, 1, 19, 0, 0),
            BOOKING_OPEN_AT,
            50000
    );

    @Test
    void 예매_오픈_시각_1초_전에는_선점을_만들_수_없다() {
        assertThatThrownBy(() -> Hold.create(MEMBER, SHOWTIME, BOOKING_OPEN_AT.minusSeconds(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKING_NOT_OPEN);
    }

    @Test
    void 예매_오픈_시각_정각에는_선점을_만들_수_있다() {
        Hold hold = Hold.create(MEMBER, SHOWTIME, BOOKING_OPEN_AT);

        assertThat(hold.getExpiresAt()).isEqualTo(BOOKING_OPEN_AT.plusMinutes(5));
    }
}
