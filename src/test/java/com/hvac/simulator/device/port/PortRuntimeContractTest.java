package com.hvac.simulator.device.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hvac.simulator.energy.EnergyType;
import com.hvac.simulator.energy.runtime.ElectricalPortValue;
import com.hvac.simulator.energy.runtime.FluidMedium;
import com.hvac.simulator.energy.runtime.FluidProperties;
import com.hvac.simulator.energy.runtime.ModeSignalValue;
import com.hvac.simulator.energy.runtime.PortValue;
import com.hvac.simulator.energy.runtime.QualityStatus;
import com.hvac.simulator.energy.runtime.SetpointSignalValue;
import com.hvac.simulator.energy.runtime.StartStopCommand;
import com.hvac.simulator.energy.runtime.StartStopSignalValue;
import com.hvac.simulator.energy.runtime.SupplyStatus;
import com.hvac.simulator.energy.runtime.UnitCode;
import com.hvac.simulator.energy.runtime.UnavailablePressure;
import com.hvac.simulator.energy.runtime.WaterPortValue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortRuntimeContractTest {

    @Test
    void electricalAndWaterValuesEnforceFiniteSignRules() {
        assertThrows(IllegalArgumentException.class, () -> new ElectricalPortValue(
                -1.0, 0.0, SupplyStatus.AVAILABLE, QualityStatus.GOOD));
        var water = new WaterPortValue(
                -5.0,
                2.0,
                UnavailablePressure.INSTANCE,
                new FluidProperties(FluidMedium.WATER, 998.2, 4_180.0),
                QualityStatus.GOOD);

        assertEquals(-5.0, water.temperatureC());
        assertThrows(IllegalArgumentException.class, () -> new WaterPortValue(
                12.0,
                Double.NaN,
                UnavailablePressure.INSTANCE,
                water.fluidProperties(),
                QualityStatus.GOOD));
    }

    @Test
    void controlSignalsHaveSeparateStrongTypes() {
        PortValue setpoint = new SetpointSignalValue(7.0, UnitCode.CELSIUS, QualityStatus.GOOD);
        PortValue command = new StartStopSignalValue(StartStopCommand.START, QualityStatus.GOOD);
        PortValue mode = new ModeSignalValue("COOLING", QualityStatus.UNCERTAIN);

        assertInstanceOf(SetpointSignalValue.class, setpoint);
        assertInstanceOf(StartStopSignalValue.class, command);
        assertInstanceOf(ModeSignalValue.class, mode);
    }

    @Test
    void portDefinitionRejectsEnergyAndValueSpecMismatch() {
        assertThrows(IllegalArgumentException.class, () -> new PortDefinition(
                "power-in",
                "供电输入",
                EnergyType.ELECTRICITY,
                PortDirection.INPUT,
                WaterSide.NOT_APPLICABLE,
                PortCardinality.REQUIRED_SINGLE,
                new WaterPortValueSpec(FluidMedium.WATER)));
    }

    @Test
    void waterRuntimeSpecSupportsAllExistingWaterEnergyTypes() {
        var spec = new WaterPortValueSpec(FluidMedium.WATER);

        assertTrue(spec.supportsEnergyType(EnergyType.CHILLED_WATER));
        assertTrue(spec.supportsEnergyType(EnergyType.CONDENSER_WATER));
        assertTrue(spec.supportsEnergyType(EnergyType.HOT_WATER));
        assertFalse(spec.supportsEnergyType(EnergyType.ELECTRICITY));
        assertFalse(spec.supportsEnergyType(EnergyType.CONTROL_SIGNAL));
    }

    @Test
    void modeSpecDefensivelyCopiesAllowedModes() {
        var modes = new ArrayList<>(List.of("COOLING", "HEATING"));
        var spec = new ModeSignalSpec(modes);
        modes.clear();

        assertEquals(List.of("COOLING", "HEATING"), spec.allowedModes());
        assertThrows(UnsupportedOperationException.class, () -> spec.allowedModes().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new ModeSignalSpec(List.of("COOLING", "COOLING")));
    }
}
