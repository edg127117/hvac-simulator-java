package com.hvac.simulator.device.parameter;

import com.hvac.simulator.device.DeviceDefinition;
import com.hvac.simulator.device.DeviceModuleKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 冻结一个设备版本的全部最终参数值；计算阶段不再合并默认值。 */
public final class ParameterSnapshot {
    private final DeviceModuleKey moduleKey;
    private final Map<String, ParameterValue> values;

    private ParameterSnapshot(DeviceModuleKey moduleKey, Map<String, ParameterValue> values) {
        this.moduleKey = Objects.requireNonNull(moduleKey, "设备模块键不能为空");
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static ParameterSnapshot fromDefaults(DeviceDefinition definition) {
        return withOverrides(definition, Map.of());
    }

    public static ParameterSnapshot withOverrides(
            DeviceDefinition definition, Map<String, ParameterValue> overrides) {
        Objects.requireNonNull(definition, "设备定义不能为空");
        validateEntries(overrides, "参数覆盖集合不能为空");
        var resolved = new LinkedHashMap<String, ParameterValue>();
        for (var parameter : definition.parameters()) {
            var override = overrides.get(parameter.code());
            if (override != null && !parameter.modifiable()) {
                throw new IllegalArgumentException("参数不允许修改: " + parameter.code());
            }
            var value = override == null ? parameter.defaultValue() : override;
            validateValue(parameter, value);
            resolved.put(parameter.code(), value);
        }
        for (var code : overrides.keySet()) {
            if (definition.findParameter(code).isEmpty()) {
                throw new IllegalArgumentException("未知参数: " + code);
            }
        }
        return new ParameterSnapshot(definition.key(), resolved);
    }

    public static ParameterSnapshot restore(
            DeviceDefinition definition, Map<String, ParameterValue> completeValues) {
        Objects.requireNonNull(definition, "设备定义不能为空");
        validateEntries(completeValues, "历史参数集合不能为空");
        if (completeValues.size() != definition.parameters().size()) {
            throw new IllegalArgumentException("历史参数快照必须包含完整参数集合");
        }
        var restored = new LinkedHashMap<String, ParameterValue>();
        for (var parameter : definition.parameters()) {
            var value = completeValues.get(parameter.code());
            if (value == null) {
                throw new IllegalArgumentException("历史参数缺失: " + parameter.code());
            }
            validateValue(parameter, value);
            if (!parameter.modifiable() && !parameter.defaultValue().equals(value)) {
                throw new IllegalArgumentException("固定参数与版本默认值不一致: " + parameter.code());
            }
            restored.put(parameter.code(), value);
        }
        return new ParameterSnapshot(definition.key(), restored);
    }

    public DeviceModuleKey moduleKey() {
        return moduleKey;
    }

    public Map<String, ParameterValue> values() {
        return values;
    }

    public ParameterValue requiredValue(String code) {
        var value = values.get(code);
        if (value == null) {
            throw new IllegalArgumentException("参数快照中不存在参数: " + code);
        }
        return value;
    }

    private static void validateEntries(Map<String, ParameterValue> values, String nullMessage) {
        Objects.requireNonNull(values, nullMessage);
        for (var entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("参数集合不能包含空白编码或空值");
            }
        }
    }

    private static void validateValue(ParameterDefinition definition, ParameterValue value) {
        if (value.type() != definition.type()) {
            throw new IllegalArgumentException("参数类型不匹配: " + definition.code());
        }
        if (value.unit() != definition.unit()) {
            throw new IllegalArgumentException("参数单位不匹配: " + definition.code());
        }
        if (!definition.constraint().accepts(value)) {
            throw new IllegalArgumentException("参数值超出允许范围: " + definition.code());
        }
    }
}
