package com.hvac.simulator.device.parameter;

import com.hvac.simulator.energy.runtime.UnitCode;

public record BooleanParameterValue(boolean value) implements ParameterValue {
    @Override
    public ParameterType type() {
        return ParameterType.BOOLEAN;
    }

    @Override
    public UnitCode unit() {
        return UnitCode.NONE;
    }
}
