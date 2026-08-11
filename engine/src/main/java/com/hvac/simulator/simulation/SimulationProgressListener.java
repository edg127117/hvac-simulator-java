package com.hvac.simulator.simulation;

import java.time.LocalDateTime;

/** 接收仿真推进快照，不参与公式和状态更新。 */
@FunctionalInterface
public interface SimulationProgressListener {
    SimulationProgressListener NOOP = (completedSteps, totalSteps, simulationTime) -> { };

    void onProgress(int completedSteps, int totalSteps, LocalDateTime simulationTime);
}
