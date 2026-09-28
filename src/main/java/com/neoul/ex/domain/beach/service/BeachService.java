package com.neoul.ex.domain.beach.service;

import com.neoul.ex.domain.beach.dto.BeachDetailResponse;
import com.neoul.ex.domain.beach.dto.BeachResponse;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;
import com.neoul.ex.domain.ship.repository.ShipRepository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BeachService {

    private final BeachRepository beachRepository;
    private final ShipRepository shipRepository;

    public List<BeachResponse> getBeaches(String keyword) {
        var beaches = StringUtils.hasText(keyword)
                ? beachRepository.findByNameContainingOrderByNameAsc(keyword.trim())
                : beachRepository.findAllByOrderByNameAsc();

        return beaches.stream()
                .map(BeachResponse::from)
                .toList();
    }

    public BeachDetailResponse getBeach(Long beachId) {
        Beach beach = findBeach(beachId);
        return new BeachDetailResponse(beach.getId(), beach.getName(), shipRepository.countByBeachId(beachId));
    }

    private Beach findBeach(Long beachId) {
        if (beachId == null || beachId <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
        return beachRepository.findById(beachId).orElseThrow(() -> new BusinessException(ErrorCode.BEACH_NOT_FOUND));
    }
}
