package com.neoul.ex.domain.alert.service;

import com.neoul.ex.domain.alert.dto.DrowningAlertResponse;
import com.neoul.ex.domain.alert.entity.PersonLog;
import com.neoul.ex.domain.alert.repository.PersonLogRepository;
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
    private final PersonLogRepository logs;

    public Long authorize(AccessTokenPrincipal principal) {
        return access.requireAlertBeach(principal, Set.of(Role.GUARD));
    }

    public long validateCursor(Long beachId, String lastEventId) {
        if (lastEventId == null) return 0;
        try {
            long cursor = Long.parseLong(lastEventId);
            if (cursor < 0 || (cursor > 0 && !logs.existsByIdAndShipBeachId(cursor, beachId))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return cursor;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    public List<DrowningAlertResponse> getNext(AccessTokenPrincipal principal, Long beachId, long cursor) {
        if (!authorize(principal).equals(beachId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return logs.findByShipBeachIdAndIdGreaterThanOrderByIdAsc(beachId, cursor, PageRequest.of(0, BATCH_SIZE))
                .stream().map(DrowningAlertResponse::from).toList();
    }

    // Future ingestion adapters call this service after receiving an actual log.
    // The beach lock ensures ID order matches commit order within each beach.
    @Transactional
    public DrowningAlertResponse record(Long shipId, String imageUrl, Instant detectedAt,
                                        double latitude, double longitude) {
        if (shipId == null || shipId <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
        var ship = ships.findById(shipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHIP_NOT_FOUND));
        beaches.findByIdForUpdate(ship.getBeach().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BEACH_NOT_FOUND));
        PersonLog log;
        try {
            log = PersonLog.create(ship, imageUrl, detectedAt, latitude, longitude);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return DrowningAlertResponse.from(logs.saveAndFlush(log));
    }
}
