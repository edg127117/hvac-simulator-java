package com.hvac.simulator.weather;

import java.util.List;
import java.util.Objects;

/** 不可变且严格递增的气象时间序列。 */
public record WeatherSeries(List<WeatherPoint> points) {

    public WeatherSeries {
        Objects.requireNonNull(points, "气象序列不能为空");
        points = List.copyOf(points);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("气象序列不能为空");
        }
        for (int index = 1; index < points.size(); index++) {
            if (!points.get(index).timestamp().isAfter(points.get(index - 1).timestamp())) {
                throw new IllegalArgumentException("气象时间必须严格递增，索引=" + index);
            }
        }
    }
}
