package com.neoul.ex.domain.ship.dto;

public record ShipApiKeyResponse(Long shipId, String apiKey) {
    @Override
    public String toString() {
        return "ShipApiKeyResponse[shipId=" + shipId + ", apiKey=REDACTED]";
    }
}
