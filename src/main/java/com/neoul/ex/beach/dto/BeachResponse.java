package com.neoul.ex.beach.dto;

import com.neoul.ex.beach.entity.Beach;

public record BeachResponse(Long id, String name) {

    public static BeachResponse from(Beach beach) {
        return new BeachResponse(beach.getId(), beach.getName());
    }
}
