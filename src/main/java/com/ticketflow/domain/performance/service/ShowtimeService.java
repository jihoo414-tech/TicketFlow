package com.ticketflow.domain.performance.service;

import com.ticketflow.domain.performance.dto.ShowtimeResponse;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.domain.performance.repository.PerformanceRepository;
import com.ticketflow.domain.performance.repository.ShowtimeRepository;
import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowtimeService {
    private final PerformanceRepository performanceRepository;
    private final ShowtimeRepository showtimeRepository;

    @Transactional(readOnly = true)
    public List<ShowtimeResponse> getShowtimes(Long performanceId) {
        validatePerformanceExists(performanceId);
        return toResponses(findShowtimesOf(performanceId));
    }

    private void validatePerformanceExists(Long performanceId) {
        if (!performanceRepository.existsById(performanceId)) {
            throw new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND);
        }
    }

    private List<Showtime> findShowtimesOf(Long performanceId) {
        return showtimeRepository.findByPerformanceIdOrderByStartsAtAsc(performanceId);
    }

    private List<ShowtimeResponse> toResponses(List<Showtime> showtimes) {
        return showtimes.stream()
                .map(ShowtimeResponse::from)
                .toList();
    }
}
