package com.neoul.ex.repository;

import com.neoul.ex.entity.Beach;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeachRepository extends JpaRepository<Beach, Long> {

    List<Beach> findAllByOrderByNameAsc();

    List<Beach> findByNameContainingOrderByNameAsc(String keyword);
}
