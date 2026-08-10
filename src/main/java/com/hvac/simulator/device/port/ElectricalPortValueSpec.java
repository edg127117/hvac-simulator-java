package com.hvac.simulator.device.port;

import com.hvac.simulator.energy.EnergyType;
import com.hvac.simulator.energy.runtime.ElectricalPortValue;
import com.hvac.simulator.energy.runtime.PortValue;

public enum ElectricalPortValueSpec implements PortValueSpec {
    INSTANCE;

    @Override
    public Class<? extends PortValue> valueType() {
        return ElectricalPortValue.class;
    }

    @Override
    public boolean supportsEnergyType(EnergyType energyType) {
        return energyType == EnergyType.ELECTRICITY;
    }
}
