package com.hvac.simulator.device;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 为纯 Java 引擎装配已发布设备定义；重复版本键必须在启动装配阶段失败。 */
public final class InMemoryDeviceCatalog implements DeviceCatalog {
    private final Map<DeviceModuleKey, DeviceDefinition> definitions;

    private InMemoryDeviceCatalog(Map<DeviceModuleKey, DeviceDefinition> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    public static InMemoryDeviceCatalog fromModules(Collection<? extends DeviceModule> modules) {
        Objects.requireNonNull(modules, "设备模块集合不能为空");
        var definitions = new LinkedHashMap<DeviceModuleKey, DeviceDefinition>();
        for (var module : modules) {
            Objects.requireNonNull(module, "设备模块集合不能包含空值");
            var definition = Objects.requireNonNull(module.definition(), "设备模块定义不能为空");
            if (definitions.putIfAbsent(definition.key(), definition) != null) {
                throw new IllegalArgumentException("设备模块键重复: " + definition.key());
            }
        }
        return new InMemoryDeviceCatalog(definitions);
    }

    @Override
    public Optional<DeviceDefinition> find(DeviceModuleKey key) {
        Objects.requireNonNull(key, "设备模块键不能为空");
        return Optional.ofNullable(definitions.get(key));
    }
}
