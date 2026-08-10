package com.hvac.simulator.device.parameter;

import com.hvac.simulator.energy.runtime.UnitCode;

public record EnumParameterValue(String value) implements ParameterValue {
    public EnumParameterValue {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("枚举参数编码不能为空");
        }
    }

    @Override
    public ParameterType type() {
        return ParameterType.ENUM;
    }

    @Override
    public UnitCode unit() {
        return UnitCode.NONE;
    }
}
