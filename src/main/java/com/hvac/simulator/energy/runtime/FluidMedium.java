package com.hvac.simulator.energy.runtime;

public record FluidMedium(String code) {
    public static final FluidMedium WATER = new FluidMedium("WATER");

    public FluidMedium {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("介质编码不能为空");
        }
    }
}
