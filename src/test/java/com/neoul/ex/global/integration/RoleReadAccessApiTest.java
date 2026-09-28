package com.neoul.ex.global.integration;

import com.neoul.ex.domain.user.entity.User;
import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.global.security.JwtProvider;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.testutil.CommonResponseAssertions;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.entity.enums.ChargingStatus;
import com.neoul.ex.domain.ship.entity.value.ShipLocation;
import com.neoul.ex.domain.ship.entity.value.ShipStatus;
import com.neoul.ex.domain.ship.repository.ShipRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class RoleReadAccessApiTest {
    @Autowired MockMvc mvc;
    @Autowired BeachRepository beaches;
    @Autowired UserRepository users;
    @Autowired ShipRepository ships;
    @Autowired JwtProvider jwt;
    Beach own;
    Beach other;
    User guard;
    User admin;
    String guardToken;
    String adminToken;

    @BeforeEach
    void setUp() {
        own = beaches.saveAndFlush(Beach.create("권한 검증 소속 해변"));
        other = beaches.saveAndFlush(Beach.create("권한 검증 다른 해변"));
        guard = users.saveAndFlush(User.createGuard("access-guard@example.com", "unused", own));
        admin = User.createGuard("access-admin@example.com", "unused", null);
        ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
        users.saveAndFlush(admin);
        guardToken = jwt.createAccessToken(guard.getId(), Role.GUARD);
        adminToken = jwt.createAccessToken(admin.getId(), Role.ADMIN);
    }

    @Test
    void removedMonitoringReturnsNotFoundForAnonymousGuardAndAdmin() throws Exception {
        mvc.perform(get("/beaches/" + own.getId() + "/monitoring"))
                .andExpect(status().isNotFound()).andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        read("/beaches/" + own.getId() + "/monitoring", guardToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        read("/beaches/" + own.getId() + "/monitoring", adminToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void beachSearchAndDetailRemainPublic() throws Exception {
        mvc.perform(get("/beaches")).andExpect(status().isOk())
                .andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value("BEACH_LIST_RETRIEVED"));
        mvc.perform(get("/beaches/" + own.getId())).andExpect(status().isOk())
                .andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value("BEACH_RETRIEVED"));
    }

    ResultActions read(String path, String token) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(CommonResponseAssertions::assertCommonResponse);
    }

    @Test
    void guardListDefaultsToOwnBeachAndReturnsOnlyBasicInformation() throws Exception {
        seedShips();
        read("/ships", guardToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].beachId").value(own.getId()))
                .andExpect(jsonPath("$.data[0]", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.data[0].status").doesNotExist());
        read("/ships?beachId=" + other.getId(), guardToken).andExpect(status().isForbidden());
        read("/ships?beachId=99999999", guardToken).andExpect(status().isForbidden());
        read("/ships?beachId=" + own.getId(), guardToken).andExpect(status().isOk());
        mvc.perform(get("/ships")).andExpect(status().isForbidden());
    }

    @Test
    void adminListIncludesBasicInformationFromAnyBeach() throws Exception {
        seedShips();
        read("/ships?beachId=" + other.getId(), adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].beachId").value(other.getId()))
                .andExpect(jsonPath("$.data[0]", org.hamcrest.Matchers.aMapWithSize(5)));
        read("/ships", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void listRejectsUnassignedGuardAndReflectsRoleChanges() throws Exception {
        seedShips();
        ReflectionTestUtils.setField(guard, "beach", null);
        users.saveAndFlush(guard);
        read("/ships", guardToken).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BEACH_NOT_ASSIGNED"));
        ReflectionTestUtils.setField(admin, "role", Role.GUARD);
        ReflectionTestUtils.setField(admin, "beach", own);
        users.saveAndFlush(admin);
        read("/ships", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].beachId").value(own.getId()))
                .andExpect(jsonPath("$.data[0].status").doesNotExist());
    }

    void seedShips() {
        for (var beach : new Beach[]{own, other}) {
            var ship = Ship.create("ACCESS-" + beach.getId(), "권한 검증 배", beach);
            ship.updateLocation(new ShipLocation(35.1, 129.1, java.time.Instant.now()));
            ship.updateStatus(new ShipStatus(80.0, 340.0, 12.0, -65, 14.2, ChargingStatus.CHARGING,
                    java.time.Instant.now(), java.time.Instant.now()));
            ships.saveAndFlush(ship);
        }
    }

    @Test
    void guardDetailReturnsBasicInformationAndRejectsForeignShips() throws Exception {
        seedShips();
        var ownShip = ships.findByBeachIdOrderByCodeAsc(own.getId()).getFirst();
        var foreignShip = ships.findByBeachIdOrderByCodeAsc(other.getId()).getFirst();
        read("/ships/" + ownShip.getId(), guardToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ownShip.getId()))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.data.status").doesNotExist());
        read("/ships/" + foreignShip.getId(), guardToken).andExpect(status().isForbidden());
        mvc.perform(get("/ships/" + ownShip.getId())).andExpect(status().isForbidden());
        ReflectionTestUtils.setField(guard, "beach", null);
        users.saveAndFlush(guard);
        read("/ships/" + ownShip.getId(), guardToken).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BEACH_NOT_ASSIGNED"));
    }

    @Test
    void adminSolarEndpointRetainsMeasurementsIncludingUnmeasuredNull() throws Exception {
        seedShips();
        var ship = ships.findByBeachIdOrderByCodeAsc(other.getId()).getFirst();
        read("/ships/" + ship.getId() + "/solar-power", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.solarPowerW").value(340));
        ship.updateStatus(new ShipStatus(80.0, null, 12.0, -65, 14.2, ChargingStatus.CHARGING,
                java.time.Instant.now(), java.time.Instant.now()));
        ships.saveAndFlush(ship);
        read("/ships/" + ship.getId() + "/solar-power", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.hasKey("solarPowerW")))
                .andExpect(jsonPath("$.data.solarPowerW").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void detailUsesNewRoleAndNewAssignmentWithoutReissuingToken() throws Exception {
        seedShips();
        var ship = ships.findByBeachIdOrderByCodeAsc(other.getId()).getFirst();
        ReflectionTestUtils.setField(admin, "role", Role.GUARD);
        ReflectionTestUtils.setField(admin, "beach", own);
        users.saveAndFlush(admin);
        read("/ships/" + ship.getId(), adminToken).andExpect(status().isForbidden());
        ReflectionTestUtils.setField(admin, "beach", other);
        users.saveAndFlush(admin);
        read("/ships/" + ship.getId(), adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.beachId").value(other.getId()))
                .andExpect(jsonPath("$.data.status").doesNotExist());
    }

    @Test
    void locationChecksOwnershipAndHasNoPreviewBypass() throws Exception {
        seedShips();
        var ownShip = ships.findByBeachIdOrderByCodeAsc(own.getId()).getFirst();
        var foreignShip = ships.findByBeachIdOrderByCodeAsc(other.getId()).getFirst();
        read("/ships/" + ownShip.getId() + "/location", guardToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latitude").value(35.1));
        read("/ships/" + foreignShip.getId() + "/location", guardToken).andExpect(status().isForbidden());
        read("/ships/" + foreignShip.getId() + "/location", adminToken).andExpect(status().isOk());
        mvc.perform(get("/ships/" + ownShip.getId() + "/location")).andExpect(status().isForbidden());
        ReflectionTestUtils.setField(guard, "beach", null);
        users.saveAndFlush(guard);
        read("/ships/" + ownShip.getId() + "/location", guardToken).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BEACH_NOT_ASSIGNED"));
    }

    @Test
    void oldTokenCannotReadShipsMovedOutOfAssignedBeach() throws Exception {
        seedShips();
        var ship = ships.findByBeachIdOrderByCodeAsc(own.getId()).getFirst();
        ReflectionTestUtils.setField(ship, "beach", other);
        ships.saveAndFlush(ship);
        read("/ships/" + ship.getId(), guardToken).andExpect(status().isForbidden());
        read("/ships/" + ship.getId() + "/location", guardToken).andExpect(status().isForbidden());
        read("/ships", guardToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }
}
