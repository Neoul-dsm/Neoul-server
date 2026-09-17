package com.neoul.ex.service;

import com.neoul.ex.dto.BeachResponse;
import com.neoul.ex.repository.BeachRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BeachService {

    private final BeachRepository beachRepository;

    public List<BeachResponse> getBeaches(String keyword) {
        var beaches = StringUtils.hasText(keyword)
                ? beachRepository.findByNameContainingOrderByNameAsc(keyword.trim())
                : beachRepository.findAllByOrderByNameAsc();

        return beaches.stream()
                .map(BeachResponse::from)
                .toList();
    }
}
