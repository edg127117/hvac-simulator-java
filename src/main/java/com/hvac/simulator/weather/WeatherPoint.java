package com.hvac.simulator.weather;

import java.time.LocalDateTime;
import java.util.Objects;

/** 单个时刻的气象输入；温度单位为摄氏度，辐射单位为 W/m2。 */
public record WeatherPoint(
        LocalDateTime timestamp,
        double dryBulbC,
        double wetBulbC,
        double solarGlobalWPerM2) {

    public WeatherPoint {
        Objects.requireNonNull(timestamp, "气象时间不能为空");
        if (!Double.isFinite(dryBulbC)
                || !Double.isFinite(wetBulbC)
                || !Double.isFinite(solarGlobalWPerM2)) {
            throw new IllegalArgumentException("气象数值必须是有限值");
        }
    }
}
