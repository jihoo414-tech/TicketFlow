package com.ticketflow.domain.performance.repository;

import com.ticketflow.domain.performance.entity.Zone;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    List<Zone> findAllByShowtimeIdOrderByDisplayOrder(Long showtimeId);

    @EntityGraph(attributePaths = "showtime")
    Optional<Zone> findWithShowtimeById(Long zoneId);
}
