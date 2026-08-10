package com.hvac.simulator.energy.runtime;

import java.util.Objects;

public record SetpointSignalValue(
        double value, UnitCode unit, QualityStatus quality) implements PortValue {
    public SetpointSignalValue {
        Objects.requireNonNull(unit, "设定值单位不能为空");
        Objects.requireNonNull(quality, "质量状态不能为空");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("设定值必须是有限值");
        }
    }
}
