package com.hvac.simulator.device.port;

import com.hvac.simulator.energy.EnergyType;
import com.hvac.simulator.energy.runtime.PortValue;
import com.hvac.simulator.energy.runtime.SetpointSignalValue;
import com.hvac.simulator.energy.runtime.UnitCode;
import java.util.Objects;

public record SetpointSignalSpec(UnitCode unit) implements PortValueSpec {
    public SetpointSignalSpec {
        Objects.requireNonNull(unit, "设定值单位不能为空");
    }

    @Override
    public Class<? extends PortValue> valueType() {
        return SetpointSignalValue.class;
    }

    @Override
    public boolean supportsEnergyType(EnergyType energyType) {
        return energyType == EnergyType.CONTROL_SIGNAL;
    }
}
