package com.hvac.simulator.energy.runtime;

import java.util.Objects;

/** 保存本时间步设备计算使用的介质物性，不负责按温度或配比推导物性。 */
public record FluidProperties(
        FluidMedium medium,
        double densityKgPerCubicMeter,
        double specificHeatJPerKgK) {

    public FluidProperties {
        Objects.requireNonNull(medium, "介质编码不能为空");
        if (!Double.isFinite(densityKgPerCubicMeter)
                || densityKgPerCubicMeter <= 0.0
                || !Double.isFinite(specificHeatJPerKgK)
                || specificHeatJPerKgK <= 0.0) {
            throw new IllegalArgumentException("介质密度和比热容必须是有限正值");
        }
    }
}
