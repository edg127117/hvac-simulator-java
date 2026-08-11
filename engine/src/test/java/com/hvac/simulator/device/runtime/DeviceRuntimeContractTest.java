package com.hvac.simulator.device.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hvac.simulator.device.DeviceDefinition;
import com.hvac.simulator.device.DeviceModuleKey;
import com.hvac.simulator.device.TimeStepCapability;
import com.hvac.simulator.device.parameter.ParameterSnapshot;
import com.hvac.simulator.device.port.ElectricalPortValueSpec;
import com.hvac.simulator.device.port.PortCardinality;
import com.hvac.simulator.device.port.PortDefinition;
import com.hvac.simulator.device.port.PortDirection;
import com.hvac.simulator.device.port.WaterSide;
import com.hvac.simulator.energy.EnergyType;
import com.hvac.simulator.energy.runtime.ElectricalPortValue;
import com.hvac.simulator.energy.runtime.PortValue;
import com.hvac.simulator.energy.runtime.QualityStatus;
import com.hvac.simulator.energy.runtime.SupplyStatus;
import com.hvac.simulator.energy.runtime.UnitCode;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeviceRuntimeContractTest {
    private static final DeviceModuleKey KEY = new DeviceModuleKey("TEST_DEVICE", "1.0");

    @Test
    void calculationInputDefensivelyCopiesValues() {
        var values = new LinkedHashMap<String, PortValue>();
        values.put("power-in", electrical());
        var input = new DeviceCalculationInput<>(
                context(),
                values,
                ParameterSnapshot.fromDefaults(definition()),
                StatelessDeviceState.INSTANCE);
        values.clear();

        assertEquals(List.of("power-in"), List.copyOf(input.portInputs().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> input.portInputs().clear());
    }

    @Test
    void stateDescriptorUsesStableCodeAndPositiveSchemaVersion() {
        var descriptor = new DeviceStateDescriptor<>(
                "STATELESS", 1, StatelessDeviceState.class);

        assertEquals(true, descriptor.accepts(StatelessDeviceState.INSTANCE));
        assertThrows(IllegalArgumentException.class,
                () -> new DeviceStateDescriptor<>("STATELESS", 0, StatelessDeviceState.class));
    }

    @Test
    void errorProvidesStableCodeChineseTitleAndOrderedDetails() {
        var error = new DeviceCalculationError(
                DeviceCalculationErrorCode.MISSING_INPUT,
                "缺少必需输入端口：power-in",
                KEY,
                context().simulationTime(),
                DeviceCalculationElementType.PORT,
                "power-in",
                Map.of("z", "last", "a", "first"));

        assertEquals("缺少输入", error.title());
        assertEquals(List.of("a", "z"), List.copyOf(error.details().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> error.details().clear());
    }

    @Test
    void resultDefensivelyCopiesOutputsAndSupportsTypedMetrics() {
        var outputs = new LinkedHashMap<String, PortValue>();
        outputs.put("power-out", electrical());
        var metrics = new LinkedHashMap<String, DeviceMetricValue>();
        metrics.put("enabled", new DeviceMetricValue(
                "enabled", new MetricScalar.BooleanValue(true), QualityStatus.GOOD));
        var result = new DeviceCalculationResult<>(
                outputs, metrics, StatelessDeviceState.INSTANCE);
        outputs.clear();
        metrics.clear();

        assertEquals(List.of("power-out"), List.copyOf(result.portOutputs().keySet()));
        assertEquals(UnitCode.NONE, result.metrics().get("enabled").value().unit());
    }

    private static SimulationStepContext context() {
        return new SimulationStepContext(
                ZonedDateTime.of(2024, 7, 1, 10, 0, 0, 0, ZoneId.of("Asia/Shanghai")),
                Duration.ofMinutes(1));
    }

    private static DeviceDefinition definition() {
        return new DeviceDefinition(
                KEY,
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
                        Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofMinutes(5)));
    }

    private static ElectricalPortValue electrical() {
        return new ElectricalPortValue(
                10.0, 1.0, SupplyStatus.AVAILABLE, QualityStatus.GOOD);
    }
}
