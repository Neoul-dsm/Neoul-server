package com.neoul.ex.domain.alert.service;

import com.neoul.ex.domain.alert.dto.DrowningAlertResponse;
import com.neoul.ex.domain.alert.entity.HumanLog;
import com.neoul.ex.domain.alert.repository.HumanLogRepository;
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
public class DrowningAlertService implements AlertSource<DrowningAlertResponse> {
    private static final int BATCH_SIZE = 100;
    private final ResourceAccessService access;
    private final BeachRepository beaches;
    private final ShipRepository ships;
    private final HumanLogRepository logs;

    @Override
    public Long authorize(AccessTokenPrincipal principal, Long beachId) {
        return access.requireAlertBeach(principal, Set.of(Role.GUARD), beachId);
    }

    @Override
    public boolean hasEvent(Long beachId, long eventId) {
        return logs.existsByIdAndShipBeachId(eventId, beachId);
    }

    @Override
    public List<DrowningAlertResponse> getNext(AccessTokenPrincipal principal, Long beachId, long cursor) {
        if (!authorize(principal, beachId).equals(beachId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return logs.findByShipBeachIdAndIdGreaterThanOrderByIdAsc(beachId, cursor, PageRequest.of(0, BATCH_SIZE))
                .stream().map(DrowningAlertResponse::from).toList();
    }

    // Future ingestion adapters call this service after receiving an actual log.
    // The beach lock ensures ID order matches commit order within each beach.
    @Transactional
    public DrowningAlertResponse record(Long shipId, String eventId, String imageUrl, Instant detectedAt,
                                        double latitude, double longitude) {
        return record(shipId, eventId, imageUrl, detectedAt, latitude, longitude, null);
    }

    @Transactional
    public DrowningAlertResponse record(Long shipId, String eventId, String imageUrl, Instant detectedAt,
                                        double latitude, double longitude, Boolean aiResult) {
        if (shipId == null || shipId <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
        var ship = ships.findById(shipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHIP_NOT_FOUND));
        beaches.findByIdForUpdate(ship.getBeach().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BEACH_NOT_FOUND));
        HumanLog log;
        try {
            log = HumanLog.create(ship, eventId, imageUrl, detectedAt, latitude, longitude, aiResult);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return logs.findByShipIdAndEventId(shipId, log.getEventId())
                .map(DrowningAlertResponse::from)
                .orElseGet(() -> DrowningAlertResponse.from(logs.saveAndFlush(log)));
    }
}
