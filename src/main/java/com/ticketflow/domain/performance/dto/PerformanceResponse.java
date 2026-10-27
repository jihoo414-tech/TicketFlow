package com.ticketflow.domain.performance.dto;

import com.ticketflow.domain.performance.entity.Performance;

public record PerformanceResponse(
        Long id,
        String title
) {

    public static PerformanceResponse from(Performance performance) {
        return new PerformanceResponse(
                performance.getId(),
                performance.getTitle()
        );
    }
}
