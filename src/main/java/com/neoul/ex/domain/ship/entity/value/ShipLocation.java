package com.neoul.ex.domain.ship.entity.value;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Column;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipLocation {
    @Column(columnDefinition = "decimal(10,7)")
    private Double latitude;
    @Column(columnDefinition = "decimal(10,7)")
    private Double longitude;
    private Instant locationReceivedAt;
}
