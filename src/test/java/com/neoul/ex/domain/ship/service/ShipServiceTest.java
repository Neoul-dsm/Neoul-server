package com.neoul.ex.domain.ship.service;

import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.domain.auth.service.ResourceAccessService;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;
import com.neoul.ex.domain.ship.dto.ConnectionStatus;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.entity.value.ShipLocation;
import com.neoul.ex.domain.ship.entity.value.ShipStatus;
import com.neoul.ex.domain.ship.repository.ShipRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShipServiceTest {
    private final Instant now = Instant.parse("2026-09-20T00:00:00Z");
    private final ShipRepository ships = mock(ShipRepository.class);
    private final BeachRepository beaches = mock(BeachRepository.class);
    private final ResourceAccessService access = mock(ResourceAccessService.class);
    private final ShipService service = new ShipService(ships, beaches, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(2), access);
    private Ship ship;

    @BeforeEach
    void setUp() {
        when(access.readScope(null)).thenReturn(new ResourceAccessService.Scope(Role.ADMIN, null));
        ship = Ship.create("SG-01", "보트", Beach.create("해수욕장"));
        when(ships.findById(1L)).thenReturn(Optional.of(ship));
    }

    @Test
    void neverReceivedDataIsUnknownRatherThanZeroOrOffline() {
        var response = service.getConnection(1L, null);
        assertThat(response.connectionStatus()).isEqualTo(ConnectionStatus.UNKNOWN);
        assertThat(response.lastReceivedAt()).isNull();
        assertThat(service.getLocation(1L, null).latitude()).isNull();
        assertThat(service.getLocation(1L, null).longitude()).isNull();
    }

    @Test
    void disconnectedShipKeepsItsLastKnownCoordinates() {
        Instant old = now.minusSeconds(121);
        ship.updateLocation(new ShipLocation(35.1, 129.1, old));
        var response = service.getLocation(1L, null);
        assertThat(service.getConnection(1L, null).connectionStatus()).isEqualTo(ConnectionStatus.OFFLINE);
        assertThat(response.latitude()).isEqualTo(35.1);
        assertThat(response.longitude()).isEqualTo(129.1);
    }

    @Test
    void timeoutBoundaryIsOfflineAndNewestPacketDeterminesConnection() {
        ship.updateLocation(new ShipLocation(35.1, 129.1, now.minusSeconds(600)));
        ship.updateStatus(new ShipStatus(80.0, null, null, null, null, null, now.minusSeconds(120), now.minusSeconds(120)));
        assertThat(service.getConnection(1L, null).connectionStatus()).isEqualTo(ConnectionStatus.OFFLINE);
        ship.updateStatus(new ShipStatus(80.0, null, null, null, null, null, now.minusSeconds(119), now.minusSeconds(119)));
        assertThat(service.getConnection(1L, null).connectionStatus()).isEqualTo(ConnectionStatus.ONLINE);
        assertThat(service.getLocation(1L, null).latitude()).isEqualTo(35.1);
        ship.updateLocation(new ShipLocation(35.2, 129.2, now));
        assertThat(service.getConnection(1L, null).lastReceivedAt()).isEqualTo(now);
    }

    @Test
    void nonexistentBeachIsNotReportedAsAnEmptyFleet() {
        assertThatThrownBy(() -> service.getShips(999L, null)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BEACH_NOT_FOUND));
        verify(ships, never()).findByBeachIdOrderByCodeAsc(anyLong());
    }

    @Test
    void invalidIdsAreRejectedBeforeLookup() {
        assertThatThrownBy(() -> service.getShip(0L, null)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> service.getShips(-1L, null)).isInstanceOf(BusinessException.class);
    }
}
