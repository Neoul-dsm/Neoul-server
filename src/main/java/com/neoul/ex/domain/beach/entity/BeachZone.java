package com.neoul.ex.domain.beach.entity;

import com.neoul.ex.domain.beach.entity.enums.RiskLevel;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "beach_zone", uniqueConstraints = @UniqueConstraint(columnNames = {"beach_id", "code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BeachZone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beach_id", nullable = false)
    private Beach beach;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel riskLevel;

    private Instant measuredAt;

    public static BeachZone create(Beach beach, String code, String name, RiskLevel riskLevel, Instant measuredAt) {
        BeachZone zone = new BeachZone();
        zone.beach = beach;
        zone.code = code;
        zone.name = name;
        zone.riskLevel = riskLevel;
        zone.measuredAt = measuredAt;
        return zone;
    }
}
