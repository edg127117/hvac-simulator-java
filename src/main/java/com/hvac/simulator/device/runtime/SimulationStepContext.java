package com.hvac.simulator.device.runtime;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;

/** 单步计算的显式时间上下文；时间戳与步长不得从系统时钟隐式获取。 */
public record SimulationStepContext(ZonedDateTime simulationTime, Duration timeStep) {
    public SimulationStepContext {
        Objects.requireNonNull(simulationTime, "模拟时间不能为空");
        Objects.requireNonNull(timeStep, "计算步长不能为空");
        if (timeStep.isZero() || timeStep.isNegative()) {
            throw new IllegalArgumentException("计算步长必须为正数");
        }
    }
}
