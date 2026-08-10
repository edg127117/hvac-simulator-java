package com.hvac.simulator.device.parameter;

public enum NoParameterConstraint implements ParameterConstraint {
    INSTANCE;

    @Override
    public ParameterType supportedType() {
        return ParameterType.BOOLEAN;
    }

    @Override
    public boolean accepts(ParameterValue value) {
        return value instanceof BooleanParameterValue;
    }
}
