package com.hvac.simulator.simulation;

import java.util.List;
import java.util.Objects;

/** 一次 Gaia 1.1 Java 独立计算形成的不可变结果。 */
public record Gaia11SimulationResult(List<Gaia11SimulationStep> steps) {
    public Gaia11SimulationResult {
        Objects.requireNonNull(steps, "Gaia 1.1 仿真结果不能为空");
        steps = List.copyOf(steps);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Gaia 1.1 仿真结果不能为空");
        }
    }
}
