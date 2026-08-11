package com.hvac.simulator.energy.runtime;

public record KnownPressure(double pascals) implements PressureValue {
    public KnownPressure {
        if (!Double.isFinite(pascals) || pascals < 0.0) {
            throw new IllegalArgumentException("压力必须是有限非负值，单位 Pa");
        }
    }
}
