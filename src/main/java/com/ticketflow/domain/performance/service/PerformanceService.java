package com.ticketflow.domain.performance.service;

import com.ticketflow.domain.performance.dto.PerformanceResponse;
import com.ticketflow.domain.performance.entity.Performance;
import com.ticketflow.domain.performance.repository.PerformanceRepository;
import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PerformanceService {
    private final PerformanceRepository performanceRepository;

    @Transactional(readOnly = true)
    public List<PerformanceResponse.Item> getPerformances() {
        return toResponses(findAllPerformances());
    }

    public void validatePerformanceExists(Long performanceId) {
        if (!performanceRepository.existsById(performanceId)) {
            throw new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND);
        }
    }

    private List<Performance> findAllPerformances() {
        return performanceRepository.findAll();
    }

    private List<PerformanceResponse.Item> toResponses(List<Performance> performances) {
        return performances.stream()
                .map(PerformanceResponse.Item::from)
                .toList();
    }
}
