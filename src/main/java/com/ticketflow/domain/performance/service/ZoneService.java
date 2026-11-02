package com.ticketflow.domain.performance.service;

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

    private final ShowtimeService showtimeService;
    private final ZoneRepository zoneRepository;

    @Transactional(readOnly = true)
    public List<ZoneResponse> getZones(Long showtimeId) {
        showtimeService.findShowtime(showtimeId);
        return toResponses(findZonesOf(showtimeId));
    }

    @Transactional(readOnly = true)
    public Zone findZone(Long zoneId) {
        return zoneRepository.findWithShowtimeById(zoneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ZONE_NOT_FOUND));
    }

    private List<Zone> findZonesOf(Long showtimeId) {
        return zoneRepository.findAllByShowtimeIdOrderByDisplayOrder(showtimeId);
    }

    private List<ZoneResponse> toResponses(List<Zone> zones) {
        return zones.stream()
                .map(ZoneResponse::from)
                .toList();
    }
}
