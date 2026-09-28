package com.neoul.ex.domain.alert.entity;

import com.neoul.ex.domain.ship.entity.Ship;

import jakarta.persistence.*;
import java.net.URI;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "person_logs", indexes = @Index(name = "idx_person_log_boat_id", columnList = "boat_id,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "boat_id", nullable = false)
    private Ship ship;

    @Column(nullable = false, length = 2048)
    private String imageUrl;

    @Column(nullable = false)
    private Instant detectedAt;

    @Column(nullable = false, columnDefinition = "decimal(10,7)")
    private double latitude;

    @Column(nullable = false, columnDefinition = "decimal(10,7)")
    private double longitude;

    public static PersonLog create(Ship ship, String imageUrl, Instant detectedAt,
                                         double latitude, double longitude) {
        if (ship == null || detectedAt == null || imageUrl == null || imageUrl.isBlank() || imageUrl.length() > 2048) {
            throw new IllegalArgumentException("Ship, image URL and log time are required");
        }
        URI uri = URI.create(imageUrl);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || !Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid image URL or GPS coordinates");
        }
        PersonLog log = new PersonLog();
        log.ship = ship;
        log.imageUrl = imageUrl;
        log.detectedAt = detectedAt;
        log.latitude = latitude;
        log.longitude = longitude;
        return log;
    }
}
