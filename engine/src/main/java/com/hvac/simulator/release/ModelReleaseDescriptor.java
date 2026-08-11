package com.hvac.simulator.release;

import java.util.List;
import java.util.Objects;

/** 一个可运行 Gaia 版本及其隔离参数目录。 */
public record ModelReleaseDescriptor(
        ModelVersion version,
        String displayName,
        int outputFieldCount,
        List<ModelParameterDescriptor> parameters) {
    public ModelReleaseDescriptor {
        Objects.requireNonNull(version, "模型版本不能为空");
        Objects.requireNonNull(displayName, "模型名称不能为空");
        parameters = List.copyOf(parameters);
    }
}
