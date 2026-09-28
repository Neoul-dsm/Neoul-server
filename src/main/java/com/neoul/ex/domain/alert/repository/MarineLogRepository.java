package com.neoul.ex.domain.alert.repository;

import com.neoul.ex.domain.alert.entity.MarineLog;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarineLogRepository extends JpaRepository<MarineLog, Long> {
    @EntityGraph(attributePaths = {"ship", "ship.beach"})
    List<MarineLog> findByShipBeachIdAndIdGreaterThanOrderByIdAsc(Long beachId, Long id, Pageable pageable);

    boolean existsByIdAndShipBeachId(Long id, Long beachId);
}
