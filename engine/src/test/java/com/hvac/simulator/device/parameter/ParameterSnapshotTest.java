package com.hvac.simulator.device.parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hvac.simulator.device.DeviceDefinition;
import com.hvac.simulator.device.DeviceModuleKey;
import com.hvac.simulator.device.TimeStepCapability;
import com.hvac.simulator.device.port.ElectricalPortValueSpec;
import com.hvac.simulator.device.port.PortCardinality;
import com.hvac.simulator.device.port.PortDefinition;
import com.hvac.simulator.device.port.PortDirection;
import com.hvac.simulator.device.port.WaterSide;
import com.hvac.simulator.energy.EnergyType;
import com.hvac.simulator.energy.runtime.UnitCode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParameterSnapshotTest {

    @Test
    void overridesProduceCompleteDefinitionOrderedSnapshot() {
        var definition = deviceDefinition(List.of(configurable(), fixed(), unused()));

        var snapshot = ParameterSnapshot.withOverrides(definition, Map.of(
                "setpoint", new DecimalParameterValue(8.0, UnitCode.CELSIUS)));

        assertEquals(List.of("setpoint", "rated-power", "legacy-value"),
                List.copyOf(snapshot.values().keySet()));
        assertEquals(3, snapshot.values().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.values().clear());
    }

    @Test
    void fixedUnknownAndWrongUnitOverridesAreRejected() {
        var definition = deviceDefinition(List.of(configurable(), fixed(), unused()));

        assertThrows(IllegalArgumentException.class, () -> ParameterSnapshot.withOverrides(
                definition,
                Map.of("rated-power", new DecimalParameterValue(90.0, UnitCode.KILOWATT))));
        assertThrows(IllegalArgumentException.class, () -> ParameterSnapshot.withOverrides(
                definition, Map.of("missing", new BooleanParameterValue(true))));
        assertThrows(IllegalArgumentException.class, () -> ParameterSnapshot.withOverrides(
                definition,
                Map.of("setpoint", new DecimalParameterValue(8.0, UnitCode.KILOWATT))));
    }

    @Test
    void restoreRequiresCompleteSnapshotAndFixedDefaults() {
        var definition = deviceDefinition(List.of(configurable(), fixed()));

        assertThrows(IllegalArgumentException.class,
                () -> ParameterSnapshot.restore(definition, Map.of(
                        "setpoint", new DecimalParameterValue(7.0, UnitCode.CELSIUS))));
        assertThrows(IllegalArgumentException.class,
                () -> ParameterSnapshot.restore(definition, Map.of(
                        "setpoint", new DecimalParameterValue(7.0, UnitCode.CELSIUS),
                        "rated-power", new DecimalParameterValue(90.0, UnitCode.KILOWATT))));
    }

    @Test
    void snapshotsWithSameResolvedValuesHaveValueSemantics() {
        var definition = deviceDefinition(List.of(configurable(), fixed(), unused()));

        var first = ParameterSnapshot.fromDefaults(definition);
        var second = ParameterSnapshot.fromDefaults(definition);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    private static DeviceDefinition deviceDefinition(List<ParameterDefinition> parameters) {
        return new DeviceDefinition(
                new DeviceModuleKey("TEST_DEVICE", "1.0"),
                "测试设备",
                List.of(new PortDefinition(
                        "power-in",
                        "供电输入",
                        EnergyType.ELECTRICITY,
                        PortDirection.INPUT,
                        WaterSide.NOT_APPLICABLE,
                        PortCardinality.REQUIRED_SINGLE,
                        ElectricalPortValueSpec.INSTANCE)),
                new TimeStepCapability(
                        Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofMinutes(5)),
                parameters);
    }

    private static ParameterDefinition configurable() {
        return new ParameterDefinition(
                "setpoint",
                "温度设定值",
                ParameterType.DECIMAL,
                new DecimalParameterValue(7.0, UnitCode.CELSIUS),
                UnitCode.CELSIUS,
                new DecimalRange(4.0, 15.0),
                ParameterUsage.CONFIGURABLE_CALCULATION);
    }

    private static ParameterDefinition fixed() {
        return new ParameterDefinition(
                "rated-power",
                "额定功率",
                ParameterType.DECIMAL,
                new DecimalParameterValue(100.0, UnitCode.KILOWATT),
                UnitCode.KILOWATT,
                new DecimalRange(0.0, 500.0),
                ParameterUsage.FIXED_CALCULATION);
    }

    private static ParameterDefinition unused() {
        return new ParameterDefinition(
                "legacy-value",
                "当前未接入值",
                ParameterType.BOOLEAN,
                new BooleanParameterValue(false),
                UnitCode.NONE,
                NoParameterConstraint.INSTANCE,
                ParameterUsage.NOT_USED_IN_CALCULATION);
    }
}
