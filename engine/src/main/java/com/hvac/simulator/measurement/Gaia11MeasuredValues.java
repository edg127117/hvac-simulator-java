package com.hvac.simulator.measurement;

/** Gaia 1.1 六个传感器和冷机电表的同步测量值。 */
public record Gaia11MeasuredValues(
        double chillerPowerKw,
        double chilledWaterFlowM3PerSecond,
        double coolingWaterFlowM3PerSecond,
        double chilledWaterSupplyC,
        double chilledWaterReturnC,
        double coolingWaterSupplyC,
        double coolingWaterReturnC) {}
