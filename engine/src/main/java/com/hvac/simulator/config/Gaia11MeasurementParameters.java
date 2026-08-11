package com.hvac.simulator.config;

/** Gaia 1.1 流量、温度和冷机电表的测量误差参数。 */
public record Gaia11MeasurementParameters(
        double flowNoiseStdPercent,
        double temperatureNoiseStdC,
        double sensorBias,
        double powerMeterAccuracyPercent) {

    public Gaia11MeasurementParameters {
        requireFiniteNonNegative(flowNoiseStdPercent, "流量噪声标准差");
        requireFiniteNonNegative(temperatureNoiseStdC, "温度噪声标准差");
        if (!Double.isFinite(sensorBias)) {
            throw new IllegalArgumentException("传感器偏差必须是有限数值");
        }
        requireFiniteNonNegative(powerMeterAccuracyPercent, "电能表精度");
    }

    public static Gaia11MeasurementParameters gaiaDefaults() {
        return new Gaia11MeasurementParameters(0.5, 0.1, 0.0, 0.5);
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + "必须是有限非负数");
        }
    }
}
