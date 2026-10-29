package com.ticketflow.domain.booking.entity;

import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.performance.entity.Performance;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

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
        assertThatThrownBy(() -> Hold.create(MEMBER, SHOWTIME, seats(1), BOOKING_OPEN_AT.minusSeconds(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKING_NOT_OPEN);
    }

    @Test
    void 예매_오픈_시각_정각에는_선점을_만들_수_있다() {
        Hold hold = Hold.create(MEMBER, SHOWTIME, seats(1), BOOKING_OPEN_AT);

        assertThat(hold.getExpiresAt()).isEqualTo(BOOKING_OPEN_AT.plusMinutes(5));
    }

    @Test
    void 좌석_0석으로는_선점을_만들_수_없다() {
        assertThatThrownBy(() -> Hold.create(MEMBER, SHOWTIME, seats(0), BOOKING_OPEN_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SEAT_COUNT);
    }

    @Test
    void 좌석_4석으로는_선점을_만들_수_있다() {
        Hold hold = Hold.create(MEMBER, SHOWTIME, seats(4), BOOKING_OPEN_AT);

        assertThat(hold.getStatus()).isEqualTo(HoldStatus.HELD);
    }

    @Test
    void 좌석_5석으로는_선점을_만들_수_없다() {
        assertThatThrownBy(() -> Hold.create(MEMBER, SHOWTIME, seats(5), BOOKING_OPEN_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SEAT_COUNT);
    }

    @Test
    void 선점_만료_시각_1초_전에는_선점이_좌석을_차지한다() {
        Hold hold = Hold.create(MEMBER, SHOWTIME, seats(1), BOOKING_OPEN_AT);

        assertThat(hold.isOccupying(BOOKING_OPEN_AT.plusMinutes(5).minusSeconds(1))).isTrue();
    }

    @Test
    void 선점_만료_시각_정각에는_선점이_좌석을_차지하지_않는다() {
        Hold hold = Hold.create(MEMBER, SHOWTIME, seats(1), BOOKING_OPEN_AT);

        assertThat(hold.isOccupying(BOOKING_OPEN_AT.plusMinutes(5))).isFalse();
    }

    private static List<Seat> seats(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(seatNo -> new Seat(SHOWTIME, "A", seatNo))
                .toList();
    }
}
