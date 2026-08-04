package com.hvac.simulator.weather;

import com.hvac.simulator.config.SimulationConfig;
import com.hvac.simulator.config.WeatherParameters;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

/**
 * 用 Java 随机源转换 Gaia 的合成气象公式。
 * 年周期仍从仿真首日按相对第 0 天计算，这是基准兼容假设，并非真实日历年积日。
 */
public final class SyntheticWeatherGenerator implements WeatherSource {

    private static final double SOLAR_CONSTANT_W_PER_M2 = 1367.0;
    private final WeatherParameters parameters;

    public SyntheticWeatherGenerator(WeatherParameters parameters) {
        this.parameters = Objects.requireNonNull(parameters, "气象参数不能为空");
    }

    @Override
    public WeatherSeries load(SimulationConfig config) {
        Objects.requireNonNull(config, "仿真配置不能为空");
        int count = config.expectedSteps();
        double[] dryBulb = new double[count];
        double[] wetBulb = new double[count];
        double[] solar = new double[count];
        var random = new Random(config.randomSeed());

        // 随机数按 Gaia 的三个数组阶段消费：全部干球、全部湿球、全部云量。
        for (int index = 0; index < count; index++) {
            double days = daysFromStart(index, config.dtMinutes());
            double hour = hourOfDay(index, config.dtMinutes());
            double annual = 18.0 - 12.0 * Math.cos(2.0 * Math.PI * (days - 15.0) / 365.0);
            double diurnal = 5.0 * Math.sin(2.0 * Math.PI * (hour - 14.0) / 24.0);
            dryBulb[index] = annual + diurnal + 0.8 * random.nextGaussian();
        }
        for (int index = 0; index < count; index++) {
            double days = daysFromStart(index, config.dtMinutes());
            double seasonFactor = Math.sin(2.0 * Math.PI * (days - 170.0) / 365.0) * 0.5 + 0.5;
            double wetDepression = 3.0 + 5.0 * seasonFactor;
            wetBulb[index] = dryBulb[index] - wetDepression + 0.5 * random.nextGaussian();
        }
        for (int index = 0; index < count; index++) {
            double clearSky = clearSkyGlobalRadiation(index, config.dtMinutes());
            double cloudFactor = 0.6 + 0.6 * random.nextDouble();
            solar[index] = Math.max(0.0, clearSky * cloudFactor);
        }

        var points = new ArrayList<WeatherPoint>(count);
        for (int index = 0; index < count; index++) {
            points.add(new WeatherPoint(
                    config.start().plusMinutes((long) index * config.dtMinutes()),
                    dryBulb[index], wetBulb[index], solar[index]));
        }
        return new WeatherSeries(points);
    }

    private double clearSkyGlobalRadiation(int index, int dtMinutes) {
        double days = daysFromStart(index, dtMinutes);
        double hour = hourOfDay(index, dtMinutes);
        double declinationDegrees = 23.45 * Math.sin(2.0 * Math.PI * (284.0 + days) / 365.0);
        double hourAngleDegrees = (hour - 12.0) * 15.0;
        double latitude = Math.toRadians(parameters.latitudeDegrees());
        double declination = Math.toRadians(declinationDegrees);
        double hourAngle = Math.toRadians(hourAngleDegrees);
        double sinAltitude = Math.sin(latitude) * Math.sin(declination)
                + Math.cos(latitude) * Math.cos(declination) * Math.cos(hourAngle);
        if (sinAltitude <= 0.0) {
            return 0.0;
        }
        double airMass = 1.0 / Math.min(1.0, Math.max(0.01, sinAltitude));
        double direct = SOLAR_CONSTANT_W_PER_M2 * Math.pow(0.7, airMass) * sinAltitude;
        double diffuse = 0.3 * SOLAR_CONSTANT_W_PER_M2 * sinAltitude;
        return direct + diffuse;
    }

    private static double daysFromStart(int index, int dtMinutes) {
        return (double) index * dtMinutes / (24.0 * 60.0);
    }

    private static double hourOfDay(int index, int dtMinutes) {
        return ((double) index * dtMinutes / 60.0) % 24.0;
    }
}
