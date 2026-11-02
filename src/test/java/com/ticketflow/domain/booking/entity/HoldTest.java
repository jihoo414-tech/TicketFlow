package com.ticketflow.domain.booking.entity;

import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.fixture.TestMembers;
import com.ticketflow.domain.performance.entity.Performance;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.domain.performance.entity.Zone;
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
    private static final Member MEMBER = TestMembers.member("회원1");
    private static final Showtime SHOWTIME = Showtime.create(
            new Performance("레미제라블"),
            LocalDateTime.of(2026, 11, 1, 19, 0, 0),
            BOOKING_OPEN_AT,
            50000
    );
    private static final Zone ZONE = new Zone(SHOWTIME, "A구역", 1);

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

    @Test
    void 선점_만료_시각_1초_전에는_예매할_수_있다() {
        Hold hold = Hold.create(MEMBER, SHOWTIME, seats(1), BOOKING_OPEN_AT);

        hold.reserve(BOOKING_OPEN_AT.plusMinutes(5).minusSeconds(1));

        assertThat(hold.getStatus()).isEqualTo(HoldStatus.RESERVED);
    }

    @Test
    void 선점_만료_시각_정각에는_예매할_수_없다() {
        Hold hold = Hold.create(MEMBER, SHOWTIME, seats(1), BOOKING_OPEN_AT);

        assertThatThrownBy(() -> hold.reserve(BOOKING_OPEN_AT.plusMinutes(5)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HOLD_EXPIRED);
    }

    @Test
    void 예매된_선점은_다시_예매할_수_없다() {
        Hold hold = Hold.create(MEMBER, SHOWTIME, seats(1), BOOKING_OPEN_AT);
        hold.reserve(BOOKING_OPEN_AT.plusMinutes(1));

        assertThatThrownBy(() -> hold.reserve(BOOKING_OPEN_AT.plusMinutes(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HOLD_ALREADY_RESERVED);
    }

    @Test
    void 취소된_선점은_예매할_수_없다() {
        Hold hold = Hold.create(MEMBER, SHOWTIME, seats(1), BOOKING_OPEN_AT);
        hold.cancel(BOOKING_OPEN_AT.plusMinutes(1));

        assertThatThrownBy(() -> hold.reserve(BOOKING_OPEN_AT.plusMinutes(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HOLD_ALREADY_CANCELLED);
    }

    @Test
    void 선점_만료_시각_전에_선점을_취소하면_취소_상태가_된다() {
        Hold hold = Hold.create(MEMBER, SHOWTIME, seats(1), BOOKING_OPEN_AT);

        hold.cancel(BOOKING_OPEN_AT.plusMinutes(5).minusSeconds(1));

        assertThat(hold.getStatus()).isEqualTo(HoldStatus.CANCELLED);
    }

    @Test
    void 예매된_선점은_취소할_수_없다() {
        Hold hold = Hold.create(MEMBER, SHOWTIME, seats(1), BOOKING_OPEN_AT);
        hold.reserve(BOOKING_OPEN_AT.plusMinutes(1));

        assertThatThrownBy(() -> hold.cancel(BOOKING_OPEN_AT.plusMinutes(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HOLD_ALREADY_RESERVED);
    }

    @Test
    void 선점_만료_시각_정각에는_선점을_취소할_수_없다() {
        Hold hold = Hold.create(MEMBER, SHOWTIME, seats(1), BOOKING_OPEN_AT);

        assertThatThrownBy(() -> hold.cancel(BOOKING_OPEN_AT.plusMinutes(5)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HOLD_EXPIRED);
    }

    @Test
    void 취소된_선점은_다시_취소할_수_없다() {
        Hold hold = Hold.create(MEMBER, SHOWTIME, seats(1), BOOKING_OPEN_AT);
        hold.cancel(BOOKING_OPEN_AT.plusMinutes(1));

        assertThatThrownBy(() -> hold.cancel(BOOKING_OPEN_AT.plusMinutes(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HOLD_ALREADY_CANCELLED);
    }

    private static List<Seat> seats(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(seatNo -> new Seat(ZONE, "1", seatNo))
                .toList();
    }
}
