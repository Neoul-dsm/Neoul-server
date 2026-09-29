package com.neoul.ex.domain.ship.service;

import com.neoul.ex.global.security.AccessTokenPrincipal;
import com.neoul.ex.domain.auth.service.ResourceAccessService;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;
import com.neoul.ex.domain.ship.dto.ConnectionStatus;
import com.neoul.ex.domain.ship.dto.ShipConnectionResponse;
import com.neoul.ex.domain.ship.dto.ShipLocationResponse;
import com.neoul.ex.domain.ship.dto.ShipResponse;
import com.neoul.ex.domain.ship.entity.Ship;
import com.neoul.ex.domain.ship.repository.ShipRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ShipService {
    private final ShipRepository shipRepository;
    private final BeachRepository beachRepository;
    private final Clock clock;
    private final Duration connectionTimeout;
    private final ResourceAccessService access;

    public ShipService(ShipRepository shipRepository, BeachRepository beachRepository, Clock clock,
                       @Value("${monitoring.ship-connection-timeout:PT2M}") Duration connectionTimeout,
                       ResourceAccessService access) {
        if (connectionTimeout.isNegative() || connectionTimeout.isZero()) {
            throw new IllegalArgumentException("Ship connection timeout must be positive");
        }
        this.shipRepository = shipRepository;
        this.beachRepository = beachRepository;
        this.clock = clock;
        this.connectionTimeout = connectionTimeout;
        this.access = access;
    }

    public List<ShipResponse> getShips(Long beachId, AccessTokenPrincipal principal) {
        var scope = access.readScope(principal);
        List<Ship> ships;
        if (beachId != null) {
            validateId(beachId);
            scope.requireBeach(beachId);
            if (!beachRepository.existsById(beachId)) {
                throw new BusinessException(ErrorCode.BEACH_NOT_FOUND);
            }
            ships = shipRepository.findByBeachIdOrderByCodeAsc(beachId);
        } else if (scope.isAdmin()) {
            ships = shipRepository.findAllByOrderByCodeAsc();
        } else {
            ships = shipRepository.findByBeachIdInOrderByCodeAsc(scope.beachIds());
        }
        return ships.stream().map(ShipResponse::from).toList();
    }

    public ShipResponse getShip(Long shipId, AccessTokenPrincipal principal) {
        return ShipResponse.from(findAccessibleShip(shipId, principal));
    }

    public ShipConnectionResponse getConnection(Long shipId, AccessTokenPrincipal principal) {
        Instant receivedAt = lastReceivedAt(findAccessibleShip(shipId, principal));
        return new ShipConnectionResponse(receivedAt, connectionStatus(receivedAt, clock.instant()));
    }

    private Ship findAccessibleShip(Long shipId, AccessTokenPrincipal principal) {
        var scope = access.readScope(principal);
        validateId(shipId);
        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHIP_NOT_FOUND));
        scope.requireBeach(ship.getBeach().getId());
        return ship;
    }

    public ShipLocationResponse getLocation(Long shipId, AccessTokenPrincipal principal) {
        Ship ship = findAccessibleShip(shipId, principal);
        if (ship.getLatitude() == null || ship.getLongitude() == null) {
            return new ShipLocationResponse(null, null);
        }
        return new ShipLocationResponse(ship.getLatitude(), ship.getLongitude());
    }

    private Instant lastReceivedAt(Ship ship) {
        Instant statusAt = ship.getLastCommunicationAt();
        Instant locationAt = ship.getLocationReceivedAt();
        if (statusAt == null) return locationAt;
        return locationAt != null && locationAt.isAfter(statusAt) ? locationAt : statusAt;
    }

    private ConnectionStatus connectionStatus(Instant receivedAt, Instant now) {
        if (receivedAt == null) return ConnectionStatus.UNKNOWN;
        return receivedAt.isAfter(now.minus(connectionTimeout)) ? ConnectionStatus.ONLINE : ConnectionStatus.OFFLINE;
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
}
