package com.hvac.simulator.device.runtime;

import java.util.Objects;

/** 用稳定编码和模式版本描述设备状态，序列化时不依赖 Java 类名。 */
public record DeviceStateDescriptor<S extends DeviceState>(
        String stateCode,
        int schemaVersion,
        Class<S> stateType) {
    public DeviceStateDescriptor {
        if (stateCode == null || stateCode.isBlank()) {
            throw new IllegalArgumentException("状态编码不能为空");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("状态模式版本必须为正整数");
        }
        Objects.requireNonNull(stateType, "状态类型不能为空");
    }

    public boolean accepts(DeviceState state) {
        return stateType.isInstance(state);
    }
}
