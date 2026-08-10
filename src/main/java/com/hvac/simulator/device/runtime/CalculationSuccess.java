package com.hvac.simulator.device.runtime;

import java.util.Objects;

public record CalculationSuccess<S extends DeviceState>(DeviceCalculationResult<S> result)
        implements CalculationOutcome<S> {
    public CalculationSuccess {
        Objects.requireNonNull(result, "计算结果不能为空");
    }
}
