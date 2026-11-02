package com.ticketflow.domain.performance.dto;

import com.ticketflow.domain.performance.entity.Performance;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PerformanceResponse {

    public record Item(
            Long id,
            String title
    ) {

        public static Item from(Performance performance) {
            return new Item(
                    performance.getId(),
                    performance.getTitle()
            );
        }
    }
}
