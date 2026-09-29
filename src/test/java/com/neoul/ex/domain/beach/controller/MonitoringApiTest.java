package com.neoul.ex.domain.beach.controller;

import com.neoul.ex.domain.user.entity.User;
import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.global.security.JwtProvider;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.testutil.CommonResponseAssertions;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.repository.ShipRepository;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("client-preview")
@Transactional
class MonitoringApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private BeachRepository beaches;
    @Autowired private ShipRepository ships;
    @Autowired private com.neoul.ex.domain.user.repository.UserRepository users;
    @Autowired private com.neoul.ex.global.security.JwtProvider jwt;
    private Beach beach;
    private Beach emptyBeach;
    private Ship ship;
    @Autowired private org.springframework.web.context.WebApplicationContext context;

    @BeforeEach
    void setUp() {
        var admin = com.neoul.ex.domain.user.entity.User.createGuard("monitor-admin@example.com", "unused", null);
        org.springframework.test.util.ReflectionTestUtils.setField(admin, "role", com.neoul.ex.domain.user.entity.enums.Role.ADMIN);
        users.saveAndFlush(admin);
        String token = jwt.createAccessToken(admin.getId(), com.neoul.ex.domain.user.entity.enums.Role.ADMIN);
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(get("/").header("Authorization", "Bearer " + token))
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .alwaysExpect(CommonResponseAssertions::assertCommonResponse).build();
        beach = Beach.create("검증용 해변");
        beaches.saveAndFlush(beach);
        emptyBeach = beaches.saveAndFlush(Beach.create("수신 전 검증 해변"));
        ship = Ship.create("TEST-01", "검증용 보트", beach);
        ship.updateLocation(35.1, 129.1, Instant.parse("2026-09-19T00:00:00Z"));
        ships.saveAndFlush(ship);
    }

    @Test
    void beachSearchKeepsExistingContract() throws Exception {
        mvc.perform(get("/beaches").param("keyword", " 검증용 "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("BEACH_LIST_RETRIEVED"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("검증용 해변"));
    }

    @Test
    void filtersFleetByBeachAndReturnsBeachShipCount() throws Exception {
        mvc.perform(get("/ships").param("beachId", beach.getId().toString()))
                .andExpect(jsonPath("$.code").value("SHIP_LIST_RETRIEVED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].code").value("TEST-01"))
                .andExpect(jsonPath("$.data[0].beachName").value("검증용 해변"));
        mvc.perform(get("/beaches/{id}", beach.getId()))
                .andExpect(jsonPath("$.code").value("BEACH_RETRIEVED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shipCount").value(1));
        mvc.perform(get("/ships").param("beachId", emptyBeach.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void detailContainsOnlyBasicInformationAndLocationKeepsLastKnownPosition() throws Exception {
        mvc.perform(get("/ships/{id}", ship.getId()))
                .andExpect(jsonPath("$.code").value("SHIP_RETRIEVED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.data.connectionStatus").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist());
        mvc.perform(get("/ships/{id}/location", ship.getId()))
                .andExpect(jsonPath("$.code").value("SHIP_LOCATION_RETRIEVED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.latitude").value(35.1))
                .andExpect(jsonPath("$.data.longitude").value(129.1));
    }

    @Test
    void distinguishesMissingResourcesFromBadInput() throws Exception {
        mvc.perform(get("/ships/999999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/beaches/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/ships").param("beachId", "999999")).andExpect(status().isNotFound());
        mvc.perform(get("/ships/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/beaches/-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/ships").param("beachId", "abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/beaches/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void previewDoesNotAllowShipWrites() throws Exception {
        mvc.perform(post("/ships/{id}/commands", ship.getId())).andExpect(status().is4xxClientError());
    }

    @Test
    void formatsMissingRoutesUnderPublicPaths() throws Exception {
        mvc.perform(get("/beaches/1/unknown")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
