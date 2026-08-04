package com.hvac.simulator.config;

/** 冷机、水系统、冷却塔、末端和控制参数的不可变集合。 */
public record HvacParameters(
        double chillerRatedCapacityKw,
        double chillerRatedCop,
        double chilledWaterSupplyC,
        double chilledWaterReturnC,
        double coolingWaterSupplyC,
        double coolingWaterReturnC,
        double plrCurveA,
        double plrCurveB,
        double plrCurveC,
        PipeNetwork chilledWaterPipe,
        PipeNetwork coolingWaterPipe,
        PumpParameters chilledWaterPump,
        PumpParameters coolingWaterPump,
        CoolingTowerParameters coolingTower,
        TerminalParameters terminal,
        double coolingSetpointC,
        double heatingSetpointC,
        double deadbandC) {

    public static HvacParameters gaiaDefaults() {
        return new HvacParameters(
                1_400.0, 6.0, 7.0, 12.0, 32.0, 37.0,
                0.2, 0.8, 0.0,
                new PipeNetwork(0.207, 200.0, 0.03, 0.034),
                new PipeNetwork(0.257, 200.0, 0.03, 0.034),
                new PumpParameters(200.0, 28.0, 0.75, 0.9),
                new PumpParameters(220.0, 25.0, 0.78, 0.9),
                new CoolingTowerParameters(220.0, 7.5, 4.0),
                new TerminalParameters(200, 0.15, 0.05, 0.2, 30.0),
                25.0, 20.0, 2.0);
    }

    /** 管网热损失计算只使用内径、长度和保温层参数。 */
    public record PipeNetwork(
            double innerDiameterM,
            double lengthM,
            double insulationThicknessM,
            double insulationConductivityWPerMK) {}

    /** 水泵额定流量单位为 m3/h，其余量沿用 Gaia 参数。 */
    public record PumpParameters(
            double ratedFlowM3PerHour,
            double ratedHeadM,
            double ratedEfficiency,
            double motorEfficiency) {}

    public record CoolingTowerParameters(
            double ratedFlowM3PerHour,
            double ratedFanPowerKw,
            double ratedApproachC) {}

    /** FCU 水流和压降在 Gaia 1.0 主链中未参与计算，仍保留以便追溯。 */
    public record TerminalParameters(
            int count,
            double ratedAirFlowM3PerSecond,
            double ratedFanPowerKw,
            double ratedWaterFlowLitersPerSecond,
            double ratedPressureDropKpa) {}
}
