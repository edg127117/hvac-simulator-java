package com.hvac.simulator.model;

/** 冷机单次计算结果：功率 kW、供水温度 ℃、部分负荷率和 COP。 */
public record ChillerResult(double powerKw, double chilledWaterSupplyC, double plr, double cop) {}
