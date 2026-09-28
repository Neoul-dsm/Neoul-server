package com.neoul.ex.domain.beach.entity.value;

import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BeachEnvironment {
    private Double waterTemperatureC;
    private Double salinityPsu;
    private Double dissolvedOxygenMgL;
    private Double waveHeightM;
    private Double waveSpeedMs;
    private Instant environmentMeasuredAt;
}
