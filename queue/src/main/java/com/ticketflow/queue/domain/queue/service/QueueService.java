package com.ticketflow.queue.domain.queue.service;

import com.ticketflow.queue.domain.queue.client.ShowtimeClient;
import com.ticketflow.queue.domain.queue.dto.QueueResponse;
import com.ticketflow.queue.global.exception.BusinessException;
import com.ticketflow.queue.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final WaitingQueue waitingQueue;
    private final ShowtimeClient showtimeClient;
    private final EntryPassIssuer entryPassIssuer;
    private final InternalSecret internalSecret;
    private final Clock clock;

    public QueueResponse.Status join(Long memberId, Long showtimeId) {
        validateBookingOpen(findBookingOpenAt(showtimeId));
        return toResponse(waitingQueue.join(memberId, showtimeId));
    }

    public QueueResponse.Status status(Long memberId, Long showtimeId) {
        return toResponse(findStatusFor(memberId, showtimeId));
    }

    public void leave(Long memberId, Long showtimeId) {
        findStatusFor(memberId, showtimeId);
        waitingQueue.leave(memberId);
    }

    public void release(String internalSecret, String passId) {
        this.internalSecret.validate(internalSecret);
        waitingQueue.release(passId);
    }

    private QueueStatus findStatusFor(Long memberId, Long showtimeId) {
        QueueStatus status = waitingQueue.status(memberId);
        if (!status.isFor(showtimeId)) {
            throw new BusinessException(ErrorCode.NOT_IN_QUEUE);
        }
        return status;
    }

    private LocalDateTime findBookingOpenAt(Long showtimeId) {
        return showtimeClient.findBookingOpenAt(showtimeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND));
    }

    private void validateBookingOpen(LocalDateTime bookingOpenAt) {
        if (LocalDateTime.now(clock).isBefore(bookingOpenAt)) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_OPEN);
        }
    }

    private QueueResponse.Status toResponse(QueueStatus status) {
        return switch (status) {
            case QueueStatus.Waiting waiting -> QueueResponse.Waiting.from(waiting);
            case QueueStatus.Admitted admitted -> toAdmittedResponse(admitted.pass());
            case QueueStatus.NotInQueue notInQueue -> throw new BusinessException(ErrorCode.NOT_IN_QUEUE);
        };
    }

    private QueueResponse.Admitted toAdmittedResponse(EntryPass pass) {
        return QueueResponse.Admitted.of(entryPassIssuer.issue(pass), pass.expiresAt());
    }
}
