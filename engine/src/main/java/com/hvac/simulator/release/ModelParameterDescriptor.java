package com.hvac.simulator.release;

import java.util.Objects;

/** 模型真实默认参数及其编辑约束。 */
public record ModelParameterDescriptor(
        String code,
        String label,
        String group,
        String unit,
        ParameterValueType valueType,
        double defaultValue,
        double minimum,
        double maximum,
        ParameterScope scope,
        boolean editable,
        String readOnlyReason) {

    public ModelParameterDescriptor {
        Objects.requireNonNull(code, "参数代码不能为空");
        Objects.requireNonNull(label, "参数名称不能为空");
        Objects.requireNonNull(group, "参数分组不能为空");
        Objects.requireNonNull(unit, "参数单位不能为空");
        Objects.requireNonNull(valueType, "参数类型不能为空");
        Objects.requireNonNull(scope, "参数归属不能为空");
        if (!Double.isFinite(defaultValue) || !Double.isFinite(minimum)
                || !Double.isFinite(maximum) || minimum > defaultValue || defaultValue > maximum) {
            throw new IllegalArgumentException("参数默认值或范围无效：" + code);
        }
        if (!editable && (readOnlyReason == null || readOnlyReason.isBlank())) {
            throw new IllegalArgumentException("只读参数必须说明原因：" + code);
        }
    }

    public double validate(double value) {
        if (!editable) {
            throw new IllegalArgumentException("参数不可修改：" + code);
        }
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException("参数超出范围：" + code);
        }
        if (valueType == ParameterValueType.INTEGER && value != Math.rint(value)) {
            throw new IllegalArgumentException("参数必须是整数：" + code);
        }
        return value;
    }
}
