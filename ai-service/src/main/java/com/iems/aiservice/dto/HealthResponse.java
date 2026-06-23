package com.iems.aiservice.dto;

public record HealthResponse(
                String service,
                String status,
                String model) {
}
