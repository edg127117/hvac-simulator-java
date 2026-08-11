package com.hvac.simulator.energy.runtime;

import java.util.Objects;

public record ElectricalPortValue(
        double activePowerKw,
        double energyKwh,
        SupplyStatus supplyStatus,
        QualityStatus quality) implements PortValue {
    public ElectricalPortValue {
        Objects.requireNonNull(supplyStatus, "供电状态不能为空");
        Objects.requireNonNull(quality, "质量状态不能为空");
        if (!Double.isFinite(activePowerKw) || activePowerKw < 0.0
                || !Double.isFinite(energyKwh) || energyKwh < 0.0) {
            throw new IllegalArgumentException("有功功率和电能必须是有限非负值");
        }
    }
}
