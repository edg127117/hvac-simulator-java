package com.hvac.simulator.simulation;

import com.hvac.simulator.config.SimulationConfig;
import com.hvac.simulator.measurement.Gaia11DerivedMeasurements;
import com.hvac.simulator.measurement.Gaia11MeasuredValues;
import com.hvac.simulator.measurement.Gaia11MeasurementModel;
import com.hvac.simulator.measurement.RandomDrawSource;
import com.hvac.simulator.model.BuildingThermalModel;
import com.hvac.simulator.model.HvacStepResult;
import com.hvac.simulator.model.HvacSystem;
import com.hvac.simulator.weather.WeatherPoint;
import com.hvac.simulator.weather.WeatherSeries;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Objects;

/** 按物理层、测量层、测量派生层的固定顺序执行 Gaia 1.1。 */
public final class Gaia11Simulator {
    private static final double INITIAL_ROOM_C = 25.0;
    private static final double INITIAL_CHILLED_WATER_RETURN_C = 12.0;
    private final SimulationConfig config;
    private final BuildingThermalModel building;
    private final HvacSystem hvac;
    private final Gaia11MeasurementModel measurement;
    private final RandomDrawSource random;

    public Gaia11Simulator(
            SimulationConfig config,
            BuildingThermalModel building,
            HvacSystem hvac,
            Gaia11MeasurementModel measurement,
            RandomDrawSource random) {
        this.config = Objects.requireNonNull(config, "仿真配置不能为空");
        this.building = Objects.requireNonNull(building, "建筑模型不能为空");
        this.hvac = Objects.requireNonNull(hvac, "HVAC 模型不能为空");
        this.measurement = Objects.requireNonNull(measurement, "测量模型不能为空");
        this.random = Objects.requireNonNull(random, "随机源不能为空");
    }

    public Gaia11SimulationResult run(WeatherSeries weather) {
        return run(weather, SimulationProgressListener.NOOP);
    }

    public Gaia11SimulationResult run(WeatherSeries weather, SimulationProgressListener progress) {
        Objects.requireNonNull(weather, "气象序列不能为空");
        Objects.requireNonNull(progress, "进度监听器不能为空");
        if (weather.points().size() != config.expectedSteps()) {
            throw new IllegalArgumentException("气象点数与仿真步数不一致");
        }
        var steps = new ArrayList<Gaia11SimulationStep>(config.expectedSteps());
        double roomC = INITIAL_ROOM_C;
        double previousChilledWaterReturnC = INITIAL_CHILLED_WATER_RETURN_C;

        for (int index = 0; index < weather.points().size(); index++) {
            WeatherPoint point = weather.points().get(index);
            var expectedTimestamp = config.start().plusMinutes((long) index * config.dtMinutes());
            if (!point.timestamp().equals(expectedTimestamp)) {
                throw new IllegalArgumentException("气象时间与仿真时间不一致，索引=" + index);
            }
            double hour = point.timestamp().getHour() + point.timestamp().getMinute() / 60.0;
            boolean weekday = point.timestamp().getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue();
            double supplyW = coolingDemand(
                    roomC, point.dryBulbC(), point.solarGlobalWPerM2(), hour, weekday);
            HvacStepResult physical = hvac.simulate(
                    supplyW, roomC, point.dryBulbC(), point.wetBulbC(), config.dtSeconds(),
                    previousChilledWaterReturnC);

            roomC = building.step(
                    roomC, point.dryBulbC(), point.solarGlobalWPerM2(), hour, weekday,
                    supplyW, config.dtSeconds());
            var draws = random.draws(index, point.timestamp(), physical.chillerPowerKw() > 0.0);
            Gaia11MeasuredValues measured = measurement.measure(physical, draws);
            Gaia11DerivedMeasurements derived = measurement.derive(measured);
            double measuredTotalPower = measured.chillerPowerKw()
                    + physical.chilledWaterPumpPowerKw() + physical.coolingWaterPumpPowerKw()
                    + physical.coolingTowerFanPowerKw() + physical.terminalFanPowerKw();

            steps.add(new Gaia11SimulationStep(
                    point.timestamp(), point.dryBulbC(), point.wetBulbC(), point.solarGlobalWPerM2(),
                    roomC, supplyW < 0.0 ? -supplyW / 1_000.0 : 0.0,
                    measured.chillerPowerKw(), physical.chilledWaterPumpPowerKw(),
                    physical.coolingWaterPumpPowerKw(), physical.coolingTowerFanPowerKw(),
                    physical.terminalFanPowerKw(), measuredTotalPower,
                    physical.chillerPlr(), physical.chillerCop(),
                    physical.chilledWaterFlowM3PerSecond(), physical.coolingWaterFlowM3PerSecond(),
                    physical.chilledWaterSupplyC(), physical.chilledWaterReturnC(),
                    physical.coolingWaterSupplyC(), physical.coolingWaterReturnC(),
                    physical.pipeHeatGainKw(), measured.chilledWaterFlowM3PerSecond(),
                    measured.coolingWaterFlowM3PerSecond(), measured.chilledWaterSupplyC(),
                    measured.chilledWaterReturnC(), measured.coolingWaterSupplyC(),
                    measured.coolingWaterReturnC(), physical.chillerPowerKw(),
                    derived.coolingKw(), derived.cop()));
            previousChilledWaterReturnC = physical.chilledWaterReturnC();
            progress.onProgress(index + 1, config.expectedSteps(), point.timestamp());
        }
        return new Gaia11SimulationResult(steps);
    }

    private double coolingDemand(
            double roomC, double outdoorC, double solarWPerM2, double hour,
            boolean weekday) {
        var parameters = hvac.parameters();
        if (roomC <= parameters.coolingSetpointC() + parameters.deadbandC() / 2.0) {
            return 0.0;
        }
        double netGainW = building.netSensibleGainWithoutHvac(
                roomC, outdoorC, solarWPerM2, hour, weekday);
        double supplyW = -netGainW
                - building.thermalCapacityJPerK() * (roomC - parameters.coolingSetpointC())
                / config.dtSeconds();
        return Math.max(supplyW, -parameters.chillerRatedCapacityKw() * 1_000.0);
    }
}
