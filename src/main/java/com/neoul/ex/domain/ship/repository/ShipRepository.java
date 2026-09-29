package com.neoul.ex.domain.ship.repository;

import com.neoul.ex.domain.ship.entity.Ship;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipRepository extends JpaRepository<Ship, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Ship s where s.id = :id")
    Optional<Ship> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "beach")
    List<Ship> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = "beach")
    List<Ship> findByBeachIdOrderByCodeAsc(Long beachId);

    @EntityGraph(attributePaths = "beach")
    List<Ship> findByBeachIdInOrderByCodeAsc(Set<Long> beachIds);

    @Override
    @EntityGraph(attributePaths = "beach")
    Optional<Ship> findById(Long id);

    long countByBeachId(Long beachId);
}
