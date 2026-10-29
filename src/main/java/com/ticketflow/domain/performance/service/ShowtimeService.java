package com.ticketflow.domain.performance.service;

import com.ticketflow.domain.performance.dto.ShowtimeResponse;
import com.ticketflow.domain.performance.entity.Showtime;
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
    private final PerformanceService performanceService;
    private final ShowtimeRepository showtimeRepository;

    @Transactional(readOnly = true)
    public List<ShowtimeResponse> getShowtimes(Long performanceId) {
        performanceService.validatePerformanceExists(performanceId);
        return toResponses(findShowtimesOf(performanceId));
    }

    @Transactional(readOnly = true)
    public Showtime findShowtime(Long showtimeId) {
        return showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND));
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
