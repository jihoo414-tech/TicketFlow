package com.ticketflow.domain.performance.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ticketflow.domain.performance.dto.ZoneResponse;
import com.ticketflow.domain.performance.entity.Zone;
import com.ticketflow.domain.performance.repository.ZoneRepository;
import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private static final int MAXIMUM_ZONES = 300;

    private final ShowtimeService showtimeService;
    private final ZoneRepository zoneRepository;
    private final Cache<Long, Long> showtimeIdsByZone = Caffeine.newBuilder()
            .maximumSize(MAXIMUM_ZONES)
            .build();

    @Transactional(readOnly = true)
    public List<ZoneResponse.Item> getZones(Long showtimeId) {
        showtimeService.validateShowtimeExists(showtimeId);
        return toResponses(findZonesOf(showtimeId));
    }

    @Transactional(readOnly = true)
    public Zone findZone(Long zoneId) {
        return zoneRepository.findWithShowtimeById(zoneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ZONE_NOT_FOUND));
    }

    public Long findShowtimeIdOf(Long zoneId) {
        return showtimeIdsByZone.get(zoneId, id -> findZone(id).getShowtime().getId());
    }

    private List<Zone> findZonesOf(Long showtimeId) {
        return zoneRepository.findAllByShowtimeIdOrderByDisplayOrder(showtimeId);
    }

    private List<ZoneResponse.Item> toResponses(List<Zone> zones) {
        return zones.stream()
                .map(ZoneResponse.Item::from)
                .toList();
    }
}
