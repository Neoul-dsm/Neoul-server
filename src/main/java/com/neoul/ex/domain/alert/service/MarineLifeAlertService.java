package com.neoul.ex.domain.alert.service;

import com.neoul.ex.domain.alert.dto.MarineLifeAlertResponse;
import com.neoul.ex.domain.alert.entity.MarineLifeLog;
import com.neoul.ex.domain.alert.repository.MarineLifeLogRepository;
import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.global.security.AccessTokenPrincipal;
import com.neoul.ex.domain.auth.service.ResourceAccessService;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;
import com.neoul.ex.domain.ship.repository.ShipRepository;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarineLifeAlertService implements AlertSource<MarineLifeAlertResponse> {
    private static final int BATCH_SIZE = 100;
    private final ResourceAccessService access;
    private final BeachRepository beaches;
    private final ShipRepository ships;
    private final MarineLifeLogRepository logs;

    @Override
    public Long authorize(AccessTokenPrincipal principal, Long beachId) {
        return access.requireAlertBeach(principal, Set.of(Role.ADMIN, Role.GUARD), beachId);
    }

    @Override
    public boolean hasEvent(Long beachId, long eventId) {
        return logs.existsByIdAndShipBeachId(eventId, beachId);
    }

    @Override
    public List<MarineLifeAlertResponse> getNext(AccessTokenPrincipal principal, Long beachId, long cursor) {
        if (!authorize(principal, beachId).equals(beachId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return logs.findByShipBeachIdAndIdGreaterThanOrderByIdAsc(beachId, cursor, PageRequest.of(0, BATCH_SIZE))
                .stream().map(MarineLifeAlertResponse::from).toList();
    }

    // Future ingestion adapters use this method so IDs follow commit order within a beach.
    @Transactional
    public MarineLifeAlertResponse record(Long shipId, String eventId, String species, int count, Instant detectedAt) {
        if (shipId == null || shipId <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
        var ship = ships.findById(shipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHIP_NOT_FOUND));
        beaches.findByIdForUpdate(ship.getBeach().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BEACH_NOT_FOUND));
        MarineLifeLog log;
        try {
            log = MarineLifeLog.create(ship, eventId, species, count, detectedAt);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return logs.findByShipIdAndEventId(shipId, log.getEventId())
                .map(MarineLifeAlertResponse::from)
                .orElseGet(() -> MarineLifeAlertResponse.from(logs.saveAndFlush(log)));
    }
}
