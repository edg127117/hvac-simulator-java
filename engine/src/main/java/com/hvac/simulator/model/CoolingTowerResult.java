package com.hvac.simulator.model;

/** 冷却塔出口水温 ℃ 和风机功率 kW。 */
public record CoolingTowerResult(double coolingWaterOutletC, double fanPowerKw) {}
