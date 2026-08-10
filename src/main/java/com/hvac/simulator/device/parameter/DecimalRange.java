package com.hvac.simulator.device.parameter;

public record DecimalRange(double minimum, double maximum) implements ParameterConstraint {
    public DecimalRange {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("小数参数范围必须是有限闭区间");
        }
    }

    @Override
    public ParameterType supportedType() {
        return ParameterType.DECIMAL;
    }

    @Override
    public boolean accepts(ParameterValue value) {
        return value instanceof DecimalParameterValue decimal
                && decimal.value() >= minimum
                && decimal.value() <= maximum;
    }
}
