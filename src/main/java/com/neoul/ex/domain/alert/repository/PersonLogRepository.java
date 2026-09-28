package com.neoul.ex.domain.alert.repository;

import com.neoul.ex.domain.alert.entity.PersonLog;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonLogRepository extends JpaRepository<PersonLog, Long> {
    @EntityGraph(attributePaths = {"ship", "ship.beach"})
    List<PersonLog> findByShipBeachIdAndIdGreaterThanOrderByIdAsc(Long beachId, Long id, Pageable pageable);

    boolean existsByIdAndShipBeachId(Long id, Long beachId);
}
