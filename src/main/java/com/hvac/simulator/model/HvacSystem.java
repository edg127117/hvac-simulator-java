package com.hvac.simulator.model;

import com.hvac.simulator.config.HvacParameters;
import com.hvac.simulator.config.HvacParameters.PipeNetwork;
import com.hvac.simulator.config.HvacParameters.PumpParameters;
import java.util.Objects;

/**
 * 按 Gaia 1.0 的计算顺序组合冷机、水泵、冷却塔、管道和 FCU。
 * 房间供冷需求使用 W 且供冷为负，设备制冷量和电功率使用正值 kW。
 */
public final class HvacSystem {

    private static final double WATER_DENSITY_KG_PER_M3 = 1_000.0;
    private static final double WATER_SPECIFIC_HEAT_J_PER_KG_K = 4_180.0;
    private final HvacParameters parameters;

    public HvacSystem(HvacParameters parameters) {
        this.parameters = Objects.requireNonNull(parameters, "HVAC 参数不能为空");
    }

    /**
     * 冷机 COP 同时按冷却水温和 PLR 修正；PLR 强制处于 0.1~1.0。
     * dtSeconds 为忠实保留的 Gaia 入参，原公式未使用它。
     */
    public ChillerResult calculateChiller(double loadKw, double coolingWaterInletC, double dtSeconds) {
        if (loadKw <= 0.0) {
            return new ChillerResult(0.0, parameters.chilledWaterSupplyC(), 0.0, 0.0);
        }
        double plr = Math.max(0.1, Math.min(1.0, loadKw / parameters.chillerRatedCapacityKw()));
        double coolingWaterFactor = 1.0
                - 0.025 * (coolingWaterInletC - parameters.coolingWaterReturnC());
        double plrFactor = parameters.plrCurveA()
                + parameters.plrCurveB() * plr
                + parameters.plrCurveC() * plr * plr;
        double cop = parameters.chillerRatedCop() * coolingWaterFactor * plrFactor;
        if (cop <= 0.0) {
            cop = 1.0;
        }
        return new ChillerResult(loadKw / cop, parameters.chilledWaterSupplyC(), plr, cop);
    }

    /** 按恒压差简化曲线计算水泵输入功率，流量单位 m3/s，返回 kW。 */
    public double calculatePumpPower(
            double flowM3PerSecond,
            PumpParameters pump,
            boolean variableSpeed) {
        if (flowM3PerSecond <= 0.0) {
            return 0.0;
        }
        double ratedFlowM3PerSecond = pump.ratedFlowM3PerHour() / 3_600.0;
        double flowRatio = flowM3PerSecond / ratedFlowM3PerSecond;
        double headM = variableSpeed
                ? pump.ratedHeadM() * (0.3 + 0.7 * flowRatio * flowRatio)
                : pump.ratedHeadM();
        double hydraulicPowerW = WATER_DENSITY_KG_PER_M3 * 9.81 * flowM3PerSecond * headM;
        double pumpEfficiency = Math.min(
                pump.ratedEfficiency() * (0.5 + 0.5 * flowRatio), 0.85);
        double shaftPowerW = pumpEfficiency > 0.0 ? hydraulicPowerW / pumpEfficiency : 0.0;
        return shaftPowerW / pump.motorEfficiency() / 1_000.0;
    }

    /** 冷却塔逼近度和风机功率均按未限幅负荷率线性变化。 */
    public CoolingTowerResult calculateCoolingTower(double heatRejectedKw, double wetBulbC) {
        if (heatRejectedKw <= 0.0) {
            return new CoolingTowerResult(wetBulbC, 0.0);
        }
        double ratedRejectionKw = parameters.chillerRatedCapacityKw()
                * (1.0 + 1.0 / parameters.chillerRatedCop());
        double loadRatio = heatRejectedKw / ratedRejectionKw;
        double approachC = parameters.coolingTower().ratedApproachC() * (0.5 + 0.5 * loadRatio);
        double fanPowerKw = parameters.coolingTower().ratedFanPowerKw() * loadRatio;
        return new CoolingTowerResult(wetBulbC + approachC, fanPowerKw);
    }

    /**
     * 计算保温管传热，返回 W。公式为 (流体温度-环境温度)/热阻，
     * 因此 7℃ 冷水处于 28℃ 环境时结果为负；虽与变量名 heatGain 冲突，仍保留原符号。
     */
    public double calculatePipeHeatLoss(double fluidC, double ambientC, PipeNetwork pipe) {
        double innerRadiusM = pipe.innerDiameterM() / 2.0;
        double insulationRadiusM = innerRadiusM + pipe.insulationThicknessM();
        if (insulationRadiusM <= innerRadiusM) {
            return 0.0;
        }
        double conductionResistance = Math.log(insulationRadiusM / innerRadiusM)
                / (2.0 * Math.PI * pipe.insulationConductivityWPerMK() * pipe.lengthM());
        double convectionResistance = 1.0
                / (2.0 * Math.PI * insulationRadiusM * pipe.lengthM() * 10.0);
        double totalResistance = conductionResistance + convectionResistance;
        return totalResistance > 0.0 ? (fluidC - ambientC) / totalResistance : 0.0;
    }

    /** 执行单步系统计算；室温和干球温度是 Gaia 预留参数，当前公式不使用。 */
    public HvacStepResult simulate(
            double sensibleDemandW,
            double roomC,
            double outdoorC,
            double wetBulbC,
            double dtSeconds,
            double previousChilledWaterReturnC) {
        if (sensibleDemandW >= 0.0) {
            return stoppedResult();
        }

        double roomLoadKw = -sensibleDemandW / 1_000.0;
        double chilledWaterDeltaC = parameters.chilledWaterReturnC() - parameters.chilledWaterSupplyC();
        double chilledWaterFlowM3PerSecond = roomLoadKw * 1_000.0
                / (WATER_DENSITY_KG_PER_M3 * WATER_SPECIFIC_HEAT_J_PER_KG_K * chilledWaterDeltaC);

        double pipeHeatW = calculatePipeHeatLoss(
                parameters.chilledWaterSupplyC(), 28.0, parameters.chilledWaterPipe());
        double pipeHeatKw = pipeHeatW / 1_000.0;
        double totalChillerLoadKw = roomLoadKw + pipeHeatKw;

        // 先用 COP≈5 估算冷却侧温度，再用实际冷机功率重算冷却塔输出。
        double estimatedRejectionKw = totalChillerLoadKw * (1.0 + 1.0 / 5.0);
        CoolingTowerResult tower = calculateCoolingTower(estimatedRejectionKw, wetBulbC);
        ChillerResult chiller = calculateChiller(totalChillerLoadKw, tower.coolingWaterOutletC(), dtSeconds);
        double actualRejectionKw = totalChillerLoadKw + chiller.powerKw();
        tower = calculateCoolingTower(actualRejectionKw, wetBulbC);

        double chilledWaterPumpKw = calculatePumpPower(
                chilledWaterFlowM3PerSecond, parameters.chilledWaterPump(), true);
        double coolingWaterFlowM3PerSecond = chilledWaterFlowM3PerSecond * 1.1;
        double coolingWaterPumpKw = calculatePumpPower(
                coolingWaterFlowM3PerSecond, parameters.coolingWaterPump(), true);
        int terminalCount = Math.max(1, (int) (parameters.terminal().count() * chiller.plr()));
        double terminalFanKw = terminalCount * parameters.terminal().ratedFanPowerKw();
        double totalPowerKw = chiller.powerKw() + chilledWaterPumpKw + coolingWaterPumpKw
                + tower.fanPowerKw() + terminalFanKw;
        double pipeTemperatureChangeC = pipeHeatW
                / (WATER_DENSITY_KG_PER_M3 * WATER_SPECIFIC_HEAT_J_PER_KG_K
                * chilledWaterFlowM3PerSecond + 1e-6);

        // 回水温度直接沿用上一时刻，是 Gaia 兼容状态，不依据本步能量平衡更新。
        return new HvacStepResult(
                chiller.powerKw(), chilledWaterPumpKw, coolingWaterPumpKw,
                tower.fanPowerKw(), terminalFanKw, totalPowerKw,
                chiller.chilledWaterSupplyC(), previousChilledWaterReturnC,
                tower.coolingWaterOutletC(), chiller.plr(), chiller.cop(),
                chilledWaterFlowM3PerSecond, pipeHeatKw, pipeTemperatureChangeC);
    }

    private HvacStepResult stoppedResult() {
        return new HvacStepResult(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                7.0, 12.0, 32.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
