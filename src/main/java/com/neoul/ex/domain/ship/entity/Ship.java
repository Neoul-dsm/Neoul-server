package com.neoul.ex.domain.ship.entity;

import com.neoul.ex.domain.beach.entity.Beach;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Beach beach;

    private Instant lastCommunicationAt;

    @Column(length = 64)
    private String apiKeyHash;

    @Column(columnDefinition = "decimal(10,7)")
    private Double latitude;

    @Column(columnDefinition = "decimal(10,7)")
    private Double longitude;

    private Instant locationReceivedAt;

    public static Ship create(String code, String name, Beach beach) {
        Ship ship = new Ship();
        ship.code = code;
        ship.name = name;
        ship.beach = beach;
        return ship;
    }

    public void updateLastCommunicationAt(Instant receivedAt) {
        this.lastCommunicationAt = receivedAt;
    }

    public void changeApiKeyHash(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash;
    }

    public void updateLocation(Double latitude, Double longitude, Instant receivedAt) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationReceivedAt = receivedAt;
    }
}
