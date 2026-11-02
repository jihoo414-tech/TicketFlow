package com.ticketflow.domain.performance.dto;

import com.ticketflow.domain.performance.entity.Zone;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ZoneResponse {

    public record Item(
            Long id,
            String name
    ) {

        public static Item from(Zone zone) {
            return new Item(zone.getId(), zone.getName());
        }
    }
}
