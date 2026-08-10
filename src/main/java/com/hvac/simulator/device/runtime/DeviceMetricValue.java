package com.hvac.simulator.device.runtime;

import com.hvac.simulator.energy.runtime.QualityStatus;
import java.util.Objects;

public record DeviceMetricValue(String code, MetricScalar value, QualityStatus quality) {
    public DeviceMetricValue {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("指标编码不能为空");
        }
        Objects.requireNonNull(value, "指标值不能为空");
        Objects.requireNonNull(quality, "指标质量状态不能为空");
    }
}
