package com.hvac.simulator.topology;

import com.hvac.simulator.device.DeviceModuleKey;
import java.util.Objects;

public record TopologyNode(String id, String displayName, DeviceModuleKey moduleKey) {
    public TopologyNode {
        requireText(id, "拓扑节点编号不能为空");
        requireText(displayName, "拓扑节点名称不能为空");
        Objects.requireNonNull(moduleKey, "拓扑节点设备模块不能为空");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
