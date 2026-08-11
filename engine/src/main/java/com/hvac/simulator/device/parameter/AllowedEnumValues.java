package com.hvac.simulator.device.parameter;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record AllowedEnumValues(List<String> values) implements ParameterConstraint {
    public AllowedEnumValues {
        Objects.requireNonNull(values, "枚举允许值不能为空");
        values = List.copyOf(values);
        if (values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("枚举允许值不能为空且不能包含空白编码");
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException("枚举允许值不能重复");
        }
    }

    @Override
    public ParameterType supportedType() {
        return ParameterType.ENUM;
    }

    @Override
    public boolean accepts(ParameterValue value) {
        return value instanceof EnumParameterValue enumValue && values.contains(enumValue.value());
    }
}
