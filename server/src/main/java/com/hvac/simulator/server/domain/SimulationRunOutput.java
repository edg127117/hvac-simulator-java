package com.hvac.simulator.server.domain;

import com.hvac.simulator.simulation.Gaia11SimulationResult;
import com.hvac.simulator.simulation.SimulationResult;

/** 一个任务只持有与所选版本相符的 Java 结果。 */
public record SimulationRunOutput(
        SimulationResult gaia10,
        Gaia11SimulationResult gaia11) {

    public SimulationRunOutput {
        if ((gaia10 == null) == (gaia11 == null)) {
            throw new IllegalArgumentException("任务结果必须且只能包含一个 Gaia 版本");
        }
    }

    public static SimulationRunOutput gaia10(SimulationResult result) {
        return new SimulationRunOutput(result, null);
    }

    public static SimulationRunOutput gaia11(Gaia11SimulationResult result) {
        return new SimulationRunOutput(null, result);
    }
}
