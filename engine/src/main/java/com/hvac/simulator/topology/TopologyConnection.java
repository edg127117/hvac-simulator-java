package com.hvac.simulator.topology;

import java.util.Objects;

public record TopologyConnection(String id, TopologyEndpoint source, TopologyEndpoint target) {
    public TopologyConnection {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("拓扑连线编号不能为空");
        }
        Objects.requireNonNull(source, "连线起点不能为空");
        Objects.requireNonNull(target, "连线终点不能为空");
    }
}
