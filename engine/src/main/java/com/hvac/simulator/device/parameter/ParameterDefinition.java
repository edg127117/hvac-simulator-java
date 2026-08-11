package com.hvac.simulator.device.parameter;

import com.hvac.simulator.energy.runtime.UnitCode;
import java.util.Objects;

public record ParameterDefinition(
        String code,
        String displayName,
        ParameterType type,
        ParameterValue defaultValue,
        UnitCode unit,
        ParameterConstraint constraint,
        ParameterUsage usage) {
    public ParameterDefinition {
        requireText(code, "参数编码不能为空");
        requireText(displayName, "参数名称不能为空");
        Objects.requireNonNull(type, "参数类型不能为空");
        Objects.requireNonNull(defaultValue, "参数默认值不能为空");
        Objects.requireNonNull(unit, "参数单位不能为空");
        Objects.requireNonNull(constraint, "参数约束不能为空");
        Objects.requireNonNull(usage, "参数生效状态不能为空");
        if (defaultValue.type() != type || defaultValue.unit() != unit) {
            throw new IllegalArgumentException("参数默认值类型或单位与定义不一致: " + code);
        }
        if (constraint.supportedType() != type || !constraint.accepts(defaultValue)) {
            throw new IllegalArgumentException("参数默认值不满足定义约束: " + code);
        }
    }

    public boolean modifiable() {
        return usage.modifiable();
    }

    public boolean usedInCalculation() {
        return usage.usedInCalculation();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
