package com.hvac.simulator.energy.runtime;

import java.util.Objects;

public record WaterPortValue(
        double temperatureC,
        double massFlowKgPerSecond,
        PressureValue pressure,
        FluidProperties fluidProperties,
        QualityStatus quality) implements PortValue {
    public WaterPortValue {
        Objects.requireNonNull(pressure, "压力状态不能为空");
        Objects.requireNonNull(fluidProperties, "介质属性不能为空");
        Objects.requireNonNull(quality, "质量状态不能为空");
        if (!Double.isFinite(temperatureC)
                || !Double.isFinite(massFlowKgPerSecond)
                || massFlowKgPerSecond < 0.0) {
            throw new IllegalArgumentException("水温必须有限，质量流量必须是有限非负值");
        }
    }
}
