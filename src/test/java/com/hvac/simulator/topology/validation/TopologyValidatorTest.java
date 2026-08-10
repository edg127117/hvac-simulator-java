package com.hvac.simulator.topology.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hvac.simulator.device.DeviceDefinition;
import com.hvac.simulator.device.DeviceModule;
import com.hvac.simulator.device.DeviceModuleKey;
import com.hvac.simulator.device.InMemoryDeviceCatalog;
import com.hvac.simulator.device.TimeStepCapability;
import com.hvac.simulator.device.port.PortCardinality;
import com.hvac.simulator.device.port.PortDefinition;
import com.hvac.simulator.device.port.PortDirection;
import com.hvac.simulator.device.port.StartStopSignalSpec;
import com.hvac.simulator.device.port.WaterSide;
import com.hvac.simulator.energy.EnergyType;
import com.hvac.simulator.topology.TopologyConnection;
import com.hvac.simulator.topology.TopologyEndpoint;
import com.hvac.simulator.topology.TopologyGraph;
import com.hvac.simulator.topology.TopologyNode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TopologyValidatorTest {
    private static final DeviceModuleKey SOURCE_KEY = new DeviceModuleKey("POWER_SOURCE", "1.0");
    private static final DeviceModuleKey LOAD_KEY = new DeviceModuleKey("POWER_LOAD", "1.0");
    private static final DeviceModuleKey WATER_SOURCE_KEY = new DeviceModuleKey("WATER_SOURCE", "1.0");
    private static final DeviceModuleKey WATER_LOAD_KEY = new DeviceModuleKey("WATER_LOAD", "1.0");
    private static final DeviceModuleKey WATER_RETURN_LOAD_KEY =
            new DeviceModuleKey("WATER_RETURN_LOAD", "1.0");
    private static final DeviceModuleKey OPTIONAL_MONITOR_KEY =
            new DeviceModuleKey("OPTIONAL_MONITOR", "1.0");
    private static final TimeStepCapability TIME_STEP = new TimeStepCapability(
            Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofMinutes(5));

    @Test
    void validOutputToInputConnectionPasses() {
        var result = validator().validateConnection(baseGraph(), powerConnection("line-1"));

        assertTrue(result.isValid());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void connectionRejectsUnknownNodeWrongDirectionEnergyAndWaterSide() {
        assertHasCode(
                validator().validateConnection(
                        baseGraph(),
                        connection("unknown", "source", "power-out", "missing", "power-in")),
                TopologyIssueCode.UNKNOWN_TARGET_NODE);
        assertHasCode(
                validator().validateConnection(
                        baseGraph(),
                        connection("direction", "load", "power-in", "source", "power-out")),
                TopologyIssueCode.INVALID_DIRECTION);
        assertHasCode(
                validator().validateConnection(
                        graphWithWaterNodes(),
                        connection("energy", "source", "power-out", "water-load", "water-in")),
                TopologyIssueCode.INCOMPATIBLE_ENERGY_TYPE);
        assertHasCode(
                validator().validateConnection(
                        graphWithWaterNodes(),
                        connection("side", "water-source", "water-out", "water-return", "water-in")),
                TopologyIssueCode.INCOMPATIBLE_WATER_SIDE);
    }

    @Test
    void connectionRejectsUnknownPortAndSelfConnection() {
        assertHasCode(
                validator().validateConnection(
                        baseGraph(),
                        connection("unknown-port", "source", "missing", "load", "power-in")),
                TopologyIssueCode.UNKNOWN_SOURCE_PORT);
        assertHasCode(
                validator().validateConnection(
                        baseGraph(),
                        connection("self", "source", "power-out", "source", "power-out")),
                TopologyIssueCode.SELF_CONNECTION);
    }

    @Test
    void secondConnectionRejectsDuplicateEndpointsAndOccupiedSinglePorts() {
        var graph = new TopologyGraph(baseGraph().nodes(), List.of(powerConnection("existing")));
        var result = validator().validateConnection(graph, powerConnection("candidate"));

        assertHasCode(result, TopologyIssueCode.DUPLICATE_CONNECTION_ENDPOINTS);
        assertHasCode(result, TopologyIssueCode.SOURCE_PORT_OCCUPIED);
        assertHasCode(result, TopologyIssueCode.TARGET_PORT_OCCUPIED);
        assertFalse(result.isValid());
    }

    @Test
    void graphReportsDuplicateIdsAndUnknownModule() {
        var duplicate = new TopologyNode("source", "重复节点", SOURCE_KEY);
        var unknown = new TopologyNode(
                "unknown", "未知设备", new DeviceModuleKey("UNKNOWN", "1.0"));
        var graph = new TopologyGraph(
                List.of(sourceNode(), duplicate, loadNode(), unknown),
                List.of(powerConnection("same"), powerConnection("same")));

        var result = validator().validateGraph(graph);

        assertHasCode(result, TopologyIssueCode.DUPLICATE_NODE_ID);
        assertHasCode(result, TopologyIssueCode.DUPLICATE_CONNECTION_ID);
        assertHasCode(result, TopologyIssueCode.DUPLICATE_CONNECTION_ENDPOINTS);
        assertHasCode(result, TopologyIssueCode.UNKNOWN_DEVICE_MODULE);
        assertFalse(result.isValid());
    }

    @Test
    void graphReportsMissingRequiredPortAndIsolatedNode() {
        var graph = new TopologyGraph(List.of(sourceNode(), loadNode()), List.of());
        var result = validator().validateGraph(graph);

        assertHasCode(result, TopologyIssueCode.REQUIRED_PORT_UNCONNECTED);
        assertHasCode(result, TopologyIssueCode.ISOLATED_NODE);
        assertFalse(result.isValid());
        assertTrue(result.issues().stream()
                .filter(issue -> issue.code() == TopologyIssueCode.ISOLATED_NODE)
                .allMatch(issue -> issue.severity() == TopologyIssueSeverity.WARNING));
    }

    @Test
    void graphWithValidConnectionPassesBasicValidation() {
        var graph = new TopologyGraph(baseGraph().nodes(), List.of(powerConnection("line-1")));

        assertTrue(validator().validateGraph(graph).isValid());
    }

    @Test
    void emptyGraphFailsStartupValidation() {
        var result = validator().validateGraph(new TopologyGraph(List.of(), List.of()));

        assertHasCode(result, TopologyIssueCode.EMPTY_TOPOLOGY);
        assertFalse(result.isValid());
    }

    @Test
    void isolatedOptionalDeviceProducesWarningWithoutError() {
        var graph = new TopologyGraph(
                List.of(new TopologyNode("monitor", "可选监视器", OPTIONAL_MONITOR_KEY)),
                List.of());
        var result = validator().validateGraph(graph);

        assertHasCode(result, TopologyIssueCode.ISOLATED_NODE);
        assertTrue(result.isValid());
    }

    private static TopologyValidator validator() {
        List<DeviceModule> modules = List.of(
                () -> definition(SOURCE_KEY, port(
                        "power-out",
                        EnergyType.ELECTRICITY,
                        PortDirection.OUTPUT,
                        WaterSide.NOT_APPLICABLE)),
                () -> definition(LOAD_KEY, port(
                        "power-in",
                        EnergyType.ELECTRICITY,
                        PortDirection.INPUT,
                        WaterSide.NOT_APPLICABLE)),
                () -> definition(WATER_SOURCE_KEY, port(
                        "water-out",
                        EnergyType.CHILLED_WATER,
                        PortDirection.OUTPUT,
                        WaterSide.SUPPLY)),
                () -> definition(WATER_LOAD_KEY, port(
                        "water-in",
                        EnergyType.CHILLED_WATER,
                        PortDirection.INPUT,
                        WaterSide.SUPPLY)),
                () -> definition(WATER_RETURN_LOAD_KEY, port(
                        "water-in",
                        EnergyType.CHILLED_WATER,
                        PortDirection.INPUT,
                        WaterSide.RETURN)),
                () -> definition(OPTIONAL_MONITOR_KEY, new PortDefinition(
                        "signal-in",
                        "可选信号输入",
                        EnergyType.CONTROL_SIGNAL,
                        PortDirection.INPUT,
                        WaterSide.NOT_APPLICABLE,
                        PortCardinality.OPTIONAL_SINGLE,
                        StartStopSignalSpec.INSTANCE)));
        return new TopologyValidator(InMemoryDeviceCatalog.fromModules(modules));
    }

    private static DeviceDefinition definition(DeviceModuleKey key, PortDefinition port) {
        return new DeviceDefinition(key, key.deviceType(), List.of(port), TIME_STEP);
    }

    private static PortDefinition port(
            String id, EnergyType energyType, PortDirection direction, WaterSide waterSide) {
        return new PortDefinition(
                id, id, energyType, direction, waterSide, PortCardinality.REQUIRED_SINGLE);
    }

    private static TopologyGraph baseGraph() {
        return new TopologyGraph(List.of(sourceNode(), loadNode()), List.of());
    }

    private static TopologyGraph graphWithWaterNodes() {
        return new TopologyGraph(
                List.of(
                        sourceNode(),
                        loadNode(),
                        new TopologyNode("water-source", "水源", WATER_SOURCE_KEY),
                        new TopologyNode("water-load", "供水负载", WATER_LOAD_KEY),
                        new TopologyNode("water-return", "回水负载", WATER_RETURN_LOAD_KEY)),
                List.of());
    }

    private static TopologyNode sourceNode() {
        return new TopologyNode("source", "电源", SOURCE_KEY);
    }

    private static TopologyNode loadNode() {
        return new TopologyNode("load", "负载", LOAD_KEY);
    }

    private static TopologyConnection powerConnection(String id) {
        return connection(id, "source", "power-out", "load", "power-in");
    }

    private static TopologyConnection connection(
            String id, String sourceNode, String sourcePort, String targetNode, String targetPort) {
        return new TopologyConnection(
                id,
                new TopologyEndpoint(sourceNode, sourcePort),
                new TopologyEndpoint(targetNode, targetPort));
    }

    private static void assertHasCode(TopologyValidationResult result, TopologyIssueCode code) {
        assertTrue(
                result.issues().stream().anyMatch(issue -> issue.code() == code),
                () -> "未找到问题编码 " + code + "，实际问题=" + result.issues());
    }
}
