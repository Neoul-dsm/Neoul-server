package com.neoul.ex.domain.ship.repository;

import com.neoul.ex.domain.ship.entity.SolarGenerationLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolarGenerationLogRepository extends JpaRepository<SolarGenerationLog, Long> {
    List<SolarGenerationLog> findByShipIdAndPeriodStartedAtGreaterThanEqualAndPeriodStartedAtLessThanOrderByPeriodStartedAtAsc(
            Long shipId, Instant from, Instant to);
}
