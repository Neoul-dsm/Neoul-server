package com.neoul.ex.domain.ship.dto;

import java.time.Instant;

public record ShipConnectionResponse(Instant lastReceivedAt, ConnectionStatus connectionStatus) {}
