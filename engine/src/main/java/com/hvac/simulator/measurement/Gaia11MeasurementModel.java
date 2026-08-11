package com.hvac.simulator.measurement;

import com.hvac.simulator.config.Gaia11MeasurementParameters;
import com.hvac.simulator.model.HvacStepResult;
import java.util.Objects;

/** 忠实执行 Gaia 1.1 的物理量测量和测量派生顺序。 */
public final class Gaia11MeasurementModel {
    private static final double WATER_VOLUMETRIC_HEAT_CAPACITY = 4_180.0;
    private final Gaia11MeasurementParameters parameters;

    public Gaia11MeasurementModel(Gaia11MeasurementParameters parameters) {
        this.parameters = Objects.requireNonNull(parameters, "测量参数不能为空");
    }

    public Gaia11MeasuredValues measure(HvacStepResult physical, Gaia11RandomDraws draws) {
        Objects.requireNonNull(physical, "物理结果不能为空");
        Objects.requireNonNull(draws, "随机抽样不能为空");
        double power = measurePower(physical.chillerPowerKw(), draws.powerUniformUnit());
        return new Gaia11MeasuredValues(
                power,
                measureFlow(physical.chilledWaterFlowM3PerSecond(), draws.chilledWaterFlowNormal()),
                measureFlow(physical.coolingWaterFlowM3PerSecond(), draws.coolingWaterFlowNormal()),
                measureTemperature(physical.chilledWaterSupplyC(), draws.chilledWaterSupplyTemperatureNormal()),
                measureTemperature(physical.chilledWaterReturnC(), draws.chilledWaterReturnTemperatureNormal()),
                measureTemperature(physical.coolingWaterSupplyC(), draws.coolingWaterSupplyTemperatureNormal()),
                measureTemperature(physical.coolingWaterReturnC(), draws.coolingWaterReturnTemperatureNormal()));
    }

    public Gaia11DerivedMeasurements derive(Gaia11MeasuredValues measured) {
        Objects.requireNonNull(measured, "测量结果不能为空");
        double deltaC = measured.chilledWaterReturnC() - measured.chilledWaterSupplyC();
        double coolingKw = measured.chilledWaterFlowM3PerSecond() > 0.0 && deltaC > 0.0
                ? measured.chilledWaterFlowM3PerSecond() * WATER_VOLUMETRIC_HEAT_CAPACITY * deltaC
                : 0.0;
        double cop = measured.chillerPowerKw() > 0.0 && coolingKw > 0.0
                ? coolingKw / measured.chillerPowerKw()
                : 0.0;
        return new Gaia11DerivedMeasurements(coolingKw, cop);
    }

    private double measurePower(double truePowerKw, Double uniformUnit) {
        if (truePowerKw <= 0.0) {
            if (uniformUnit != null) {
                throw new IllegalArgumentException("冷机停机时不得提供电表随机数");
            }
            return 0.0;
        }
        if (uniformUnit == null) {
            throw new IllegalArgumentException("冷机运行时缺少电表随机数");
        }
        return Math.max(0.0, truePowerKw
                * (1.0 + uniformUnit * parameters.powerMeterAccuracyPercent() / 100.0));
    }

    private double measureFlow(double trueValue, double normal) {
        double noiseScale = trueValue == 0.0
                ? parameters.flowNoiseStdPercent()
                : parameters.flowNoiseStdPercent() / 100.0 * Math.abs(trueValue);
        return trueValue + parameters.sensorBias() + normal * noiseScale;
    }

    private double measureTemperature(double trueValue, double normal) {
        return trueValue + parameters.sensorBias() + normal * parameters.temperatureNoiseStdC();
    }
}
