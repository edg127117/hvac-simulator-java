package com.hvac.simulator.device;

import com.hvac.simulator.device.port.PortDefinition;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 独立设备模块的静态公开契约；公式、状态和运行时值由后续执行契约承载。 */
public record DeviceDefinition(
        DeviceModuleKey key,
        String displayName,
        List<PortDefinition> ports,
        TimeStepCapability timeStepCapability) {

    public DeviceDefinition {
        Objects.requireNonNull(key, "设备模块键不能为空");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("设备名称不能为空");
        }
        Objects.requireNonNull(ports, "设备端口不能为空");
        ports = List.copyOf(ports);
        if (ports.isEmpty()) {
            throw new IllegalArgumentException("设备至少需要一个端口");
        }
        var ids = new HashSet<String>();
        for (var port : ports) {
            Objects.requireNonNull(port, "设备端口不能包含空值");
            if (!ids.add(port.id())) {
                throw new IllegalArgumentException("设备端口编号重复: " + port.id());
            }
        }
        Objects.requireNonNull(timeStepCapability, "设备时间步能力不能为空");
    }

    public Optional<PortDefinition> findPort(String portId) {
        if (portId == null || portId.isBlank()) {
            return Optional.empty();
        }
        return ports.stream().filter(port -> port.id().equals(portId)).findFirst();
    }
}
