package com.hvac.simulator.server.domain;

import java.util.Optional;
import java.util.UUID;

public interface SimulationRunRepository {
    void save(SimulationRun run);
    Optional<SimulationRun> find(UUID id);
}
