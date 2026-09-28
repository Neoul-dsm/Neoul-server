package com.neoul.ex.domain.alert.entity;

import com.neoul.ex.domain.ship.entity.Ship;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "marine_logs", indexes = @Index(name = "idx_marine_log_boat_id", columnList = "boat_id,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarineLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "boat_id", nullable = false)
    private Ship ship;

    @Column(name = "marine_type", nullable = false, length = 100)
    private String species;

    @Column(nullable = false)
    private int count;

    @Column(nullable = false)
    private Instant detectedAt;

    public static MarineLog create(Ship ship, String species, int count, Instant detectedAt) {
        if (ship == null || detectedAt == null || species == null || species.isBlank()
                || species.strip().length() > 100 || count <= 0) {
            throw new IllegalArgumentException("Ship, species up to 100 characters, positive count and log time are required");
        }
        MarineLog log = new MarineLog();
        log.ship = ship;
        log.species = species.strip();
        log.count = count;
        log.detectedAt = detectedAt;
        return log;
    }
}
