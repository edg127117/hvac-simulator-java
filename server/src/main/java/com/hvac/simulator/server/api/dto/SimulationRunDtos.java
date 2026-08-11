package com.hvac.simulator.server.api.dto;

import com.hvac.simulator.server.domain.SimulationMode;
import com.hvac.simulator.server.domain.SimulationRunStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SimulationRunDtos {
    private SimulationRunDtos() {}

    public record CreateRequest(
            String modelVersion, SimulationMode mode, Long seed, Map<String, Double> overrides) {}

    public record Created(UUID runId, SimulationRunStatus status) {}

    public record RunView(
            UUID runId, String modelVersion, SimulationMode mode, long seed,
            Map<String, Double> overrides, SimulationRunStatus status,
            int completedSteps, int totalSteps, LocalDateTime simulationTime,
            String errorCode, String errorMessage, Instant createdAt) {}

    public record SeriesResponse(List<String> timestamps, List<SeriesGroup> groups) {}
    public record SeriesGroup(String code, String title, String unit, List<Series> series) {}
    public record Series(String code, String label, List<Double> values) {}
    public record Rows(int offset, int limit, int total, List<Map<String, Object>> items) {}
}
