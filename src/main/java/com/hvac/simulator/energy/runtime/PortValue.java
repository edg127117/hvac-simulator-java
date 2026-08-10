package com.hvac.simulator.energy.runtime;

public sealed interface PortValue permits ElectricalPortValue, WaterPortValue,
        SetpointSignalValue, StartStopSignalValue, ModeSignalValue {
    QualityStatus quality();
}
