package com.hvac.simulator.device.port;

import com.hvac.simulator.energy.EnergyType;
import com.hvac.simulator.energy.runtime.PortValue;

public sealed interface PortValueSpec permits ElectricalPortValueSpec, WaterPortValueSpec,
        SetpointSignalSpec, StartStopSignalSpec, ModeSignalSpec {
    Class<? extends PortValue> valueType();

    boolean supportsEnergyType(EnergyType energyType);
}
