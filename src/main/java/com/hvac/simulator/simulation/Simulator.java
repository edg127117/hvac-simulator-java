package com.hvac.simulator.simulation;

import com.hvac.simulator.config.SimulationConfig;
import com.hvac.simulator.model.BuildingThermalModel;
import com.hvac.simulator.model.HvacStepResult;
import com.hvac.simulator.model.HvacSystem;
import com.hvac.simulator.weather.WeatherPoint;
import com.hvac.simulator.weather.WeatherSeries;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Objects;

/**
 * 逐分钟仿真编排器：气象输入决定建筑净得热，温控逻辑给出供冷需求，HVAC 计算设备功率，
 * 最后由建筑显式欧拉积分更新并记录室温。计算顺序严格保持 Gaia 1.0。
 */
public final class Simulator {

    private static final double INITIAL_ROOM_C = 25.0;
    private static final double INITIAL_CHILLED_WATER_RETURN_C = 12.0;

    private final SimulationConfig config;
    private final BuildingThermalModel building;
    private final HvacSystem hvac;

    public Simulator(SimulationConfig config, BuildingThermalModel building, HvacSystem hvac) {
        this.config = Objects.requireNonNull(config, "仿真配置不能为空");
        this.building = Objects.requireNonNull(building, "建筑模型不能为空");
        this.hvac = Objects.requireNonNull(hvac, "HVAC 模型不能为空");
    }

    public SimulationResult run(WeatherSeries weather) {
        Objects.requireNonNull(weather, "气象序列不能为空");
        if (weather.points().size() != config.expectedSteps()) {
            throw new IllegalArgumentException("气象点数与仿真步数不一致");
        }

        var steps = new ArrayList<SimulationStep>(config.expectedSteps());
        double roomC = INITIAL_ROOM_C;
        double previousChilledWaterReturnC = INITIAL_CHILLED_WATER_RETURN_C;
        double dtSeconds = config.dtSeconds();

        for (int index = 0; index < weather.points().size(); index++) {
            WeatherPoint point = weather.points().get(index);
            var expectedTimestamp = config.start().plusMinutes((long) index * config.dtMinutes());
            if (!point.timestamp().equals(expectedTimestamp)) {
                throw new IllegalArgumentException("气象时间与仿真时间不一致，索引=" + index);
            }
            double hour = point.timestamp().getHour() + point.timestamp().getMinute() / 60.0;
            boolean weekday = isWeekday(point.timestamp().getDayOfWeek());
            double sensibleSupplyW = coolingDemand(
                    roomC, point.dryBulbC(), point.solarGlobalWPerM2(), hour, weekday, dtSeconds);
            HvacStepResult hvacResult = hvac.simulate(
                    sensibleSupplyW, roomC, point.dryBulbC(), point.wetBulbC(), dtSeconds,
                    previousChilledWaterReturnC);

            roomC = building.step(
                    roomC, point.dryBulbC(), point.solarGlobalWPerM2(), hour, weekday,
                    sensibleSupplyW, dtSeconds);
            steps.add(new SimulationStep(
                    point.timestamp(), point.dryBulbC(), point.wetBulbC(), point.solarGlobalWPerM2(),
                    roomC, sensibleSupplyW < 0.0 ? -sensibleSupplyW / 1_000.0 : 0.0,
                    hvacResult.chillerPowerKw(), hvacResult.chilledWaterPumpPowerKw(),
                    hvacResult.coolingWaterPumpPowerKw(), hvacResult.coolingTowerFanPowerKw(),
                    hvacResult.terminalFanPowerKw(), hvacResult.systemTotalPowerKw(),
                    hvacResult.chillerPlr(), hvacResult.chillerCop(),
                    hvacResult.chilledWaterSupplyC(), hvacResult.coolingWaterSupplyC(),
                    hvacResult.pipeHeatGainKw()));
            previousChilledWaterReturnC = hvacResult.chilledWaterReturnC();
        }
        return new SimulationResult(steps);
    }

    /**
     * 室温严格大于设定点加半死区时才启动；供冷为负，并限制在冷机额定制冷量以内。
     */
    private double coolingDemand(
            double roomC,
            double outdoorC,
            double solarGlobalWPerM2,
            double hour,
            boolean weekday,
            double dtSeconds) {
        var parameters = hvac.parameters();
        if (roomC <= parameters.coolingSetpointC() + parameters.deadbandC() / 2.0) {
            return 0.0;
        }
        double netGainW = building.netSensibleGainWithoutHvac(
                roomC, outdoorC, solarGlobalWPerM2, hour, weekday);
        double supplyW = -netGainW
                - building.thermalCapacityJPerK() * (roomC - parameters.coolingSetpointC()) / dtSeconds;
        return Math.max(supplyW, -parameters.chillerRatedCapacityKw() * 1_000.0);
    }

    private boolean isWeekday(DayOfWeek dayOfWeek) {
        return dayOfWeek.getValue() <= DayOfWeek.FRIDAY.getValue();
    }
}
