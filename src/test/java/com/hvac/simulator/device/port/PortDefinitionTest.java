package com.hvac.simulator.device.port;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hvac.simulator.energy.EnergyType;
import org.junit.jupiter.api.Test;

class PortDefinitionTest {

    @Test
    void waterPortRequiresSupplyOrReturnSide() {
        assertThrows(IllegalArgumentException.class, () -> new PortDefinition(
                "chw-out",
                "冷冻水出水",
                EnergyType.CHILLED_WATER,
                PortDirection.OUTPUT,
                WaterSide.NOT_APPLICABLE,
                PortCardinality.REQUIRED_SINGLE));
    }

    @Test
    void nonWaterPortRejectsWaterSide() {
        assertThrows(IllegalArgumentException.class, () -> new PortDefinition(
                "power-in",
                "供电输入",
                EnergyType.ELECTRICITY,
                PortDirection.INPUT,
                WaterSide.SUPPLY,
                PortCardinality.REQUIRED_SINGLE));
    }

    @Test
    void cardinalityExpressesRequiredAndMultipleConnections() {
        assertTrue(PortCardinality.REQUIRED_SINGLE.accepts(1));
        assertFalse(PortCardinality.REQUIRED_SINGLE.accepts(0));
        assertFalse(PortCardinality.REQUIRED_SINGLE.accepts(2));
        assertTrue(PortCardinality.OPTIONAL_MULTIPLE.accepts(0));
        assertTrue(PortCardinality.OPTIONAL_MULTIPLE.accepts(3));
    }
}
