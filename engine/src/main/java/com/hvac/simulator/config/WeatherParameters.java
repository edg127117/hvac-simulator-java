package com.hvac.simulator.config;

/** Gaia 合成气象使用的地理和夏季设计参数；设计温度仅作为基准元数据保留。 */
public record WeatherParameters(
        double latitudeDegrees,
        double longitudeDegrees,
        double designDryBulbC,
        double designWetBulbC) {

    public static WeatherParameters gaiaDefaults() {
        return new WeatherParameters(30.5, 114.3, 35.0, 28.0);
    }
}
