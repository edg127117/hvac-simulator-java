package com.hvac.simulator.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hvac.simulator.device.port.PortCardinality;
import com.hvac.simulator.device.port.PortDefinition;
import com.hvac.simulator.device.port.PortDirection;
import com.hvac.simulator.device.port.WaterSide;
import com.hvac.simulator.energy.EnergyType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeviceContractTest {

    @Test
    void definitionRejectsDuplicatePortIds() {
        var port = electricityInput("power-in");

        assertThrows(IllegalArgumentException.class, () -> definition("CHILLER", List.of(port, port)));
    }

    @Test
    void definitionDefensivelyCopiesPortsAndFindsById() {
        var ports = new ArrayList<>(List.of(electricityInput("power-in")));
        var definition = definition("CHILLER", ports);

        ports.clear();

        assertEquals(1, definition.ports().size());
        assertTrue(definition.findPort("power-in").isPresent());
        assertTrue(definition.findPort("missing").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> definition.ports().clear());
    }

    @Test
    void timeStepCapabilityUsesClosedRange() {
        var capability = new TimeStepCapability(
                Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofMinutes(5));

        assertTrue(capability.supports(Duration.ofSeconds(1)));
        assertTrue(capability.supports(Duration.ofMinutes(5)));
        assertFalse(capability.supports(Duration.ofMillis(999)));
        assertFalse(capability.supports(Duration.ofMinutes(6)));
        assertThrows(IllegalArgumentException.class, () -> new TimeStepCapability(
                Duration.ofMinutes(2), Duration.ofMinutes(1), Duration.ofMinutes(5)));
    }

    @Test
    void catalogRejectsDuplicateModuleKeys() {
        DeviceModule first = () -> definition("CHILLER", List.of(electricityInput("power-in")));
        DeviceModule second = () -> definition("CHILLER", List.of(electricityInput("other-power-in")));

        assertThrows(IllegalArgumentException.class,
                () -> InMemoryDeviceCatalog.fromModules(List.of(first, second)));
    }

    @Test
    void catalogFindsVersionedDefinition() {
        var expected = definition("PUMP", List.of(electricityInput("power-in")));
        var catalog = InMemoryDeviceCatalog.fromModules(List.of(() -> expected));

        assertEquals(expected, catalog.find(expected.key()).orElseThrow());
        assertTrue(catalog.find(new DeviceModuleKey("PUMP", "2.0")).isEmpty());
    }

    private static DeviceDefinition definition(String type, List<PortDefinition> ports) {
        return new DeviceDefinition(
                new DeviceModuleKey(type, "1.0"),
                type,
                ports,
                new TimeStepCapability(Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofMinutes(5)));
    }

    private static PortDefinition electricityInput(String id) {
        return new PortDefinition(
                id,
                id,
                EnergyType.ELECTRICITY,
                PortDirection.INPUT,
                WaterSide.NOT_APPLICABLE,
                PortCardinality.REQUIRED_SINGLE);
    }
}
