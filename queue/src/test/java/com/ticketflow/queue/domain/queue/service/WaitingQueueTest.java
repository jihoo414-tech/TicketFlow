package com.ticketflow.queue.domain.queue.service;

import com.ticketflow.queue.TestClock;
import com.ticketflow.queue.global.exception.BusinessException;
import com.ticketflow.queue.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaitingQueueTest {

    private static final long SHOWTIME_ID = 10L;
    private static final long OTHER_SHOWTIME_ID = 20L;
    private static final int ADMISSION_LIMIT = 4;
    private static final int ADMITS_PER_SECOND = 2;

    private final TestClock clock = new TestClock();
    private final WaitingQueue queue = new WaitingQueue(clock, ADMISSION_LIMIT, ADMITS_PER_SECOND);

    @Test
    void 줄을_서면_도착_순서대로_순번이_매겨진다() {
        queue.join(1L, SHOWTIME_ID);
        queue.join(2L, SHOWTIME_ID);
        queue.join(3L, SHOWTIME_ID);

        assertThat(positionOf(1L)).isZero();
        assertThat(positionOf(2L)).isEqualTo(1);
        assertThat(positionOf(3L)).isEqualTo(2);
    }

    @Test
    void 같은_회원이_다시_줄을_서면_같은_번호표를_받는다() {
        queue.join(1L, SHOWTIME_ID);
        queue.join(2L, SHOWTIME_ID);

        queue.join(1L, SHOWTIME_ID);

        assertThat(positionOf(1L)).isZero();
        assertThat(positionOf(2L)).isEqualTo(1);
    }

    @Test
    void 입장_처리는_초당_입장_수까지만_들여보낸다() {
        queue.join(1L, SHOWTIME_ID);
        queue.join(2L, SHOWTIME_ID);
        queue.join(3L, SHOWTIME_ID);

        queue.admit();

        assertThat(queue.status(1L)).isInstanceOf(QueueStatus.Admitted.class);
        assertThat(queue.status(2L)).isInstanceOf(QueueStatus.Admitted.class);
        assertThat(positionOf(3L)).isZero();
    }

    @Test
    void 활성_회원이_입장_인원만큼_있으면_더_들여보내지_않는다() {
        joinMembers(1L, 2L, 3L, 4L, 5L);

        queue.admit();
        queue.admit();
        queue.admit();

        assertThat(queue.status(4L)).isInstanceOf(QueueStatus.Admitted.class);
        assertThat(positionOf(5L)).isZero();
    }

    @Test
    void 자리_비움을_받으면_다음_입장_처리에서_한_명_더_들어온다() {
        joinMembers(1L, 2L, 3L, 4L, 5L);
        queue.admit();
        queue.admit();

        queue.release(passOf(1L).id());
        queue.admit();

        assertThat(queue.status(5L)).isInstanceOf(QueueStatus.Admitted.class);
    }

    @Test
    void 입장권이_만료되면_그_자리가_빈다() {
        joinMembers(1L, 2L, 3L, 4L, 5L);
        queue.admit();
        queue.admit();

        clock.advance(Duration.ofMinutes(5));
        queue.status(5L);
        queue.admit();

        assertThat(queue.status(5L)).isInstanceOf(QueueStatus.Admitted.class);
    }

    @Test
    void 입장한_회원이_다시_줄을_서면_같은_입장_정보를_받는다() {
        joinMembers(1L, 2L, 3L);
        queue.admit();
        EntryPass pass = passOf(1L);

        QueueStatus rejoined = queue.join(1L, SHOWTIME_ID);
        queue.join(4L, SHOWTIME_ID);

        assertThat(rejoined).isEqualTo(new QueueStatus.Admitted(pass));
        assertThat(positionOf(4L)).isEqualTo(1);
    }

    @Test
    void 입장권을_다_쓴_회원이_다시_줄을_서면_맨_뒤에_선다() {
        joinMembers(1L, 2L, 3L);
        queue.admit();
        queue.release(passOf(1L).id());

        queue.join(1L, SHOWTIME_ID);

        assertThat(positionOf(3L)).isZero();
        assertThat(positionOf(1L)).isEqualTo(1);
    }

    @Test
    void 이분_동안_순번을_확인하지_않은_회원은_입장_차례에_빠진다() {
        joinMembers(1L, 2L, 3L);

        clock.advance(Duration.ofMinutes(2));
        queue.status(2L);
        queue.status(3L);
        queue.admit();

        assertThat(queue.status(2L)).isInstanceOf(QueueStatus.Admitted.class);
        assertThat(queue.status(3L)).isInstanceOf(QueueStatus.Admitted.class);
    }

    @Test
    void 대기_취소한_회원은_입장하지_않는다() {
        joinMembers(1L, 2L, 3L);

        queue.leave(1L);
        queue.admit();

        assertThat(queue.status(1L)).isEqualTo(new QueueStatus.NotInQueue());
        assertThat(queue.status(3L)).isInstanceOf(QueueStatus.Admitted.class);
    }

    @Test
    void 다른_회차를_기다리는_회원은_줄을_설_수_없다() {
        joinMembers(1L, 2L);
        queue.admit();
        queue.join(3L, SHOWTIME_ID);

        assertThatThrownBy(() -> queue.join(3L, OTHER_SHOWTIME_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_IN_QUEUE);
        assertThatThrownBy(() -> queue.join(1L, OTHER_SHOWTIME_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_IN_QUEUE);
    }

    @Test
    void 앞에_남은_인원이_적으면_1초_뒤에_다시_확인하라고_한다() {
        joinMembers(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);

        assertThat(queue.status(7L)).isEqualTo(new QueueStatus.Waiting(6, 1));
        assertThat(queue.status(8L)).isEqualTo(new QueueStatus.Waiting(7, 3));
    }

    @Test
    void 입장권이_만료되면_입장_처리를_기다리지_않고_바로_줄에_없는_것으로_보인다() {
        joinMembers(1L);
        queue.admit();

        clock.advance(Duration.ofMinutes(5));

        assertThat(queue.status(1L)).isEqualTo(new QueueStatus.NotInQueue());
    }

    private void joinMembers(Long... memberIds) {
        for (Long memberId : memberIds) {
            queue.join(memberId, SHOWTIME_ID);
        }
    }

    private EntryPass passOf(Long memberId) {
        return ((QueueStatus.Admitted) queue.status(memberId)).pass();
    }

    private long positionOf(Long memberId) {
        return ((QueueStatus.Waiting) queue.status(memberId)).position();
    }
}
