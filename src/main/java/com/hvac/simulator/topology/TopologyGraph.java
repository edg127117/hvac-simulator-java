package com.hvac.simulator.topology;

import java.util.List;
import java.util.Objects;

/** 保存用户方案的原始节点和连线；跨对象错误由校验器一次性汇总。 */
public record TopologyGraph(List<TopologyNode> nodes, List<TopologyConnection> connections) {
    public TopologyGraph {
        Objects.requireNonNull(nodes, "拓扑节点集合不能为空");
        Objects.requireNonNull(connections, "拓扑连线集合不能为空");
        if (nodes.stream().anyMatch(Objects::isNull) || connections.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("拓扑集合不能包含空值");
        }
        nodes = List.copyOf(nodes);
        connections = List.copyOf(connections);
    }
}
