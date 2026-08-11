package com.hvac.simulator.device.runtime;

import java.util.Objects;

public record CalculationFailure<S extends DeviceState>(DeviceCalculationError error)
        implements CalculationOutcome<S> {
    public CalculationFailure {
        Objects.requireNonNull(error, "计算错误不能为空");
    }
}
