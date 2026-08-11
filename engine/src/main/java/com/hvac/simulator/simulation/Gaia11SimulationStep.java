package com.hvac.simulator.simulation;

import java.time.LocalDateTime;

/** 与 Gaia 1.1 CSV 三层语义和 30 字段顺序一一对应的单步结果。 */
public record Gaia11SimulationStep(
        LocalDateTime timestamp,
        double outdoorC,
        double wetBulbC,
        double solarGlobalWPerM2,
        double roomC,
        double coolingLoadKw,
        double chillerPowerKw,
        double chilledWaterPumpPowerKw,
        double coolingWaterPumpPowerKw,
        double coolingTowerFanPowerKw,
        double terminalFanPowerKw,
        double totalPowerKw,
        double chillerPlr,
        double chillerCop,
        double chilledWaterFlowM3PerSecond,
        double coolingWaterFlowM3PerSecond,
        double chilledWaterSupplyC,
        double chilledWaterReturnC,
        double coolingWaterSupplyC,
        double coolingWaterReturnC,
        double pipeHeatGainKw,
        double chilledWaterFlowSensorM3PerSecond,
        double coolingWaterFlowSensorM3PerSecond,
        double chilledWaterSupplySensorC,
        double chilledWaterReturnSensorC,
        double coolingWaterSupplySensorC,
        double coolingWaterReturnSensorC,
        double chillerPowerTrueKw,
        double measuredCoolingKw,
        double measuredCop) {

    public static final String CSV_HEADER = String.join(",",
            "datetime", "T_outdoor", "T_wb", "solar", "T_room", "cooling_load_kW",
            "chiller_power_kW", "chw_pump_power_kW", "cw_pump_power_kW", "ct_fan_power_kW",
            "terminal_fan_power_kW", "total_power_kW", "chiller_PLR", "chiller_COP",
            "chw_flow_rate", "cw_flow_rate", "T_chw_supply", "T_chw_return", "T_cw_supply",
            "T_cw_return", "pipe_heat_gain_kW", "chw_flow_sensor", "cw_flow_sensor",
            "T_chw_supply_sensor", "T_chw_return_sensor", "T_cw_supply_sensor",
            "T_cw_return_sensor", "chiller_power_true_kW", "measured_cooling_kW", "measured_COP");
}
