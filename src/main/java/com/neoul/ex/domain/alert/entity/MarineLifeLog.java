package com.neoul.ex.domain.alert.entity;

import com.neoul.ex.domain.ship.entity.Ship;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_marine_ship_event", columnNames = {"ship_id", "event_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarineLifeLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Ship ship;

    // Nullable only for logs recorded before device event IDs were introduced.
    @Column(length = 36, updatable = false)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String marineType;

    @Column(nullable = false)
    private int count;

    @Column(nullable = false)
    private Instant detectedAt;

    public static MarineLifeLog create(Ship ship, String eventId, String marineType, int count, Instant detectedAt) {
        if (eventId == null || eventId.length() != 36 || ship == null || detectedAt == null || marineType == null || marineType.isBlank()
                || marineType.strip().length() > 100 || count <= 0) {
            throw new IllegalArgumentException("Ship, species up to 100 characters, positive count and log time are required");
        }
        MarineLifeLog log = new MarineLifeLog();
        log.eventId = UUID.fromString(eventId).toString();
        log.ship = ship;
        log.marineType = marineType.strip();
        log.count = count;
        log.detectedAt = detectedAt;
        return log;
    }
}
