package com.neoul.ex.domain.alert.entity;

import com.neoul.ex.domain.ship.entity.Ship;

import jakarta.persistence.*;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_human_ship_event", columnNames = {"ship_id", "event_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HumanLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Ship ship;

    // Nullable only for logs recorded before device event IDs were introduced.
    @Column(length = 36, updatable = false)
    private String eventId;

    @Column(nullable = false, length = 2048)
    private String imageUrl;

    @Column(nullable = false)
    private Instant detectedAt;

    // null means no AI verdict was supplied and must not be interpreted as false.
    private Boolean aiResult;

    @Column(nullable = false, columnDefinition = "decimal(10,7)")
    private double latitude;

    @Column(nullable = false, columnDefinition = "decimal(10,7)")
    private double longitude;

    public static HumanLog create(Ship ship, String eventId, String imageUrl, Instant detectedAt,
                                         double latitude, double longitude) {
        return create(ship, eventId, imageUrl, detectedAt, latitude, longitude, null);
    }

    public static HumanLog create(Ship ship, String eventId, String imageUrl, Instant detectedAt,
                                   double latitude, double longitude, Boolean aiResult) {
        if (eventId == null || eventId.length() != 36 || ship == null || detectedAt == null
                || imageUrl == null || imageUrl.isBlank() || imageUrl.length() > 2048) {
            throw new IllegalArgumentException("Ship, image URL and log time are required");
        }
        URI uri = URI.create(imageUrl);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || !Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid image URL or GPS coordinates");
        }
        HumanLog log = new HumanLog();
        log.eventId = UUID.fromString(eventId).toString();
        log.ship = ship;
        log.imageUrl = imageUrl;
        log.detectedAt = detectedAt;
        log.latitude = latitude;
        log.longitude = longitude;
        log.aiResult = aiResult;
        return log;
    }
}
