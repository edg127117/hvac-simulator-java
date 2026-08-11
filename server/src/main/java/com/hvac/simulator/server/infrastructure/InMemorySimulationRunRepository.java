package com.hvac.simulator.server.infrastructure;

import com.hvac.simulator.server.domain.SimulationRun;
import com.hvac.simulator.server.domain.SimulationRunRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemorySimulationRunRepository implements SimulationRunRepository {
    private final ConcurrentHashMap<UUID, SimulationRun> runs = new ConcurrentHashMap<>();

    @Override
    public void save(SimulationRun run) {
        runs.put(run.id(), run);
    }

    @Override
    public Optional<SimulationRun> find(UUID id) {
        return Optional.ofNullable(runs.get(id));
    }
}
