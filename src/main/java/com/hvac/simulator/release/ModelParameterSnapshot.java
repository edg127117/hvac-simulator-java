package com.hvac.simulator.release;

import com.hvac.simulator.config.BuildingEnvelope;
import com.hvac.simulator.config.Gaia11MeasurementParameters;
import com.hvac.simulator.config.HvacParameters;
import com.hvac.simulator.config.InternalLoad;
import java.util.Map;

/** 一次运行实际使用的不可变参数对象和用户覆盖值。 */
public record ModelParameterSnapshot(
        ModelVersion version,
        BuildingEnvelope building,
        InternalLoad internalLoad,
        HvacParameters hvac,
        Gaia11MeasurementParameters measurement,
        Map<String, Double> overrides) {
    public ModelParameterSnapshot {
        overrides = Map.copyOf(overrides);
    }
}
