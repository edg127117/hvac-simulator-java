package com.hvac.simulator.device.parameter;

public record IntegerRange(long minimum, long maximum) implements ParameterConstraint {
    public IntegerRange {
        if (minimum > maximum) {
            throw new IllegalArgumentException("整数参数范围必须是有效闭区间");
        }
    }

    @Override
    public ParameterType supportedType() {
        return ParameterType.INTEGER;
    }

    @Override
    public boolean accepts(ParameterValue value) {
        return value instanceof IntegerParameterValue integer
                && integer.value() >= minimum
                && integer.value() <= maximum;
    }
}
