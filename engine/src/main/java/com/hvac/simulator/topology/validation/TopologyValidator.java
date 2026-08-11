package com.hvac.simulator.topology.validation;

import com.hvac.simulator.device.DeviceCatalog;
import com.hvac.simulator.device.DeviceDefinition;
import com.hvac.simulator.device.port.PortDefinition;
import com.hvac.simulator.device.port.PortDirection;
import com.hvac.simulator.topology.TopologyConnection;
import com.hvac.simulator.topology.TopologyEndpoint;
import com.hvac.simulator.topology.TopologyGraph;
import com.hvac.simulator.topology.TopologyNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 校验画布连线和启动前拓扑的结构契约；闭环、守恒、边界流量与收敛由后续求解器负责。
 */
public final class TopologyValidator {
    private final DeviceCatalog deviceCatalog;

    public TopologyValidator(DeviceCatalog deviceCatalog) {
        this.deviceCatalog = Objects.requireNonNull(deviceCatalog, "设备目录不能为空");
    }

    public TopologyValidationResult validateConnection(
            TopologyGraph graph, TopologyConnection candidate) {
        Objects.requireNonNull(graph, "待校验拓扑不能为空");
        Objects.requireNonNull(candidate, "候选连线不能为空");
        var issues = new ArrayList<TopologyIssue>();

        if (graph.connections().stream().anyMatch(connection -> connection.id().equals(candidate.id()))) {
            addError(
                    issues,
                    TopologyIssueCode.DUPLICATE_CONNECTION_ID,
                    "连线编号已存在: " + candidate.id(),
                    candidate.id());
        }
        if (graph.connections().stream().anyMatch(connection -> sameEndpoints(connection, candidate))) {
            addError(
                    issues,
                    TopologyIssueCode.DUPLICATE_CONNECTION_ENDPOINTS,
                    "相同起止端点已经存在连线",
                    candidate.id());
        }
        if (candidate.source().nodeId().equals(candidate.target().nodeId())) {
            addError(
                    issues,
                    TopologyIssueCode.SELF_CONNECTION,
                    "同一设备节点不能连接自身",
                    candidate.id());
        }

        var nodes = indexFirstNodes(graph.nodes());
        var source = resolveCandidateEndpoint(nodes, candidate.source(), true, candidate.id(), issues);
        var target = resolveCandidateEndpoint(nodes, candidate.target(), false, candidate.id(), issues);
        if (source.isPresent()) {
            validateDirection(source.orElseThrow().port(), true, candidate.id(), issues);
            validateCardinality(graph, candidate.source(), source.orElseThrow().port(), true, candidate.id(), issues);
        }
        if (target.isPresent()) {
            validateDirection(target.orElseThrow().port(), false, candidate.id(), issues);
            validateCardinality(graph, candidate.target(), target.orElseThrow().port(), false, candidate.id(), issues);
        }
        if (source.isPresent() && target.isPresent()) {
            validateCompatibility(
                    source.orElseThrow().port(), target.orElseThrow().port(), candidate.id(), issues);
        }
        return new TopologyValidationResult(issues);
    }

    public TopologyValidationResult validateGraph(TopologyGraph graph) {
        Objects.requireNonNull(graph, "待校验拓扑不能为空");
        var issues = new ArrayList<TopologyIssue>();
        if (graph.nodes().isEmpty()) {
            addError(
                    issues,
                    TopologyIssueCode.EMPTY_TOPOLOGY,
                    "拓扑至少需要一个设备节点",
                    "topology");
        }
        var nodes = indexNodesAndReportDuplicates(graph.nodes(), issues);
        reportDuplicateConnections(graph.connections(), issues);

        var definitions = resolveDefinitions(nodes, issues);
        for (var connection : graph.connections()) {
            if (connection.source().nodeId().equals(connection.target().nodeId())) {
                addError(
                        issues,
                        TopologyIssueCode.SELF_CONNECTION,
                        "同一设备节点不能连接自身",
                        connection.id());
            }
            var source = resolveGraphEndpoint(
                    nodes, definitions, connection.source(), true, connection.id(), issues);
            var target = resolveGraphEndpoint(
                    nodes, definitions, connection.target(), false, connection.id(), issues);
            source.ifPresent(resolved -> validateDirection(resolved.port(), true, connection.id(), issues));
            target.ifPresent(resolved -> validateDirection(resolved.port(), false, connection.id(), issues));
            if (source.isPresent() && target.isPresent()) {
                validateCompatibility(
                        source.orElseThrow().port(),
                        target.orElseThrow().port(),
                        connection.id(),
                        issues);
            }
        }

        var counts = countResolvedPortReferences(graph.connections(), nodes, definitions);
        validatePortCounts(nodes, definitions, counts, issues);
        reportIsolatedNodes(graph, nodes, issues);
        return new TopologyValidationResult(issues);
    }

    private Optional<ResolvedEndpoint> resolveCandidateEndpoint(
            Map<String, TopologyNode> nodes,
            TopologyEndpoint endpoint,
            boolean source,
            String elementId,
            List<TopologyIssue> issues) {
        var node = nodes.get(endpoint.nodeId());
        if (node == null) {
            addError(
                    issues,
                    source ? TopologyIssueCode.UNKNOWN_SOURCE_NODE : TopologyIssueCode.UNKNOWN_TARGET_NODE,
                    (source ? "起点" : "终点") + "节点不存在: " + endpoint.nodeId(),
                    elementId);
            return Optional.empty();
        }
        var definition = deviceCatalog.find(node.moduleKey());
        if (definition.isEmpty()) {
            addError(
                    issues,
                    TopologyIssueCode.UNKNOWN_DEVICE_MODULE,
                    "节点引用的设备模块不存在: " + node.moduleKey(),
                    elementId);
            return Optional.empty();
        }
        return resolvePort(node, definition.orElseThrow(), endpoint, source, elementId, issues);
    }

    private Optional<ResolvedEndpoint> resolveGraphEndpoint(
            Map<String, TopologyNode> nodes,
            Map<String, DeviceDefinition> definitions,
            TopologyEndpoint endpoint,
            boolean source,
            String elementId,
            List<TopologyIssue> issues) {
        var node = nodes.get(endpoint.nodeId());
        if (node == null) {
            addError(
                    issues,
                    source ? TopologyIssueCode.UNKNOWN_SOURCE_NODE : TopologyIssueCode.UNKNOWN_TARGET_NODE,
                    (source ? "起点" : "终点") + "节点不存在: " + endpoint.nodeId(),
                    elementId);
            return Optional.empty();
        }
        var definition = definitions.get(node.id());
        if (definition == null) {
            return Optional.empty();
        }
        return resolvePort(node, definition, endpoint, source, elementId, issues);
    }

    private static Optional<ResolvedEndpoint> resolvePort(
            TopologyNode node,
            DeviceDefinition definition,
            TopologyEndpoint endpoint,
            boolean source,
            String elementId,
            List<TopologyIssue> issues) {
        var port = definition.findPort(endpoint.portId());
        if (port.isEmpty()) {
            addError(
                    issues,
                    source ? TopologyIssueCode.UNKNOWN_SOURCE_PORT : TopologyIssueCode.UNKNOWN_TARGET_PORT,
                    (source ? "起点" : "终点") + "端口不存在: "
                            + endpoint.nodeId() + ":" + endpoint.portId(),
                    elementId);
            return Optional.empty();
        }
        return Optional.of(new ResolvedEndpoint(node, port.orElseThrow()));
    }

    private static void validateDirection(
            PortDefinition port,
            boolean source,
            String elementId,
            List<TopologyIssue> issues) {
        var expected = source ? PortDirection.OUTPUT : PortDirection.INPUT;
        if (port.direction() != expected) {
            addError(
                    issues,
                    TopologyIssueCode.INVALID_DIRECTION,
                    (source ? "起点端口必须是输出端口: " : "终点端口必须是输入端口: ") + port.id(),
                    elementId);
        }
    }

    private static void validateCompatibility(
            PortDefinition source,
            PortDefinition target,
            String elementId,
            List<TopologyIssue> issues) {
        if (source.energyType() != target.energyType()) {
            addError(
                    issues,
                    TopologyIssueCode.INCOMPATIBLE_ENERGY_TYPE,
                    "端口能源类型不一致: " + source.energyType() + " -> " + target.energyType(),
                    elementId);
            return;
        }
        if (source.energyType().isWater() && source.waterSide() != target.waterSide()) {
            addError(
                    issues,
                    TopologyIssueCode.INCOMPATIBLE_WATER_SIDE,
                    "水端口供回水侧不一致: " + source.waterSide() + " -> " + target.waterSide(),
                    elementId);
        }
    }

    private static void validateCardinality(
            TopologyGraph graph,
            TopologyEndpoint endpoint,
            PortDefinition port,
            boolean source,
            String elementId,
            List<TopologyIssue> issues) {
        int connectionCount = countReferences(graph.connections(), endpoint) + 1;
        if (!port.cardinality().accepts(connectionCount)) {
            addError(
                    issues,
                    source ? TopologyIssueCode.SOURCE_PORT_OCCUPIED : TopologyIssueCode.TARGET_PORT_OCCUPIED,
                    "端口连接数量超过声明限制: " + endpoint.nodeId() + ":" + endpoint.portId(),
                    elementId);
        }
    }

    private static Map<String, TopologyNode> indexFirstNodes(List<TopologyNode> nodes) {
        var index = new LinkedHashMap<String, TopologyNode>();
        nodes.forEach(node -> index.putIfAbsent(node.id(), node));
        return index;
    }

    private static Map<String, TopologyNode> indexNodesAndReportDuplicates(
            List<TopologyNode> nodes, List<TopologyIssue> issues) {
        var index = new LinkedHashMap<String, TopologyNode>();
        for (var node : nodes) {
            if (index.putIfAbsent(node.id(), node) != null) {
                addError(
                        issues,
                        TopologyIssueCode.DUPLICATE_NODE_ID,
                        "拓扑节点编号重复: " + node.id(),
                        node.id());
            }
        }
        return index;
    }

    private static void reportDuplicateConnections(
            List<TopologyConnection> connections, List<TopologyIssue> issues) {
        var ids = new HashSet<String>();
        var endpoints = new HashSet<ConnectionEndpoints>();
        for (var connection : connections) {
            if (!ids.add(connection.id())) {
                addError(
                        issues,
                        TopologyIssueCode.DUPLICATE_CONNECTION_ID,
                        "拓扑连线编号重复: " + connection.id(),
                        connection.id());
            }
            if (!endpoints.add(new ConnectionEndpoints(connection.source(), connection.target()))) {
                addError(
                        issues,
                        TopologyIssueCode.DUPLICATE_CONNECTION_ENDPOINTS,
                        "相同起止端点存在重复连线",
                        connection.id());
            }
        }
    }

    private Map<String, DeviceDefinition> resolveDefinitions(
            Map<String, TopologyNode> nodes, List<TopologyIssue> issues) {
        var definitions = new LinkedHashMap<String, DeviceDefinition>();
        for (var node : nodes.values()) {
            var definition = deviceCatalog.find(node.moduleKey());
            if (definition.isEmpty()) {
                addError(
                        issues,
                        TopologyIssueCode.UNKNOWN_DEVICE_MODULE,
                        "节点引用的设备模块不存在: " + node.moduleKey(),
                        node.id());
            } else {
                definitions.put(node.id(), definition.orElseThrow());
            }
        }
        return definitions;
    }

    private static Map<TopologyEndpoint, Integer> countResolvedPortReferences(
            List<TopologyConnection> connections,
            Map<String, TopologyNode> nodes,
            Map<String, DeviceDefinition> definitions) {
        var counts = new HashMap<TopologyEndpoint, Integer>();
        for (var connection : connections) {
            countIfResolved(connection.source(), nodes, definitions, counts);
            countIfResolved(connection.target(), nodes, definitions, counts);
        }
        return counts;
    }

    private static void countIfResolved(
            TopologyEndpoint endpoint,
            Map<String, TopologyNode> nodes,
            Map<String, DeviceDefinition> definitions,
            Map<TopologyEndpoint, Integer> counts) {
        var node = nodes.get(endpoint.nodeId());
        var definition = node == null ? null : definitions.get(node.id());
        if (definition != null && definition.findPort(endpoint.portId()).isPresent()) {
            counts.merge(endpoint, 1, Integer::sum);
        }
    }

    private static void validatePortCounts(
            Map<String, TopologyNode> nodes,
            Map<String, DeviceDefinition> definitions,
            Map<TopologyEndpoint, Integer> counts,
            List<TopologyIssue> issues) {
        for (var node : nodes.values()) {
            var definition = definitions.get(node.id());
            if (definition == null) {
                continue;
            }
            for (var port : definition.ports()) {
                var endpoint = new TopologyEndpoint(node.id(), port.id());
                int count = counts.getOrDefault(endpoint, 0);
                if (count == 0 && port.cardinality().required()) {
                    addError(
                            issues,
                            TopologyIssueCode.REQUIRED_PORT_UNCONNECTED,
                            "必需端口尚未连接: " + endpoint.nodeId() + ":" + endpoint.portId(),
                            endpoint.nodeId() + ":" + endpoint.portId());
                } else if (count > 0 && !port.cardinality().accepts(count)) {
                    addError(
                            issues,
                            port.direction() == PortDirection.OUTPUT
                                    ? TopologyIssueCode.SOURCE_PORT_OCCUPIED
                                    : TopologyIssueCode.TARGET_PORT_OCCUPIED,
                            "端口连接数量超过声明限制: "
                                    + endpoint.nodeId() + ":" + endpoint.portId(),
                            endpoint.nodeId() + ":" + endpoint.portId());
                }
            }
        }
    }

    private static void reportIsolatedNodes(
            TopologyGraph graph,
            Map<String, TopologyNode> nodes,
            List<TopologyIssue> issues) {
        Set<String> referencedNodeIds = new HashSet<>();
        for (var connection : graph.connections()) {
            referencedNodeIds.add(connection.source().nodeId());
            referencedNodeIds.add(connection.target().nodeId());
        }
        for (var node : nodes.values()) {
            if (!referencedNodeIds.contains(node.id())) {
                issues.add(new TopologyIssue(
                        TopologyIssueCode.ISOLATED_NODE,
                        TopologyIssueSeverity.WARNING,
                        "节点未连接到任何端口: " + node.id(),
                        node.id()));
            }
        }
    }

    private static int countReferences(
            List<TopologyConnection> connections, TopologyEndpoint endpoint) {
        int count = 0;
        for (var connection : connections) {
            if (connection.source().equals(endpoint)) {
                count++;
            }
            if (connection.target().equals(endpoint)) {
                count++;
            }
        }
        return count;
    }

    private static boolean sameEndpoints(TopologyConnection first, TopologyConnection second) {
        return first.source().equals(second.source()) && first.target().equals(second.target());
    }

    private static void addError(
            List<TopologyIssue> issues,
            TopologyIssueCode code,
            String message,
            String elementId) {
        issues.add(new TopologyIssue(code, TopologyIssueSeverity.ERROR, message, elementId));
    }

    private record ResolvedEndpoint(TopologyNode node, PortDefinition port) {}

    private record ConnectionEndpoints(TopologyEndpoint source, TopologyEndpoint target) {}
}
