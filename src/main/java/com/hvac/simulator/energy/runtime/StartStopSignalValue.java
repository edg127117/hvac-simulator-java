package com.hvac.simulator.energy.runtime;

import java.util.Objects;

public record StartStopSignalValue(
        StartStopCommand command, QualityStatus quality) implements PortValue {
    public StartStopSignalValue {
        Objects.requireNonNull(command, "启停命令不能为空");
        Objects.requireNonNull(quality, "质量状态不能为空");
    }
}
