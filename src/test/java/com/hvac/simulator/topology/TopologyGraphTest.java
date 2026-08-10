package com.hvac.simulator.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hvac.simulator.device.DeviceModuleKey;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TopologyGraphTest {

    @Test
    void graphDefensivelyCopiesNodesAndConnections() {
        var nodes = new ArrayList<>(List.of(node("source"), node("load")));
        var connections = new ArrayList<>(List.of(connection("line-1")));
        var graph = new TopologyGraph(nodes, connections);

        nodes.clear();
        connections.clear();

        assertEquals(2, graph.nodes().size());
        assertEquals(1, graph.connections().size());
        assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
    }

    @Test
    void graphRejectsNullCollectionElements() {
        var nodes = new ArrayList<TopologyNode>();
        nodes.add(null);

        assertThrows(IllegalArgumentException.class, () -> new TopologyGraph(nodes, List.of()));
    }

    @Test
    void endpointRejectsBlankNodeOrPort() {
        assertThrows(IllegalArgumentException.class, () -> new TopologyEndpoint(" ", "power-out"));
        assertThrows(IllegalArgumentException.class, () -> new TopologyEndpoint("source", " "));
    }

    private static TopologyNode node(String id) {
        return new TopologyNode(id, id, new DeviceModuleKey("TEST_DEVICE", "1.0"));
    }

    private static TopologyConnection connection(String id) {
        return new TopologyConnection(
                id,
                new TopologyEndpoint("source", "power-out"),
                new TopologyEndpoint("load", "power-in"));
    }
}
