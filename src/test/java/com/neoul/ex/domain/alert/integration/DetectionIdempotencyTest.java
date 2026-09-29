package com.neoul.ex.domain.alert.integration;

import com.neoul.ex.domain.alert.service.DrowningAlertService;
import com.neoul.ex.domain.alert.service.MarineLifeAlertService;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.repository.ShipRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class DetectionIdempotencyTest {
    private static final Instant DETECTED_AT = Instant.parse("2026-09-29T01:00:00Z");
    @Autowired DrowningAlertService humans;
    @Autowired MarineLifeAlertService marine;
    @Autowired BeachRepository beaches;
    @Autowired ShipRepository ships;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Transactional
    void retriesReuseTheOriginalRecordButAnotherShipCanUseTheSameEventId(boolean human) {
        var beach = beaches.saveAndFlush(Beach.create("중복 검증 해변"));
        var ship = newShip(beach);
        var other = newShip(beach);
        String eventId = "aabbccdd-1234-4567-89ab-aabbccddeeff";
        long first = record(human, ship.getId(), eventId);
        assertThat(record(human, ship.getId(), eventId.toUpperCase(Locale.ROOT))).isEqualTo(first);
        assertThat(record(human, other.getId(), eventId)).isNotEqualTo(first);
        assertThat(count(human, ship.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT event_id FROM " + table(human) + " WHERE id = ?",
                String.class, first)).isEqualTo(eventId);
        assertThat(record(human, ship.getId(), UUID.randomUUID().toString())).isNotEqualTo(first);
        assertThat(count(human, ship.getId())).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Transactional
    void databaseAlsoRejectsDuplicateDeviceEvents(boolean human) {
        var beach = beaches.saveAndFlush(Beach.create("고유 제약 검증 해변"));
        var ship = newShip(beach);
        long id = record(human, ship.getId(), UUID.randomUUID().toString());
        String columns = human ? "ship_id,event_id,image_url,detected_at,latitude,longitude"
                : "ship_id,event_id,marine_type,count,detected_at";
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + table(human) + " (" + columns + ") SELECT "
                + columns + " FROM " + table(human) + " WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "not-a-uuid", "1-1-1-1-1", "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz"})
    @Transactional
    void requiresADeviceSuppliedUuid(String eventId) {
        var beach = beaches.saveAndFlush(Beach.create("식별자 검증 해변"));
        var ship = newShip(beach);
        for (boolean human : new boolean[]{false, true}) {
            assertThatThrownBy(() -> record(human, ship.getId(), eventId))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
            assertThat(count(human, ship.getId())).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void simultaneousRetriesReturnOneCommittedRecord(boolean human) throws Exception {
        var beach = beaches.saveAndFlush(Beach.create("동시 수신 검증 해변"));
        var ship = newShip(beach);
        String eventId = UUID.randomUUID().toString();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Callable<Long> request = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return record(human, ship.getId(), eventId);
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
            assertThat(count(human, ship.getId())).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM " + table(human) + " WHERE ship_id = ?", ship.getId());
            ships.deleteById(ship.getId());
            beaches.deleteById(beach.getId());
        }
    }

    private long record(boolean human, Long shipId, String eventId) {
        return human
                ? humans.record(shipId, eventId, "https://images.example.com/person.jpg", DETECTED_AT, 35.1, 129.1).detectionId()
                : marine.record(shipId, eventId, "해파리", 3, DETECTED_AT).detectionId();
    }

    private Ship newShip(Beach beach) {
        return ships.saveAndFlush(Ship.create(UUID.randomUUID().toString().substring(0, 30), "검증 배", beach));
    }

    private long count(boolean human, Long shipId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table(human) + " WHERE ship_id = ?", Long.class, shipId);
    }

    private String table(boolean human) {
        return human ? "human_log" : "marine_life_log";
    }
}
