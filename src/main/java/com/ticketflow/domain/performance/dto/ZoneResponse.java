package com.ticketflow.domain.performance.dto;

import com.ticketflow.domain.performance.entity.Zone;

public record ZoneResponse(
        Long id,
        String name
) {

    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(zone.getId(), zone.getName());
    }
}
