package com.hvac.simulator.device.runtime;

import com.hvac.simulator.device.DeviceModuleKey;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** 可预期计算失败的稳定数据对象，不以异常替代业务错误。 */
public record DeviceCalculationError(
        DeviceCalculationErrorCode code,
        String message,
        DeviceModuleKey deviceKey,
        ZonedDateTime simulationTime,
        DeviceCalculationElementType elementType,
        String elementCode,
        Map<String, String> details) {
    public DeviceCalculationError {
        Objects.requireNonNull(code, "错误编码不能为空");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("错误消息不能为空");
        }
        Objects.requireNonNull(deviceKey, "设备模块键不能为空");
        Objects.requireNonNull(simulationTime, "模拟时间不能为空");
        Objects.requireNonNull(elementType, "错误元素类型不能为空");
        if (elementCode == null || elementCode.isBlank()) {
            throw new IllegalArgumentException("错误元素编码不能为空");
        }
        details = immutableSortedDetails(details);
    }

    public String title() {
        return code.title();
    }

    private static Map<String, String> immutableSortedDetails(Map<String, String> values) {
        Objects.requireNonNull(values, "错误详情不能为空");
        var sorted = new TreeMap<String, String>();
        for (var entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null) {
                throw new IllegalArgumentException("错误详情不能包含空白编码或空值");
            }
            sorted.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}
