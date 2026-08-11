package com.hvac.simulator.device.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hvac.simulator.device.DeviceDefinition;
import com.hvac.simulator.device.DeviceModuleKey;
import com.hvac.simulator.device.TimeStepCapability;
import com.hvac.simulator.device.parameter.ParameterSnapshot;
import com.hvac.simulator.device.port.ElectricalPortValueSpec;
import com.hvac.simulator.device.port.ModeSignalSpec;
import com.hvac.simulator.device.port.PortCardinality;
import com.hvac.simulator.device.port.PortDefinition;
import com.hvac.simulator.device.port.PortDirection;
import com.hvac.simulator.device.port.SetpointSignalSpec;
import com.hvac.simulator.device.port.WaterPortValueSpec;
import com.hvac.simulator.device.port.WaterSide;
import com.hvac.simulator.energy.EnergyType;
import com.hvac.simulator.energy.runtime.ElectricalPortValue;
import com.hvac.simulator.energy.runtime.FluidMedium;
import com.hvac.simulator.energy.runtime.FluidProperties;
import com.hvac.simulator.energy.runtime.ModeSignalValue;
import com.hvac.simulator.energy.runtime.PortValue;
import com.hvac.simulator.energy.runtime.QualityStatus;
import com.hvac.simulator.energy.runtime.SetpointSignalValue;
import com.hvac.simulator.energy.runtime.SupplyStatus;
import com.hvac.simulator.energy.runtime.UnitCode;
import com.hvac.simulator.energy.runtime.UnavailablePressure;
import com.hvac.simulator.energy.runtime.WaterPortValue;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class SingleDeviceStepExecutorTest {
    private static final DeviceModuleKey KEY = new DeviceModuleKey("TEST_DEVICE", "1.0");
    private static final DeviceStateDescriptor<TestState> STATE_DESCRIPTOR =
            new DeviceStateDescriptor<>("TEST_STATE", 1, TestState.class);
    private final SingleDeviceStepExecutor executor = new SingleDeviceStepExecutor();

    @Test
    void unsupportedTimeStepTakesPriorityOverMissingInput() {
        var outcome = executor.execute(
                definition(), successfulRuntime(), input(Duration.ofMinutes(10), Map.of()));

        assertError(outcome, DeviceCalculationErrorCode.UNSUPPORTED_TIME_STEP, KEY.deviceType());
    }

    @Test
    void missingRequiredInputReturnsStableError() {
        var outcome = executor.execute(
                definition(), successfulRuntime(), input(Duration.ofMinutes(1), Map.of()));

        var error = assertError(outcome, DeviceCalculationErrorCode.MISSING_INPUT, "power-in");
        assertEquals("缺少输入", error.title());
        assertEquals(DeviceCalculationElementType.PORT, error.elementType());
    }

    @Test
    void incompatibleValueTypeIsRejectedBeforeRuntime() {
        var outcome = executor.execute(definition(), successfulRuntime(), input(Map.of(
                "power-in", new SetpointSignalValue(10.0, UnitCode.KILOWATT, QualityStatus.GOOD))));

        assertError(outcome, DeviceCalculationErrorCode.INCOMPATIBLE_VALUE_TYPE, "power-in");
    }

    @Test
    void incompatibleSetpointUnitIsRejected() {
        var outcome = executor.execute(definition(), successfulRuntime(), input(Map.of(
                "power-in", electrical(QualityStatus.GOOD),
                "setpoint-in", new SetpointSignalValue(
                        7.0, UnitCode.KILOWATT, QualityStatus.GOOD))));

        assertError(outcome, DeviceCalculationErrorCode.INCOMPATIBLE_UNIT, "setpoint-in");
    }

    @Test
    void incompatibleWaterMediumIsRejected() {
        var otherMedium = new FluidMedium("TEST_OTHER");
        var outcome = executor.execute(definition(), successfulRuntime(), input(Map.of(
                "power-in", electrical(QualityStatus.GOOD),
                "water-in", water(otherMedium))));

        assertError(outcome, DeviceCalculationErrorCode.INCOMPATIBLE_MEDIUM, "water-in");
    }

    @Test
    void unsupportedModeIsRejectedAsIncompatibleValue() {
        var outcome = executor.execute(definition(), successfulRuntime(), input(Map.of(
                "power-in", electrical(QualityStatus.GOOD),
                "mode-in", new ModeSignalValue("HEAT", QualityStatus.GOOD))));

        assertError(outcome, DeviceCalculationErrorCode.INCOMPATIBLE_VALUE_TYPE, "mode-in");
    }

    @ParameterizedTest
    @EnumSource(QualityStatus.class)
    void commonExecutorPassesEveryQualityStatusToDevice(QualityStatus quality) {
        var outcome = executor.execute(definition(), successfulRuntime(), input(Map.of(
                "power-in", electrical(quality))));

        assertInstanceOf(CalculationSuccess.class, outcome);
    }

    @ParameterizedTest
    @MethodSource("runtimeErrors")
    void expectedRuntimeErrorsPassThrough(DeviceCalculationErrorCode code) {
        var expected = error(code, "device");
        var runtime = runtime(ignored -> new CalculationFailure<>(expected));

        var outcome = executor.execute(definition(), runtime, validInput());

        var failure = assertInstanceOf(CalculationFailure.class, outcome);
        assertSame(expected, failure.error());
    }

    @Test
    void normalizesOutputsAndMetricsForDeterministicResults() {
        var runtime = runtime(ignored -> {
            var outputs = new LinkedHashMap<String, PortValue>();
            outputs.put("power-out-a", electrical(QualityStatus.GOOD));
            outputs.put("power-out-b", electrical(QualityStatus.GOOD));
            var metrics = new LinkedHashMap<String, DeviceMetricValue>();
            metrics.put("zeta", new DeviceMetricValue(
                    "zeta", new MetricScalar.IntegerValue(2, UnitCode.NONE), QualityStatus.GOOD));
            metrics.put("alpha", new DeviceMetricValue(
                    "alpha", new MetricScalar.IntegerValue(1, UnitCode.NONE), QualityStatus.GOOD));
            return new CalculationSuccess<>(new DeviceCalculationResult<>(
                    outputs, metrics, new TestState(1)));
        });

        var first = executor.execute(definition(), runtime, validInput());
        var second = executor.execute(definition(), runtime, validInput());
        var firstSuccess = assertInstanceOf(CalculationSuccess.class, first);
        @SuppressWarnings("unchecked")
        var firstResult = (DeviceCalculationResult<TestState>) firstSuccess.result();

        assertEquals(List.of("power-out-b", "power-out-a"),
                List.copyOf(firstResult.portOutputs().keySet()));
        assertEquals(List.of("alpha", "zeta"), List.copyOf(firstResult.metrics().keySet()));
        assertEquals(first, second);
    }

    @Test
    void assemblyMismatchThrowsProgrammingError() {
        var wrongRuntime = new TestRuntime(
                new DeviceModuleKey("OTHER_DEVICE", "1.0"), ignored -> success());

        assertThrows(IllegalArgumentException.class,
                () -> executor.execute(definition(), wrongRuntime, validInput()));
    }

    @Test
    void invalidRuntimeOutputThrowsProgrammingError() {
        var runtime = runtime(ignored -> new CalculationSuccess<>(new DeviceCalculationResult<>(
                Map.of("power-out-b", electrical(QualityStatus.GOOD)),
                Map.of(),
                new TestState(1))));

        assertThrows(IllegalStateException.class,
                () -> executor.execute(definition(), runtime, validInput()));
    }

    private static Stream<Arguments> runtimeErrors() {
        return Stream.of(
                Arguments.of(DeviceCalculationErrorCode.NON_FINITE_VALUE),
                Arguments.of(DeviceCalculationErrorCode.DIVISION_BY_ZERO),
                Arguments.of(DeviceCalculationErrorCode.NUMERIC_OUT_OF_RANGE));
    }

    private static DeviceDefinition definition() {
        return new DeviceDefinition(
                KEY,
                "测试设备",
                List.of(
                        port("power-in", EnergyType.ELECTRICITY, PortDirection.INPUT,
                                WaterSide.NOT_APPLICABLE, PortCardinality.REQUIRED_SINGLE,
                                ElectricalPortValueSpec.INSTANCE),
                        port("setpoint-in", EnergyType.CONTROL_SIGNAL, PortDirection.INPUT,
                                WaterSide.NOT_APPLICABLE, PortCardinality.OPTIONAL_SINGLE,
                                new SetpointSignalSpec(UnitCode.CELSIUS)),
                        port("water-in", EnergyType.CHILLED_WATER, PortDirection.INPUT,
                                WaterSide.SUPPLY, PortCardinality.OPTIONAL_SINGLE,
                                new WaterPortValueSpec(FluidMedium.WATER)),
                        port("mode-in", EnergyType.CONTROL_SIGNAL, PortDirection.INPUT,
                                WaterSide.NOT_APPLICABLE, PortCardinality.OPTIONAL_SINGLE,
                                new ModeSignalSpec(List.of("COOL", "OFF"))),
                        port("power-out-b", EnergyType.ELECTRICITY, PortDirection.OUTPUT,
                                WaterSide.NOT_APPLICABLE, PortCardinality.REQUIRED_SINGLE,
                                ElectricalPortValueSpec.INSTANCE),
                        port("power-out-a", EnergyType.ELECTRICITY, PortDirection.OUTPUT,
                                WaterSide.NOT_APPLICABLE, PortCardinality.REQUIRED_SINGLE,
                                ElectricalPortValueSpec.INSTANCE)),
                new TimeStepCapability(
                        Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofMinutes(5)));
    }

    private static PortDefinition port(
            String id,
            EnergyType energyType,
            PortDirection direction,
            WaterSide waterSide,
            PortCardinality cardinality,
            com.hvac.simulator.device.port.PortValueSpec valueSpec) {
        return new PortDefinition(id, id, energyType, direction, waterSide, cardinality, valueSpec);
    }

    private static DeviceCalculationInput<TestState> validInput() {
        return input(Map.of("power-in", electrical(QualityStatus.GOOD)));
    }

    private static DeviceCalculationInput<TestState> input(Map<String, PortValue> values) {
        return input(Duration.ofMinutes(1), values);
    }

    private static DeviceCalculationInput<TestState> input(
            Duration timeStep, Map<String, PortValue> values) {
        return new DeviceCalculationInput<>(
                new SimulationStepContext(
                        ZonedDateTime.of(
                                2024, 7, 1, 10, 0, 0, 0, ZoneId.of("Asia/Shanghai")),
                        timeStep),
                values,
                ParameterSnapshot.fromDefaults(definition()),
                new TestState(0));
    }

    private static DeviceRuntime<TestState> successfulRuntime() {
        return runtime(ignored -> success());
    }

    private static DeviceRuntime<TestState> runtime(
            Function<DeviceCalculationInput<TestState>, CalculationOutcome<TestState>> calculation) {
        return new TestRuntime(KEY, calculation);
    }

    private static CalculationSuccess<TestState> success() {
        return new CalculationSuccess<>(new DeviceCalculationResult<>(
                Map.of(
                        "power-out-a", electrical(QualityStatus.GOOD),
                        "power-out-b", electrical(QualityStatus.GOOD)),
                Map.of(),
                new TestState(1)));
    }

    private static ElectricalPortValue electrical(QualityStatus quality) {
        return new ElectricalPortValue(10.0, 1.0, SupplyStatus.AVAILABLE, quality);
    }

    private static WaterPortValue water(FluidMedium medium) {
        return new WaterPortValue(
                7.0,
                1.0,
                UnavailablePressure.INSTANCE,
                new FluidProperties(medium, 997.0, 4180.0),
                QualityStatus.GOOD);
    }

    private static DeviceCalculationError error(
            DeviceCalculationErrorCode code, String elementCode) {
        return new DeviceCalculationError(
                code,
                code.title(),
                KEY,
                ZonedDateTime.of(2024, 7, 1, 10, 0, 0, 0, ZoneId.of("Asia/Shanghai")),
                DeviceCalculationElementType.DEVICE,
                elementCode,
                Map.of());
    }

    private static DeviceCalculationError assertError(
            CalculationOutcome<TestState> outcome,
            DeviceCalculationErrorCode code,
            String elementCode) {
        var failure = assertInstanceOf(CalculationFailure.class, outcome);
        assertEquals(code, failure.error().code());
        assertEquals(elementCode, failure.error().elementCode());
        return failure.error();
    }

    private record TestState(int completedSteps) implements DeviceState {
    }

    private record TestRuntime(
            DeviceModuleKey moduleKey,
            Function<DeviceCalculationInput<TestState>, CalculationOutcome<TestState>> calculation)
            implements DeviceRuntime<TestState> {
        @Override
        public DeviceStateDescriptor<TestState> stateDescriptor() {
            return STATE_DESCRIPTOR;
        }

        @Override
        public CalculationOutcome<TestState> calculate(DeviceCalculationInput<TestState> input) {
            return calculation.apply(input);
        }
    }
}
