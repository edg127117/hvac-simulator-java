package com.hvac.simulator.device.parameter;

import com.hvac.simulator.energy.runtime.UnitCode;
import java.util.Objects;

public record IntegerParameterValue(long value, UnitCode unit) implements ParameterValue {
    public IntegerParameterValue {
        Objects.requireNonNull(unit, "整数参数单位不能为空");
    }

    @Override
    public ParameterType type() {
        return ParameterType.INTEGER;
    }
}
