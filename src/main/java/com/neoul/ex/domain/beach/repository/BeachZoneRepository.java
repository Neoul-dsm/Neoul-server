package com.neoul.ex.domain.beach.repository;

import com.neoul.ex.domain.beach.entity.BeachZone;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeachZoneRepository extends JpaRepository<BeachZone, Long> {
    List<BeachZone> findByBeachIdOrderByCodeAsc(Long beachId);
}
