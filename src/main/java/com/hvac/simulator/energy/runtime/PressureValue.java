package com.hvac.simulator.energy.runtime;

public sealed interface PressureValue permits KnownPressure, UnavailablePressure {}
