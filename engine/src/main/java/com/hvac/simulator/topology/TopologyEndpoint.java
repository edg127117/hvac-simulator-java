package com.hvac.simulator.topology;

public record TopologyEndpoint(String nodeId, String portId) {
    public TopologyEndpoint {
        requireText(nodeId, "端点节点编号不能为空");
        requireText(portId, "端点端口编号不能为空");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
