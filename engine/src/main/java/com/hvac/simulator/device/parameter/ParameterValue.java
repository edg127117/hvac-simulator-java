package com.hvac.simulator.device.parameter;

import com.hvac.simulator.energy.runtime.UnitCode;

public sealed interface ParameterValue permits DecimalParameterValue,
        IntegerParameterValue, BooleanParameterValue, EnumParameterValue {
    ParameterType type();

    UnitCode unit();
}
