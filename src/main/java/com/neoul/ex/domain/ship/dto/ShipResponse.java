package com.neoul.ex.domain.ship.dto;

import com.neoul.ex.domain.ship.entity.Ship;


public record ShipResponse(Long id, String code, String name, Long beachId, String beachName) {
    public static ShipResponse from(Ship ship) {
        return new ShipResponse(ship.getId(), ship.getCode(), ship.getName(), ship.getBeach().getId(),
                ship.getBeach().getName());
    }
}
