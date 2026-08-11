package com.hvac.simulator.device.runtime;

import com.hvac.simulator.device.DeviceDefinition;
import com.hvac.simulator.device.port.ModeSignalSpec;
import com.hvac.simulator.device.port.PortDefinition;
import com.hvac.simulator.device.port.PortDirection;
import com.hvac.simulator.device.port.SetpointSignalSpec;
import com.hvac.simulator.device.port.WaterPortValueSpec;
import com.hvac.simulator.energy.runtime.ModeSignalValue;
import com.hvac.simulator.energy.runtime.PortValue;
import com.hvac.simulator.energy.runtime.SetpointSignalValue;
import com.hvac.simulator.energy.runtime.WaterPortValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 校验并调用一个设备的一次计算。这里只处理设备边界，不传播拓扑数据，
 * 也不执行闭环迭代、质量/能量守恒或水力求解。
 */
public final class SingleDeviceStepExecutor {
    public <S extends DeviceState> CalculationOutcome<S> execute(
            DeviceDefinition definition,
            DeviceRuntime<S> runtime,
            DeviceCalculationInput<S> input) {
        Objects.requireNonNull(definition, "设备定义不能为空");
        Objects.requireNonNull(runtime, "设备运行时不能为空");
        Objects.requireNonNull(input, "设备计算输入不能为空");
        validateAssembly(definition, runtime, input);

        if (!definition.timeStepCapability().supports(input.context().timeStep())) {
            return failure(
                    definition,
                    input,
                    DeviceCalculationErrorCode.UNSUPPORTED_TIME_STEP,
                    "当前设备版本不支持计算步长: " + input.context().timeStep(),
                    DeviceCalculationElementType.DEVICE,
                    definition.key().deviceType(),
                    Map.of(
                            "actual", input.context().timeStep().toString(),
                            "maximum", definition.timeStepCapability().maximum().toString(),
                            "minimum", definition.timeStepCapability().minimum().toString()));
        }

        for (var port : definition.ports()) {
            if (port.direction() != PortDirection.INPUT) {
                continue;
            }
            var value = input.portInputs().get(port.id());
            if (value == null) {
                if (port.cardinality().required()) {
                    return failure(
                            definition,
                            input,
                            DeviceCalculationErrorCode.MISSING_INPUT,
                            "缺少必需输入端口: " + port.id(),
                            DeviceCalculationElementType.PORT,
                            port.id(),
                            Map.of());
                }
                continue;
            }
            var incompatibility = incompatibility(port, value);
            if (incompatibility != null) {
                return failure(
                        definition,
                        input,
                        incompatibility.code(),
                        incompatibility.message(),
                        DeviceCalculationElementType.PORT,
                        port.id(),
                        incompatibility.details());
            }
        }

        var outcome = Objects.requireNonNull(
                runtime.calculate(input), "设备运行时不能返回空结果");
        if (outcome instanceof CalculationFailure<?> failure) {
            return new CalculationFailure<>(failure.error());
        }
        @SuppressWarnings("unchecked")
        var success = (CalculationSuccess<S>) outcome;
        return new CalculationSuccess<>(normalizeResult(
                definition, runtime.stateDescriptor(), success.result()));
    }

    private static <S extends DeviceState> void validateAssembly(
            DeviceDefinition definition,
            DeviceRuntime<S> runtime,
            DeviceCalculationInput<S> input) {
        if (!definition.key().equals(runtime.moduleKey())) {
            throw new IllegalArgumentException("设备定义与运行时模块键不一致");
        }
        if (!definition.key().equals(input.parameters().moduleKey())) {
            throw new IllegalArgumentException("设备定义与参数快照模块键不一致");
        }
        if (!runtime.stateDescriptor().accepts(input.previousState())) {
            throw new IllegalArgumentException("上一步状态类型与设备运行时不一致");
        }
        for (var portId : input.portInputs().keySet()) {
            var port = definition.findPort(portId)
                    .orElseThrow(() -> new IllegalArgumentException("未知输入端口: " + portId));
            if (port.direction() != PortDirection.INPUT) {
                throw new IllegalArgumentException("输出端口不能作为设备输入: " + portId);
            }
        }
    }

    private static Incompatibility incompatibility(PortDefinition port, PortValue value) {
        if (!port.valueSpec().valueType().isInstance(value)) {
            return new Incompatibility(
                    DeviceCalculationErrorCode.INCOMPATIBLE_VALUE_TYPE,
                    "端口值类型与静态定义不兼容: " + port.id(),
                    Map.of(
                            "actual", value.getClass().getSimpleName(),
                            "expected", port.valueSpec().valueType().getSimpleName()));
        }
        if (port.valueSpec() instanceof SetpointSignalSpec spec) {
            var signal = (SetpointSignalValue) value;
            if (signal.unit() != spec.unit()) {
                return new Incompatibility(
                        DeviceCalculationErrorCode.INCOMPATIBLE_UNIT,
                        "设定值单位与端口定义不兼容: " + port.id(),
                        Map.of("actual", signal.unit().name(), "expected", spec.unit().name()));
            }
        }
        if (port.valueSpec() instanceof WaterPortValueSpec spec) {
            var water = (WaterPortValue) value;
            if (!water.fluidProperties().medium().equals(spec.medium())) {
                return new Incompatibility(
                        DeviceCalculationErrorCode.INCOMPATIBLE_MEDIUM,
                        "水介质与端口定义不兼容: " + port.id(),
                        Map.of(
                                "actual", water.fluidProperties().medium().code(),
                                "expected", spec.medium().code()));
            }
        }
        if (port.valueSpec() instanceof ModeSignalSpec spec) {
            var signal = (ModeSignalValue) value;
            if (!spec.allowedModes().contains(signal.modeCode())) {
                return new Incompatibility(
                        DeviceCalculationErrorCode.INCOMPATIBLE_VALUE_TYPE,
                        "模式值不在端口允许范围内: " + port.id(),
                        Map.of(
                                "actual", signal.modeCode(),
                                "expected", String.join(",", spec.allowedModes())));
            }
        }
        return null;
    }

    private static <S extends DeviceState> DeviceCalculationResult<S> normalizeResult(
            DeviceDefinition definition,
            DeviceStateDescriptor<S> stateDescriptor,
            DeviceCalculationResult<S> result) {
        if (!stateDescriptor.accepts(result.nextState())) {
            throw new IllegalStateException("设备运行时返回了不兼容的下一状态");
        }
        for (var outputId : result.portOutputs().keySet()) {
            var port = definition.findPort(outputId)
                    .orElseThrow(() -> new IllegalStateException("运行时返回未知端口: " + outputId));
            if (port.direction() != PortDirection.OUTPUT) {
                throw new IllegalStateException("运行时把输入端口作为输出返回: " + outputId);
            }
        }

        var orderedOutputs = new LinkedHashMap<String, PortValue>();
        for (var port : definition.ports()) {
            if (port.direction() != PortDirection.OUTPUT) {
                continue;
            }
            var value = result.portOutputs().get(port.id());
            if (value == null) {
                throw new IllegalStateException("运行时缺少声明的输出端口: " + port.id());
            }
            var incompatibility = incompatibility(port, value);
            if (incompatibility != null) {
                throw new IllegalStateException(incompatibility.message());
            }
            orderedOutputs.put(port.id(), value);
        }

        var orderedMetrics = new LinkedHashMap<String, DeviceMetricValue>();
        new TreeMap<>(result.metrics()).forEach(orderedMetrics::put);
        return new DeviceCalculationResult<>(orderedOutputs, orderedMetrics, result.nextState());
    }

    private static <S extends DeviceState> CalculationFailure<S> failure(
            DeviceDefinition definition,
            DeviceCalculationInput<S> input,
            DeviceCalculationErrorCode code,
            String message,
            DeviceCalculationElementType elementType,
            String elementCode,
            Map<String, String> details) {
        return new CalculationFailure<>(new DeviceCalculationError(
                code,
                message,
                definition.key(),
                input.context().simulationTime(),
                elementType,
                elementCode,
                details));
    }

    private record Incompatibility(
            DeviceCalculationErrorCode code,
            String message,
            Map<String, String> details) {
    }
}
