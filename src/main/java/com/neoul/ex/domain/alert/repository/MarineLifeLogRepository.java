package com.neoul.ex.domain.alert.repository;

import com.neoul.ex.domain.alert.entity.MarineLifeLog;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarineLifeLogRepository extends JpaRepository<MarineLifeLog, Long> {
    // A locking read sees committed retries even under MySQL REPEATABLE READ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MarineLifeLog> findByShipIdAndEventId(Long shipId, String eventId);

    @EntityGraph(attributePaths = {"ship", "ship.beach"})
    List<MarineLifeLog> findByShipBeachIdAndIdGreaterThanOrderByIdAsc(Long beachId, Long id, Pageable pageable);

    boolean existsByIdAndShipBeachId(Long id, Long beachId);
}
