package com.hvac.simulator.model;

/** 一分钟 HVAC 设备状态，功率单位 kW、温度单位 ℃、水流量单位 m3/s。 */
public record HvacStepResult(
        double chillerPowerKw,
        double chilledWaterPumpPowerKw,
        double coolingWaterPumpPowerKw,
        double coolingTowerFanPowerKw,
        double terminalFanPowerKw,
        double systemTotalPowerKw,
        double chilledWaterSupplyC,
        double chilledWaterReturnC,
        double coolingWaterSupplyC,
        double coolingWaterReturnC,
        double chillerPlr,
        double chillerCop,
        double chilledWaterFlowM3PerSecond,
        double coolingWaterFlowM3PerSecond,
        double pipeHeatGainKw,
        double pipeTemperatureChangeC) {}
