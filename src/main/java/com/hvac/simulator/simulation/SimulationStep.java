package com.hvac.simulator.simulation;

import java.time.LocalDateTime;

/** 与 Gaia CSV 17 个字段一一对应的单分钟结果。 */
public record SimulationStep(
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
        double chilledWaterSupplyC,
        double coolingWaterSupplyC,
        double pipeHeatGainKw) {

    public static final String CSV_HEADER = String.join(",",
            "datetime", "T_outdoor", "T_wb", "solar", "T_room", "cooling_load_kW",
            "chiller_power_kW", "chw_pump_power_kW", "cw_pump_power_kW", "ct_fan_power_kW",
            "terminal_fan_power_kW", "total_power_kW", "chiller_PLR", "chiller_COP",
            "T_chw_supply", "T_cw_supply", "pipe_heat_gain_kW");
}
