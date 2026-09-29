package com.neoul.ex.global.integration;

import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.entity.SolarGenerationLog;
import com.neoul.ex.domain.ship.repository.ShipRepository;
import com.neoul.ex.domain.ship.repository.SolarGenerationLogRepository;
import com.neoul.ex.domain.user.entity.User;
import com.neoul.ex.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class ErdPersistenceTest {
    @Autowired BeachRepository beaches;
    @Autowired ShipRepository ships;
    @Autowired UserRepository users;
    @Autowired SolarGenerationLogRepository generation;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @Test
    void registrationsPersistWithoutDuplicatesAndAreRemovedWithUser() {
        var first = beaches.saveAndFlush(Beach.create("동명 해수욕장"));
        var second = beaches.saveAndFlush(Beach.create("동명 해수욕장"));
        first.updateLocation(35.1587, 129.1604);
        var user = User.createGuard("erd-user@example.com", "unused", first);
        user.registerBeach(first);
        user.registerBeach(second);
        users.saveAndFlush(user);
        em.clear();
        var reloaded = users.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getRegisteredBeachIds()).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(beaches.findById(first.getId()).orElseThrow().getLatitude()).isEqualTo(35.1587);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM star_beaches WHERE user_id = ?", Long.class, user.getId())).isEqualTo(2);
        users.delete(reloaded);
        users.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM star_beaches WHERE user_id = ?", Long.class, user.getId())).isZero();
    }

    @Test
    void removingRegistrationsSurvivesAnotherQueryInTheSameTransaction() {
        var beach = beaches.saveAndFlush(Beach.create("등록 해제 검증 해변"));
        var user = users.saveAndFlush(User.createGuard("erd-remove@example.com", "unused", beach));
        user.clearRegisteredBeaches();
        users.flush();
        assertThat(users.existsByEmail(user.getEmail())).isTrue();
        assertThat(users.findById(user.getId()).orElseThrow().getRegisteredBeachIds()).isEmpty();
        users.flush();
    }

    @Test
    void hourlyEnergyUsesMeasurementTimeAndKeepsZeroDistinctFromMissing() {
        var ship = newShip("ENERGY-01");
        Instant start = Instant.parse("2026-09-27T15:00:00Z"); // midnight in Korea
        Instant lateReceipt = start.plusSeconds(90000);
        generation.saveAndFlush(SolarGenerationLog.create(ship, start.minusSeconds(3600), lateReceipt, new BigDecimal("999")));
        generation.saveAndFlush(SolarGenerationLog.create(ship, start, lateReceipt, new BigDecimal("120.125")));
        generation.saveAndFlush(SolarGenerationLog.create(ship, start.plusSeconds(3600), lateReceipt, BigDecimal.ZERO));
        generation.saveAndFlush(SolarGenerationLog.create(ship, start.plusSeconds(86400), lateReceipt, new BigDecimal("999")));
        em.clear();
        var today = generation.findByShipIdAndPeriodStartedAtGreaterThanEqualAndPeriodStartedAtLessThanOrderByPeriodStartedAtAsc(
                ship.getId(), start, start.plusSeconds(86400));
        assertThat(today).hasSize(2);
        assertThat(today.stream().map(SolarGenerationLog::getEnergyWh).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("120.125");
        assertThat(today.get(1).getEnergyWh()).isZero();
    }

    @Test
    void databaseRejectsTwoEnergySamplesForTheSameShipAndHour() {
        var ship = newShip("ENERGY-02");
        Instant start = Instant.parse("2026-09-28T00:00:00Z");
        generation.saveAndFlush(SolarGenerationLog.create(ship, start, start.plusSeconds(3600), BigDecimal.TEN));
        assertThatThrownBy(() -> generation.saveAndFlush(
                SolarGenerationLog.create(ship, start, start.plusSeconds(7200), BigDecimal.TEN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsIncompleteIntervalsAndNegativeEnergy() {
        var ship = newShip("ENERGY-03");
        Instant start = Instant.parse("2026-09-28T00:00:00Z");
        assertThatThrownBy(() -> SolarGenerationLog.create(ship, start, start.plusSeconds(3599), BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SolarGenerationLog.create(ship, start, start.plusSeconds(3600), BigDecimal.valueOf(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Ship newShip(String code) {
        var beach = beaches.saveAndFlush(Beach.create("발전 검증 해변"));
        return ships.saveAndFlush(Ship.create(code, "발전 검증 배", beach));
    }
}
