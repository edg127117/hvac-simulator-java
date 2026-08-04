package com.hvac.simulator.config;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/** 定义一次逐分钟仿真的时间范围、步长和合成气象随机种子。 */
public record SimulationConfig(
        LocalDateTime start,
        LocalDateTime end,
        int dtMinutes,
        long randomSeed) {

    public SimulationConfig {
        Objects.requireNonNull(start, "仿真开始时间不能为空");
        Objects.requireNonNull(end, "仿真结束时间不能为空");
        if (end.isBefore(start) || dtMinutes <= 0) {
            throw new IllegalArgumentException("仿真时间范围或步长无效");
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes % dtMinutes != 0) {
            throw new IllegalArgumentException("仿真时间范围必须能被步长整除");
        }
    }

    public static SimulationConfig gaiaDemo(long seed) {
        return new SimulationConfig(
                LocalDateTime.of(2024, 7, 1, 0, 0),
                LocalDateTime.of(2024, 7, 7, 23, 59),
                1,
                seed);
    }

    public int expectedSteps() {
        return Math.toIntExact(Duration.between(start, end).toMinutes() / dtMinutes + 1);
    }

    public double dtSeconds() {
        return dtMinutes * 60.0;
    }
}
