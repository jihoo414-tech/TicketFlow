package com.ticketflow.domain.performance.service;

import com.ticketflow.domain.performance.dto.ShowtimeResponse;
import com.ticketflow.domain.performance.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowtimeService {
    private final ShowtimeRepository showtimeRepository;

    @Transactional(readOnly = true)
    public List<ShowtimeResponse> getShowtimes(Long performanceId) {
        return showtimeRepository.findByPerformanceIdOrderByStartsAtAsc(performanceId)
                .stream()
                .map(ShowtimeResponse::from)
                .toList();
    }
}
