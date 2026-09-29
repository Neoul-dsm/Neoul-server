package com.neoul.ex.domain.ship.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "solar_generation_logs", uniqueConstraints =
        @UniqueConstraint(name = "uk_solar_ship_period", columnNames = {"ship_id", "period_started_at"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolarGenerationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Ship ship;

    // Energy generated during this complete one-hour interval, not instantaneous power.
    @Column(nullable = false)
    private Instant periodStartedAt;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal energyWh;

    public static SolarGenerationLog create(Ship ship, Instant periodStartedAt,
                                             Instant receivedAt, BigDecimal energyWh) {
        if (ship == null || periodStartedAt == null || receivedAt == null || energyWh == null
                || !periodStartedAt.equals(periodStartedAt.truncatedTo(ChronoUnit.HOURS))
                || receivedAt.isBefore(periodStartedAt.plus(1, ChronoUnit.HOURS))
                || energyWh.signum() < 0) {
            throw new IllegalArgumentException("A completed hourly interval and nonnegative energy are required");
        }
        BigDecimal normalized;
        try {
            normalized = energyWh.setScale(3, java.math.RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Energy supports at most three decimal places", exception);
        }
        if (normalized.precision() > 14) throw new IllegalArgumentException("Energy exceeds storage precision");
        SolarGenerationLog log = new SolarGenerationLog();
        log.ship = ship;
        log.periodStartedAt = periodStartedAt;
        log.receivedAt = receivedAt;
        log.energyWh = normalized;
        return log;
    }
}
