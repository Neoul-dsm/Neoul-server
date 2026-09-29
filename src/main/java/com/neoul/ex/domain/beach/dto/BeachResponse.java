package com.neoul.ex.domain.beach.dto;

import com.neoul.ex.domain.beach.entity.Beach;


public record BeachResponse(Long id,
                            String name, Double latitude, Double longitude
) {
    public static BeachResponse from(Beach beach) {
        return new BeachResponse(beach.getId(), beach.getName(), beach.getLatitude(), beach.getLongitude());
    }
}
