package com.hvac.simulator.device.runtime;

import com.hvac.simulator.energy.runtime.PortValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 单步成功计算产生的端口输出、设备指标和下一状态。 */
public record DeviceCalculationResult<S extends DeviceState>(
        Map<String, PortValue> portOutputs,
        Map<String, DeviceMetricValue> metrics,
        S nextState) {
    public DeviceCalculationResult {
        portOutputs = immutablePortValues(portOutputs);
        metrics = immutableMetrics(metrics);
        Objects.requireNonNull(nextState, "下一设备状态不能为空");
    }

    private static Map<String, PortValue> immutablePortValues(Map<String, PortValue> values) {
        Objects.requireNonNull(values, "端口输出不能为空");
        var copy = new LinkedHashMap<String, PortValue>();
        for (var entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("端口输出不能包含空白编码或空值");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, DeviceMetricValue> immutableMetrics(
            Map<String, DeviceMetricValue> values) {
        Objects.requireNonNull(values, "指标集合不能为空");
        var copy = new LinkedHashMap<String, DeviceMetricValue>();
        for (var entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("指标集合不能包含空白编码或空值");
            }
            if (!entry.getKey().equals(entry.getValue().code())) {
                throw new IllegalArgumentException("指标键必须与指标编码一致: " + entry.getKey());
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
