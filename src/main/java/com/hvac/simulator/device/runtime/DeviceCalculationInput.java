package com.hvac.simulator.device.runtime;

import com.hvac.simulator.device.parameter.ParameterSnapshot;
import com.hvac.simulator.energy.runtime.PortValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 单个设备一次计算所需的完整输入快照。 */
public record DeviceCalculationInput<S extends DeviceState>(
        SimulationStepContext context,
        Map<String, PortValue> portInputs,
        ParameterSnapshot parameters,
        S previousState) {
    public DeviceCalculationInput {
        Objects.requireNonNull(context, "计算上下文不能为空");
        portInputs = immutablePortValues(portInputs);
        Objects.requireNonNull(parameters, "参数快照不能为空");
        Objects.requireNonNull(previousState, "上一步设备状态不能为空");
    }

    private static Map<String, PortValue> immutablePortValues(Map<String, PortValue> values) {
        Objects.requireNonNull(values, "端口输入不能为空");
        var copy = new LinkedHashMap<String, PortValue>();
        for (var entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("端口输入不能包含空白编码或空值");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
