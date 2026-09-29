package com.neoul.ex.global.config;

import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.domain.ship.entity.Ship;
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
    private final ShipRepository shipRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (beachRepository.count() != 0) return;
        Instant now = clock.instant();
        Beach beach = Beach.create("예시 해수욕장");
        beach.updateLocation(35.1587, 129.1604);
        beachRepository.save(beach);
        beachRepository.save(Beach.create("관측 전 해수욕장"));

        Ship online = Ship.create("SG-01", "예시 보트 1호기", beach);
        online.updateLastCommunicationAt(now);
        online.updateLocation(35.1587, 129.1604, now);

        Instant old = now.minusSeconds(600);
        Ship offline = Ship.create("SG-02", "예시 보트 2호기", beach);
        offline.updateLastCommunicationAt(old);
        offline.updateLocation(35.1590, 129.1610, old);

        Ship unknown = Ship.create("SG-03", "수신 전 보트", beach);
        shipRepository.saveAll(List.of(online, offline, unknown));
    }
}
