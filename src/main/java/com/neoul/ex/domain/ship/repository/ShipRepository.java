package com.neoul.ex.domain.ship.repository;

import com.neoul.ex.domain.ship.entity.Ship;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipRepository extends JpaRepository<Ship, Long> {
    @EntityGraph(attributePaths = "beach")
    List<Ship> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = "beach")
    List<Ship> findByBeachIdOrderByCodeAsc(Long beachId);

    @Override
    @EntityGraph(attributePaths = "beach")
    Optional<Ship> findById(Long id);

    long countByBeachId(Long beachId);
}
