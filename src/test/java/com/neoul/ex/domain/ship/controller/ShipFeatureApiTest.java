package com.neoul.ex.domain.ship.controller;

import com.neoul.ex.domain.user.entity.User;
import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.global.security.JwtProvider;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.testutil.CommonResponseAssertions;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.repository.ShipRepository;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShipFeatureApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private BeachRepository beaches;
    @Autowired private ShipRepository ships;
    @Autowired private UserRepository users;
    @Autowired private JwtProvider jwt;
    private Ship ship;
    private Ship foreignShip;
    private User guard;
    private User admin;
    private String guardToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        var own = beaches.saveAndFlush(Beach.create("기능별 조회 소속"));
        var other = beaches.saveAndFlush(Beach.create("기능별 조회 다른 해변"));
        ship = ships.saveAndFlush(Ship.create("FEATURE-01", "조회 검증 배", own));
        foreignShip = ships.saveAndFlush(Ship.create("FEATURE-02", "다른 소속 배", other));
        guard = users.saveAndFlush(User.createGuard("feature-guard@example.com", "unused", own));
        admin = User.createGuard("feature-admin@example.com", "unused", null);
        ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
        users.saveAndFlush(admin);
        guardToken = jwt.createAccessToken(guard.getId(), Role.GUARD);
        adminToken = jwt.createAccessToken(admin.getId(), Role.ADMIN);
    }

    @Test
    void connectionReturnsOnlyTimeAndStateAndPreservesUnknown() throws Exception {
        read(ship.getId(), "connection", guardToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SHIP_CONNECTION_RETRIEVED"))
                .andExpect(jsonPath("$.data", aMapWithSize(2)))
                .andExpect(jsonPath("$.data.connectionStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.lastReceivedAt").value(nullValue()));
        var received = Instant.parse("2026-01-01T00:00:00Z");
        ship.updateLocation(35.1, 129.1, received);
        ships.saveAndFlush(ship);
        read(ship.getId(), "connection", guardToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connectionStatus").value("OFFLINE"))
                .andExpect(jsonPath("$.data.lastReceivedAt").value(received.toString()));
        ship.updateLocation(35.1, 129.1, Instant.now());
        ships.saveAndFlush(ship);
        read(ship.getId(), "connection", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connectionStatus").value("ONLINE"));
    }

    @Test
    void locationReturnsOnlyGpsAndKeepsLastCoordinatesWhenOffline() throws Exception {
        read(ship.getId(), "location", guardToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SHIP_LOCATION_RETRIEVED"))
                .andExpect(jsonPath("$.data", aMapWithSize(2)))
                .andExpect(jsonPath("$.data.latitude").value(nullValue()))
                .andExpect(jsonPath("$.data.longitude").value(nullValue()));
        ship.updateLocation(35.1, 129.1, Instant.parse("2026-01-01T00:00:00Z"));
        ships.saveAndFlush(ship);
        read(ship.getId(), "location", guardToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", aMapWithSize(2)))
                .andExpect(jsonPath("$.data.latitude").value(35.1))
                .andExpect(jsonPath("$.data.longitude").value(129.1));
        ship.updateLocation(null, 129.1, Instant.now());
        ships.saveAndFlush(ship);
        read(ship.getId(), "location", guardToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latitude").value(nullValue()))
                .andExpect(jsonPath("$.data.longitude").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"solar-power", "battery"})
    void removedMeasurementsReturnNotFound(String feature) throws Exception {
        for (String token : new String[]{guardToken, adminToken}) {
            read(ship.getId(), feature, token).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"connection", "location"})
    void featuresEnforceAuthenticationInputAndResourceChecks(String feature) throws Exception {
        mvc.perform(get("/ships/" + ship.getId() + "/" + feature)).andExpect(status().isForbidden())
                .andExpect(CommonResponseAssertions::assertCommonResponse);
        read(ship.getId(), feature, "invalid").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        read(0L, feature, adminToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(get("/ships/abc/" + feature).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest()).andExpect(CommonResponseAssertions::assertCommonResponse);
        read(Long.MAX_VALUE, feature, adminToken).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHIP_NOT_FOUND"));
        read(foreignShip.getId(), feature, adminToken).andExpect(status().isOk());
        read(foreignShip.getId(), feature, guardToken).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        guard.clearRegisteredBeaches();
        users.flush();
        read(ship.getId(), feature, guardToken).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BEACH_NOT_ASSIGNED"));
    }

    private ResultActions read(Long shipId, String feature, String token) throws Exception {
        return mvc.perform(get("/ships/" + shipId + "/" + feature).header("Authorization", "Bearer " + token))
                .andExpect(CommonResponseAssertions::assertCommonResponse);
    }
}
