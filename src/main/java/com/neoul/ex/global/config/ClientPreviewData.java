package com.neoul.ex.global.config;

import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.entity.BeachZone;
import com.neoul.ex.domain.beach.entity.enums.RiskLevel;
import com.neoul.ex.domain.beach.entity.value.BeachEnvironment;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.domain.beach.repository.BeachZoneRepository;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.entity.enums.ChargingStatus;
import com.neoul.ex.domain.ship.entity.value.ShipLocation;
import com.neoul.ex.domain.ship.entity.value.ShipStatus;
import com.neoul.ex.domain.ship.repository.ShipRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("client-preview")
@RequiredArgsConstructor
public class ClientPreviewData implements ApplicationRunner {
    private final BeachRepository beachRepository;
    private final BeachZoneRepository beachZoneRepository;
    private final ShipRepository shipRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (beachRepository.count() != 0) return;
        Instant now = clock.instant();
        Beach beach = Beach.create("예시 해수욕장");
        beach.updateEnvironment(new BeachEnvironment(24.0, 32.4, 7.8, 2.1, 1.6, now));
        beachRepository.save(beach);
        beachRepository.save(Beach.create("관측 전 해수욕장"));
        beachZoneRepository.saveAll(List.of(
                BeachZone.create(beach, "A", "부표 인근", RiskLevel.ANALYZING, null),
                BeachZone.create(beach, "B", "백사장 앞", RiskLevel.SAFE, now),
                BeachZone.create(beach, "C", "외곽 구역", RiskLevel.DANGER, now)));

        Ship online = Ship.create("SG-01", "예시 보트 1호기", beach);
        online.updateStatus(new ShipStatus(88.0, 340.0, 12.4, -65, 14.2, ChargingStatus.CHARGING, now, now));
        online.updateLocation(new ShipLocation(35.1587, 129.1604, now));

        Instant old = now.minusSeconds(600);
        Ship offline = Ship.create("SG-02", "예시 보트 2호기", beach);
        offline.updateStatus(new ShipStatus(15.0, 0.0, 0.0, -100, 11.5, ChargingStatus.DISCHARGING, old, old));
        offline.updateLocation(new ShipLocation(35.1590, 129.1610, old));

        Ship unknown = Ship.create("SG-03", "수신 전 보트", beach);
        shipRepository.saveAll(List.of(online, offline, unknown));
    }
}
