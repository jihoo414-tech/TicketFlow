package com.ticketflow.domain.performance.service;

import com.ticketflow.domain.performance.dto.PerformanceResponse;
import com.ticketflow.domain.performance.entity.Performance;
import com.ticketflow.domain.performance.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PerformanceService {
    private final PerformanceRepository performanceRepository;

    @Transactional(readOnly = true)
    public List<PerformanceResponse> getPerformances() {
        return toResponses(findAllPerformances());
    }

    private List<Performance> findAllPerformances() {
        return performanceRepository.findAll();
    }

    private List<PerformanceResponse> toResponses(List<Performance> performances) {
        return performances.stream()
                .map(PerformanceResponse::from)
                .toList();
    }
}
