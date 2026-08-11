package com.hvac.simulator.device.parameter;

import com.hvac.simulator.energy.runtime.UnitCode;
import java.util.Objects;

public record DecimalParameterValue(double value, UnitCode unit) implements ParameterValue {
    public DecimalParameterValue {
        Objects.requireNonNull(unit, "小数参数单位不能为空");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("小数参数必须是有限值");
        }
    }

    @Override
    public ParameterType type() {
        return ParameterType.DECIMAL;
    }
}
