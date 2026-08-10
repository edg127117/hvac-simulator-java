package com.hvac.simulator.device.port;

import com.hvac.simulator.energy.EnergyType;
import com.hvac.simulator.energy.runtime.PortValue;
import com.hvac.simulator.energy.runtime.StartStopSignalValue;

public enum StartStopSignalSpec implements PortValueSpec {
    INSTANCE;

    @Override
    public Class<? extends PortValue> valueType() {
        return StartStopSignalValue.class;
    }

    @Override
    public boolean supportsEnergyType(EnergyType energyType) {
        return energyType == EnergyType.CONTROL_SIGNAL;
    }
}
