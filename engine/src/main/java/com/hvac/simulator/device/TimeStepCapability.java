package com.hvac.simulator.device;

import java.time.Duration;
import java.util.Objects;

/** 声明设备可接受的统一任务计算步长；本类型不负责推进设备状态。 */
public record TimeStepCapability(Duration minimum, Duration defaultValue, Duration maximum) {
    public TimeStepCapability {
        Objects.requireNonNull(minimum, "最小计算步长不能为空");
        Objects.requireNonNull(defaultValue, "默认计算步长不能为空");
        Objects.requireNonNull(maximum, "最大计算步长不能为空");
        if (minimum.isZero()
                || minimum.isNegative()
                || defaultValue.compareTo(minimum) < 0
                || maximum.compareTo(defaultValue) < 0) {
            throw new IllegalArgumentException("计算步长必须满足 0 < 最小值 <= 默认值 <= 最大值");
        }
    }

    public boolean supports(Duration candidate) {
        Objects.requireNonNull(candidate, "待检查计算步长不能为空");
        return !candidate.isNegative()
                && !candidate.isZero()
                && candidate.compareTo(minimum) >= 0
                && candidate.compareTo(maximum) <= 0;
    }
}
