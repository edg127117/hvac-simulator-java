package com.hvac.simulator.energy.runtime;

import java.util.Objects;

public record ModeSignalValue(String modeCode, QualityStatus quality) implements PortValue {
    public ModeSignalValue {
        if (modeCode == null || modeCode.isBlank()) {
            throw new IllegalArgumentException("模式编码不能为空");
        }
        Objects.requireNonNull(quality, "质量状态不能为空");
    }
}
