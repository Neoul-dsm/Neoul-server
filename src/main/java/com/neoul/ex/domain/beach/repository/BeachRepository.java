package com.neoul.ex.domain.beach.repository;

import com.neoul.ex.domain.beach.entity.Beach;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeachRepository extends JpaRepository<Beach, Long> {

    List<Beach> findAllByOrderByNameAsc();

    List<Beach> findByNameContainingOrderByNameAsc(String keyword);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Beach b where b.id = :id")
    Optional<Beach> findByIdForUpdate(@Param("id") Long id);
}
