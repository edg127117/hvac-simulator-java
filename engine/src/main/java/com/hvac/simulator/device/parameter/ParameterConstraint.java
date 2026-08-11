package com.hvac.simulator.device.parameter;

public sealed interface ParameterConstraint permits DecimalRange, IntegerRange,
        AllowedEnumValues, NoParameterConstraint {
    ParameterType supportedType();

    boolean accepts(ParameterValue value);
}
