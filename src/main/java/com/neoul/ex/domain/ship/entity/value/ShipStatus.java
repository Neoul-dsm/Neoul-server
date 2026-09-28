package com.neoul.ex.domain.ship.entity.value;

import com.neoul.ex.domain.ship.entity.enums.ChargingStatus;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipStatus {
    @Column(name = "battery_level", columnDefinition = "decimal(5,2)")
    private Double batteryPercent;
    @Column(name = "solar_power", columnDefinition = "decimal(10,2)")
    private Double solarPowerW;
    private Double speedKnots;
    private Integer signalStrengthDbm;
    private Double batteryVoltageV;

    @Enumerated(EnumType.STRING)
    private ChargingStatus chargingStatus;

    private Instant statusMeasuredAt;
    @Column(name = "last_communication_at")
    private Instant lastReceivedAt;
}
