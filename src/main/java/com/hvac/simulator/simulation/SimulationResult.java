package com.hvac.simulator.simulation;

import java.util.List;
import java.util.Objects;

/** 一次完整仿真的不可变逐分钟结果。 */
public record SimulationResult(List<SimulationStep> steps) {

    public SimulationResult {
        Objects.requireNonNull(steps, "仿真结果不能为空");
        steps = List.copyOf(steps);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("仿真结果不能为空");
        }
    }
}
