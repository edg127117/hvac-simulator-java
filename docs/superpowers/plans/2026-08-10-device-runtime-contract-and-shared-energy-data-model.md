# 设备运行时契约与公共能量数据模型 Implementation Plan

> 状态：已确认、已实施并完成本地自动化验证；交付状态以 Git 和 PR 为准。
>
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变 Gaia 1.0 行为和现有拓扑静态边界的前提下，实现强类型电、水、控制信号运行值、完整参数快照、泛型设备状态、单步计算结果、中文稳定错误和公共单设备执行器。

**Architecture:** 继续使用当前单 Maven 模块，在 `energy.runtime`、`device.parameter` 和 `device.runtime` 建立不可变纯 Java 领域对象。动态拓扑通过端口编号传值，端口规格负责类型、单位和介质匹配，`SingleDeviceStepExecutor` 只校验并调用一个设备，不传播拓扑数据或执行求解。

**Tech Stack:** Java 21、Maven Wrapper 3.3.4、Maven 3.9.16、JUnit 6.1.2、Java 标准库；不增加生产依赖。

## Global Constraints

- 本计划只处理第二子项目“设备运行时契约与公共能量数据模型”。
- 不实现任何冷机、水泵、冷却塔、管道、FCU、建筑负荷或测量模型公式。
- 不修改 Gaia 1.0 公式、参数、时间语义、输出、随机行为、冻结 CSV 和参考资产。
- 不实现 Gaia 1.1 Java 转换、随机测量、测量派生或五联图。
- 不实现拓扑编译、设备排序、端口传播、闭环迭代、守恒和完整水力求解。
- 不引入 Spring Boot、Vue、数据库、MQTT、Jackson、单位库或其他新依赖。
- 不调整 `pom.xml`、Maven/JAR/CLI 结构，不重新生成 CSV 或 PNG。
- 引擎内部只使用规范单位；不实现外部单位换算器。
- 功率、电能和质量流量为有限非负大小，方向只由 `PortDirection` 表达。
- 公共对象拒绝空值和非法组合，对集合进行有序防御性复制。
- `QualityStatus` 由具体设备解释；公共执行器不统一拒绝 `UNCERTAIN`、`BAD` 或 `NOT_AVAILABLE`。
- 预期计算问题返回 `CalculationFailure`；装配错误和设备实现缺陷抛 `IllegalArgumentException` 或 `IllegalStateException`。
- 每个任务执行 Red → Green → Refactor，并在独立提交前检查明确暂存范围。
- 生产 Java 变化必须使用 `code-comment-quality` 与仓库专项注释规则；注释重点说明单位、方向、状态、数值保护和版本边界。
- 最终必须运行本子项目定向测试、`GaiaParityTest` 和完整 `.\mvnw.cmd test`。

---

## File Map

**公共运行值**

- `src/main/java/com/hvac/simulator/energy/runtime/UnitCode.java`
- `src/main/java/com/hvac/simulator/energy/runtime/QualityStatus.java`
- `src/main/java/com/hvac/simulator/energy/runtime/SupplyStatus.java`
- `src/main/java/com/hvac/simulator/energy/runtime/FluidMedium.java`
- `src/main/java/com/hvac/simulator/energy/runtime/FluidProperties.java`
- `src/main/java/com/hvac/simulator/energy/runtime/PressureValue.java`
- `src/main/java/com/hvac/simulator/energy/runtime/KnownPressure.java`
- `src/main/java/com/hvac/simulator/energy/runtime/UnavailablePressure.java`
- `src/main/java/com/hvac/simulator/energy/runtime/PortValue.java`
- `src/main/java/com/hvac/simulator/energy/runtime/ElectricalPortValue.java`
- `src/main/java/com/hvac/simulator/energy/runtime/WaterPortValue.java`
- `src/main/java/com/hvac/simulator/energy/runtime/SetpointSignalValue.java`
- `src/main/java/com/hvac/simulator/energy/runtime/StartStopCommand.java`
- `src/main/java/com/hvac/simulator/energy/runtime/StartStopSignalValue.java`
- `src/main/java/com/hvac/simulator/energy/runtime/ModeSignalValue.java`

**端口运行值规格**

- `src/main/java/com/hvac/simulator/device/port/PortValueSpec.java`
- `src/main/java/com/hvac/simulator/device/port/ElectricalPortValueSpec.java`
- `src/main/java/com/hvac/simulator/device/port/WaterPortValueSpec.java`
- `src/main/java/com/hvac/simulator/device/port/SetpointSignalSpec.java`
- `src/main/java/com/hvac/simulator/device/port/StartStopSignalSpec.java`
- `src/main/java/com/hvac/simulator/device/port/ModeSignalSpec.java`
- Modify `src/main/java/com/hvac/simulator/device/port/PortDefinition.java`

**参数定义和值快照**

- `src/main/java/com/hvac/simulator/device/parameter/ParameterType.java`
- `src/main/java/com/hvac/simulator/device/parameter/ParameterUsage.java`
- `src/main/java/com/hvac/simulator/device/parameter/ParameterValue.java`
- `src/main/java/com/hvac/simulator/device/parameter/DecimalParameterValue.java`
- `src/main/java/com/hvac/simulator/device/parameter/IntegerParameterValue.java`
- `src/main/java/com/hvac/simulator/device/parameter/BooleanParameterValue.java`
- `src/main/java/com/hvac/simulator/device/parameter/EnumParameterValue.java`
- `src/main/java/com/hvac/simulator/device/parameter/ParameterConstraint.java`
- `src/main/java/com/hvac/simulator/device/parameter/DecimalRange.java`
- `src/main/java/com/hvac/simulator/device/parameter/IntegerRange.java`
- `src/main/java/com/hvac/simulator/device/parameter/AllowedEnumValues.java`
- `src/main/java/com/hvac/simulator/device/parameter/NoParameterConstraint.java`
- `src/main/java/com/hvac/simulator/device/parameter/ParameterDefinition.java`
- `src/main/java/com/hvac/simulator/device/parameter/ParameterSnapshot.java`
- Modify `src/main/java/com/hvac/simulator/device/DeviceDefinition.java`

**单步运行时**

- `src/main/java/com/hvac/simulator/device/runtime/DeviceState.java`
- `src/main/java/com/hvac/simulator/device/runtime/StatelessDeviceState.java`
- `src/main/java/com/hvac/simulator/device/runtime/DeviceStateDescriptor.java`
- `src/main/java/com/hvac/simulator/device/runtime/SimulationStepContext.java`
- `src/main/java/com/hvac/simulator/device/runtime/DeviceCalculationInput.java`
- `src/main/java/com/hvac/simulator/device/runtime/MetricScalar.java`
- `src/main/java/com/hvac/simulator/device/runtime/DeviceMetricValue.java`
- `src/main/java/com/hvac/simulator/device/runtime/DeviceCalculationResult.java`
- `src/main/java/com/hvac/simulator/device/runtime/DeviceCalculationErrorCode.java`
- `src/main/java/com/hvac/simulator/device/runtime/DeviceCalculationElementType.java`
- `src/main/java/com/hvac/simulator/device/runtime/DeviceCalculationError.java`
- `src/main/java/com/hvac/simulator/device/runtime/CalculationOutcome.java`
- `src/main/java/com/hvac/simulator/device/runtime/CalculationSuccess.java`
- `src/main/java/com/hvac/simulator/device/runtime/CalculationFailure.java`
- `src/main/java/com/hvac/simulator/device/runtime/DeviceRuntime.java`
- `src/main/java/com/hvac/simulator/device/runtime/SingleDeviceStepExecutor.java`

**测试和项目事实**

- `src/test/java/com/hvac/simulator/energy/runtime/EnergyRuntimeValueTest.java`
- `src/test/java/com/hvac/simulator/device/port/PortRuntimeContractTest.java`
- `src/test/java/com/hvac/simulator/device/parameter/ParameterDefinitionTest.java`
- `src/test/java/com/hvac/simulator/device/parameter/ParameterSnapshotTest.java`
- `src/test/java/com/hvac/simulator/device/runtime/DeviceRuntimeContractTest.java`
- `src/test/java/com/hvac/simulator/device/runtime/SingleDeviceStepExecutorTest.java`
- Modify existing `DeviceContractTest`、`PortDefinitionTest` 和 `TopologyValidatorTest` construction helpers.
- Modify `PROJECT_GUIDE.md` and `PROJECT_STATUS.md` only after implementation and verification.

---

### Task 1: 建立公共单位、介质和基础值对象

**Files:**
- Create: `src/main/java/com/hvac/simulator/energy/runtime/UnitCode.java`
- Create: `src/main/java/com/hvac/simulator/energy/runtime/QualityStatus.java`
- Create: `src/main/java/com/hvac/simulator/energy/runtime/SupplyStatus.java`
- Create: `src/main/java/com/hvac/simulator/energy/runtime/FluidMedium.java`
- Create: `src/main/java/com/hvac/simulator/energy/runtime/FluidProperties.java`
- Create: `src/main/java/com/hvac/simulator/energy/runtime/PressureValue.java`
- Create: `src/main/java/com/hvac/simulator/energy/runtime/KnownPressure.java`
- Create: `src/main/java/com/hvac/simulator/energy/runtime/UnavailablePressure.java`
- Test: `src/test/java/com/hvac/simulator/energy/runtime/EnergyRuntimeValueTest.java`

**Interfaces:**
- Consumes: Java `record`、`enum`、`sealed interface`。
- Produces: `UnitCode`、`QualityStatus`、`FluidProperties` 和无哨兵压力表达。

- [ ] **Step 1: 写失败的基础值测试**

创建 `EnergyRuntimeValueTest`，至少包含以下完整行为：

```java
package com.hvac.simulator.energy.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EnergyRuntimeValueTest {

    @Test
    void fluidPropertiesRequireFinitePositiveValues() {
        var properties = new FluidProperties(FluidMedium.WATER, 998.2, 4_180.0);
        assertEquals(FluidMedium.WATER, properties.medium());
        assertThrows(IllegalArgumentException.class,
                () -> new FluidProperties(FluidMedium.WATER, Double.NaN, 4_180.0));
        assertThrows(IllegalArgumentException.class,
                () -> new FluidProperties(FluidMedium.WATER, 998.2, 0.0));
    }

    @Test
    void pressureUsesKnownOrExplicitUnavailableState() {
        PressureValue known = new KnownPressure(250_000.0);
        PressureValue unavailable = UnavailablePressure.INSTANCE;
        assertEquals(250_000.0, ((KnownPressure) known).pascals());
        assertEquals(UnavailablePressure.INSTANCE, unavailable);
        assertThrows(IllegalArgumentException.class, () -> new KnownPressure(-1.0));
        assertThrows(IllegalArgumentException.class, () -> new KnownPressure(Double.POSITIVE_INFINITY));
    }
}
```

- [ ] **Step 2: 运行测试并确认 Red**

Run: `.\mvnw.cmd -Dtest=EnergyRuntimeValueTest test`

Expected: FAIL，`com.hvac.simulator.energy.runtime` 类型尚不存在。

- [ ] **Step 3: 实现最小公共编码和压力类型**

```java
// UnitCode.java
package com.hvac.simulator.energy.runtime;
public enum UnitCode {
    NONE, CELSIUS, KILOWATT, KILOWATT_HOUR, KILOGRAM_PER_SECOND,
    PASCAL, KILOGRAM_PER_CUBIC_METER, JOULE_PER_KILOGRAM_KELVIN
}

// QualityStatus.java
package com.hvac.simulator.energy.runtime;
public enum QualityStatus { GOOD, UNCERTAIN, BAD, NOT_AVAILABLE }

// SupplyStatus.java
package com.hvac.simulator.energy.runtime;
public enum SupplyStatus { AVAILABLE, UNAVAILABLE, UNKNOWN }

// FluidMedium.java
package com.hvac.simulator.energy.runtime;
public record FluidMedium(String code) {
    public static final FluidMedium WATER = new FluidMedium("WATER");
    public FluidMedium {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("介质编码不能为空");
    }
}

// PressureValue.java
package com.hvac.simulator.energy.runtime;
public sealed interface PressureValue permits KnownPressure, UnavailablePressure {}

// KnownPressure.java
package com.hvac.simulator.energy.runtime;
public record KnownPressure(double pascals) implements PressureValue {
    public KnownPressure {
        if (!Double.isFinite(pascals) || pascals < 0.0) {
            throw new IllegalArgumentException("压力必须是有限非负值，单位 Pa");
        }
    }
}

// UnavailablePressure.java
package com.hvac.simulator.energy.runtime;
public enum UnavailablePressure implements PressureValue { INSTANCE }
```

```java
// FluidProperties.java
package com.hvac.simulator.energy.runtime;

import java.util.Objects;

/** 保存本时间步设备计算使用的介质物性，不负责按温度或配比推导物性。 */
public record FluidProperties(
        FluidMedium medium,
        double densityKgPerCubicMeter,
        double specificHeatJPerKgK) {
    public FluidProperties {
        Objects.requireNonNull(medium, "介质编码不能为空");
        if (!Double.isFinite(densityKgPerCubicMeter) || densityKgPerCubicMeter <= 0.0
                || !Double.isFinite(specificHeatJPerKgK) || specificHeatJPerKgK <= 0.0) {
            throw new IllegalArgumentException("介质密度和比热容必须是有限正值");
        }
    }
}
```

- [ ] **Step 4: 运行测试并进行低风险注释检查**

Run: `.\mvnw.cmd -Dtest=EnergyRuntimeValueTest test`

Expected: PASS；只有 `FluidProperties` 需要说明“不计算物性”的长期边界，简单枚举不添加逐项注释。

- [ ] **Step 5: 明确暂存并提交**

```powershell
git add -- src/main/java/com/hvac/simulator/energy/runtime src/test/java/com/hvac/simulator/energy/runtime/EnergyRuntimeValueTest.java
git diff --cached --check
git commit -m "feat(engine): define shared runtime units"
```

### Task 2: 建立强类型端口值和运行值规格

**Files:**
- Create: all `PortValue` and `PortValueSpec` files listed in File Map.
- Modify: `src/main/java/com/hvac/simulator/device/port/PortDefinition.java`
- Modify: existing port construction helpers in `DeviceContractTest` and `TopologyValidatorTest`.
- Test: `src/test/java/com/hvac/simulator/device/port/PortRuntimeContractTest.java`

**Interfaces:**
- Consumes: Task 1 runtime units, existing `EnergyType` and `PortDefinition`.
- Produces: `PortValueSpec.valueType()`、规范电/水/控制值和显式端口规格。

- [ ] **Step 1: 写失败的端口运行值测试**

测试必须覆盖：电功率和电能非负、水温允许负值、流量非负、三类控制信号分离、单位和模式规格、介质规格，以及模式集合防御性复制。核心断言如下：

```java
@Test
void electricalAndWaterValuesEnforceFiniteSignRules() {
    assertThrows(IllegalArgumentException.class, () -> new ElectricalPortValue(
            -1.0, 0.0, SupplyStatus.AVAILABLE, QualityStatus.GOOD));
    var water = new WaterPortValue(-5.0, 2.0, UnavailablePressure.INSTANCE,
            new FluidProperties(FluidMedium.WATER, 998.2, 4_180.0), QualityStatus.GOOD);
    assertEquals(-5.0, water.temperatureC());
    assertThrows(IllegalArgumentException.class, () -> new WaterPortValue(
            12.0, Double.NaN, UnavailablePressure.INSTANCE,
            water.fluidProperties(), QualityStatus.GOOD));
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
            "power-in", "供电输入", EnergyType.ELECTRICITY, PortDirection.INPUT,
            WaterSide.NOT_APPLICABLE, PortCardinality.REQUIRED_SINGLE,
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
```

- [ ] **Step 2: 运行测试并确认 Red**

Run: `.\mvnw.cmd -Dtest=PortRuntimeContractTest test`

Expected: FAIL，端口值和规格尚不存在。

- [ ] **Step 3: 实现密封端口值**

`PortValue` 明确 permits 五种类型；实现下列构造规则：

```java
public sealed interface PortValue permits ElectricalPortValue, WaterPortValue,
        SetpointSignalValue, StartStopSignalValue, ModeSignalValue {
    QualityStatus quality();
}
```

```java
public record ElectricalPortValue(double activePowerKw, double energyKwh,
        SupplyStatus supplyStatus, QualityStatus quality) implements PortValue {
    public ElectricalPortValue {
        Objects.requireNonNull(supplyStatus, "供电状态不能为空");
        Objects.requireNonNull(quality, "质量状态不能为空");
        if (!Double.isFinite(activePowerKw) || activePowerKw < 0.0
                || !Double.isFinite(energyKwh) || energyKwh < 0.0) {
            throw new IllegalArgumentException("有功功率和电能必须是有限非负值");
        }
    }
}

public record WaterPortValue(double temperatureC, double massFlowKgPerSecond,
        PressureValue pressure, FluidProperties fluidProperties,
        QualityStatus quality) implements PortValue {
    public WaterPortValue {
        Objects.requireNonNull(pressure, "压力状态不能为空");
        Objects.requireNonNull(fluidProperties, "介质属性不能为空");
        Objects.requireNonNull(quality, "质量状态不能为空");
        if (!Double.isFinite(temperatureC)
                || !Double.isFinite(massFlowKgPerSecond) || massFlowKgPerSecond < 0.0) {
            throw new IllegalArgumentException("水温必须有限，质量流量必须是有限非负值");
        }
    }
}
```

```java
public record SetpointSignalValue(double value, UnitCode unit,
        QualityStatus quality) implements PortValue {
    public SetpointSignalValue {
        Objects.requireNonNull(unit, "设定值单位不能为空");
        Objects.requireNonNull(quality, "质量状态不能为空");
        if (!Double.isFinite(value)) throw new IllegalArgumentException("设定值必须是有限值");
    }
}

public enum StartStopCommand { START, STOP }

public record StartStopSignalValue(StartStopCommand command,
        QualityStatus quality) implements PortValue {
    public StartStopSignalValue {
        Objects.requireNonNull(command, "启停命令不能为空");
        Objects.requireNonNull(quality, "质量状态不能为空");
    }
}

public record ModeSignalValue(String modeCode,
        QualityStatus quality) implements PortValue {
    public ModeSignalValue {
        if (modeCode == null || modeCode.isBlank()) throw new IllegalArgumentException("模式编码不能为空");
        Objects.requireNonNull(quality, "质量状态不能为空");
    }
}
```

将上述每个 public 类型保存到 File Map 对应的同名文件；五个 record 文件显式导入 `java.util.Objects`，并使用 Task 1 已创建的同包类型。

- [ ] **Step 4: 实现端口规格并扩展 `PortDefinition`**

`PortValueSpec` 提供精确类型和能源适用性：

```java
public sealed interface PortValueSpec permits ElectricalPortValueSpec, WaterPortValueSpec,
        SetpointSignalSpec, StartStopSignalSpec, ModeSignalSpec {
    Class<? extends PortValue> valueType();
    boolean supportsEnergyType(EnergyType energyType);
}
```

五个规格实现为：

```java
public enum ElectricalPortValueSpec implements PortValueSpec {
    INSTANCE;
    @Override public Class<? extends PortValue> valueType() { return ElectricalPortValue.class; }
    @Override public boolean supportsEnergyType(EnergyType energyType) {
        return energyType == EnergyType.ELECTRICITY;
    }
}

public record WaterPortValueSpec(FluidMedium medium) implements PortValueSpec {
    public WaterPortValueSpec { Objects.requireNonNull(medium, "水介质编码不能为空"); }
    @Override public Class<? extends PortValue> valueType() { return WaterPortValue.class; }
    @Override public boolean supportsEnergyType(EnergyType energyType) {
        return energyType != null && energyType.isWater();
    }
}

public record SetpointSignalSpec(UnitCode unit) implements PortValueSpec {
    public SetpointSignalSpec { Objects.requireNonNull(unit, "设定值单位不能为空"); }
    @Override public Class<? extends PortValue> valueType() { return SetpointSignalValue.class; }
    @Override public boolean supportsEnergyType(EnergyType energyType) {
        return energyType == EnergyType.CONTROL_SIGNAL;
    }
}

public enum StartStopSignalSpec implements PortValueSpec {
    INSTANCE;
    @Override public Class<? extends PortValue> valueType() { return StartStopSignalValue.class; }
    @Override public boolean supportsEnergyType(EnergyType energyType) {
        return energyType == EnergyType.CONTROL_SIGNAL;
    }
}

public record ModeSignalSpec(List<String> allowedModes) implements PortValueSpec {
    public ModeSignalSpec {
        Objects.requireNonNull(allowedModes, "允许模式集合不能为空");
        allowedModes = List.copyOf(allowedModes);
        if (allowedModes.isEmpty() || allowedModes.stream().anyMatch(
                mode -> mode == null || mode.isBlank())) {
            throw new IllegalArgumentException("允许模式集合不能为空且不能包含空白编码");
        }
        if (new HashSet<>(allowedModes).size() != allowedModes.size()) {
            throw new IllegalArgumentException("允许模式编码不能重复");
        }
    }
    @Override public Class<? extends PortValue> valueType() { return ModeSignalValue.class; }
    @Override public boolean supportsEnergyType(EnergyType energyType) {
        return energyType == EnergyType.CONTROL_SIGNAL;
    }
}
```

每个 public 类型保存到同名文件，使用对应包名和明确 import。

将 `PortDefinition` 扩展为：

```java
public record PortDefinition(
        String id,
        String displayName,
        EnergyType energyType,
        PortDirection direction,
        WaterSide waterSide,
        PortCardinality cardinality,
        PortValueSpec valueSpec) {
    public PortDefinition {
        requireText(id, "端口编号不能为空");
        requireText(displayName, "端口名称不能为空");
        Objects.requireNonNull(energyType, "端口能源类型不能为空");
        Objects.requireNonNull(direction, "端口方向不能为空");
        Objects.requireNonNull(waterSide, "端口水侧不能为空");
        Objects.requireNonNull(cardinality, "端口连接规则不能为空");
        Objects.requireNonNull(valueSpec, "端口运行值规格不能为空");
        if (energyType.isWater() == (waterSide == WaterSide.NOT_APPLICABLE)) {
            throw new IllegalArgumentException("水端口必须声明供回水侧，非水端口不得声明供回水侧");
        }
        if (!valueSpec.supportsEnergyType(energyType)) {
            throw new IllegalArgumentException("端口能源类型与运行值规格不兼容");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }
}
```

更新现有测试辅助方法：电端口传 `ElectricalPortValueSpec.INSTANCE`，水端口传 `new WaterPortValueSpec(FluidMedium.WATER)`，控制端口显式选择具体控制规格。不得引入“通用控制信号”默认值。

- [ ] **Step 5: 运行端口和受影响静态契约测试**

Run:

```powershell
.\mvnw.cmd -Dtest=PortDefinitionTest,PortRuntimeContractTest,DeviceContractTest,TopologyValidatorTest test
```

Expected: PASS；现有拓扑方向、能源和水侧校验不退化。

- [ ] **Step 6: Refactor 并提交**

检查公共类注释只解释规范单位、介质自包含和控制信号拆分原因；不要逐行解释 record 字段。

```powershell
git add -- src/main/java/com/hvac/simulator/energy/runtime src/main/java/com/hvac/simulator/device/port src/test/java/com/hvac/simulator/device/port src/test/java/com/hvac/simulator/device/DeviceContractTest.java src/test/java/com/hvac/simulator/topology/validation/TopologyValidatorTest.java
git diff --cached --check
git commit -m "feat(engine): define typed runtime port values"
```

### Task 3: 建立参数定义、完整快照并扩展设备定义

**Files:**
- Create: all `device.parameter` files from File Map.
- Modify: `src/main/java/com/hvac/simulator/device/DeviceDefinition.java`
- Test: `ParameterDefinitionTest.java`、`ParameterSnapshotTest.java`
- Modify: `DeviceContractTest.java`

**Interfaces:**
- Consumes: `UnitCode`、`DeviceModuleKey`、现有 `DeviceDefinition`。
- Produces: `ParameterDefinition`、`ParameterSnapshot.withOverrides(...)`、`restore(...)`。

- [ ] **Step 1: 写失败的参数定义测试**

测试四种类型、闭区间、枚举允许值、生效状态和默认值校验：

```java
@Test
void definitionRequiresMatchingTypeUnitConstraintAndDefault() {
    var definition = new ParameterDefinition("rated-power", "额定功率",
            ParameterType.DECIMAL, new DecimalParameterValue(100.0, UnitCode.KILOWATT),
            UnitCode.KILOWATT, new DecimalRange(0.0, 500.0),
            ParameterUsage.CONFIGURABLE_CALCULATION);
    assertTrue(definition.modifiable());
    assertTrue(definition.usedInCalculation());
    assertThrows(IllegalArgumentException.class, () -> new ParameterDefinition(
            "rated-power", "额定功率", ParameterType.INTEGER,
            new DecimalParameterValue(100.0, UnitCode.KILOWATT), UnitCode.KILOWATT,
            new IntegerRange(0, 500), ParameterUsage.CONFIGURABLE_CALCULATION));
}

@Test
void enumConstraintDefensivelyCopiesAllowedValues() {
    var allowed = new ArrayList<>(List.of("AUTO", "MANUAL"));
    var constraint = new AllowedEnumValues(allowed);
    allowed.clear();
    assertTrue(constraint.accepts(new EnumParameterValue("AUTO")));
    assertThrows(UnsupportedOperationException.class, () -> constraint.values().clear());
}
```

- [ ] **Step 2: 运行定义测试并确认 Red**

Run: `.\mvnw.cmd -Dtest=ParameterDefinitionTest test`

Expected: FAIL，参数类型尚不存在。

- [ ] **Step 3: 实现参数值、约束和定义**

精确公共接口：

```java
public enum ParameterType { DECIMAL, INTEGER, BOOLEAN, ENUM }

public enum ParameterUsage {
    CONFIGURABLE_CALCULATION(true, true),
    FIXED_CALCULATION(false, true),
    NOT_USED_IN_CALCULATION(false, false);
    private final boolean modifiable;
    private final boolean usedInCalculation;
    ParameterUsage(boolean modifiable, boolean usedInCalculation) {
        this.modifiable = modifiable;
        this.usedInCalculation = usedInCalculation;
    }
    public boolean modifiable() { return modifiable; }
    public boolean usedInCalculation() { return usedInCalculation; }
}

public sealed interface ParameterValue permits DecimalParameterValue,
        IntegerParameterValue, BooleanParameterValue, EnumParameterValue {
    ParameterType type();
    UnitCode unit();
}

public sealed interface ParameterConstraint permits DecimalRange, IntegerRange,
        AllowedEnumValues, NoParameterConstraint {
    ParameterType supportedType();
    boolean accepts(ParameterValue value);
}
```

实现要求：

- `DecimalParameterValue` 拒绝非有限值，返回 `DECIMAL` 和自身单位；
- `IntegerParameterValue` 返回 `INTEGER` 和自身单位；
- `BooleanParameterValue` 返回 `BOOLEAN` 和 `NONE`；
- `EnumParameterValue` 拒绝空白编码，返回 `ENUM` 和 `NONE`；
- `DecimalRange` 拒绝非有限边界及 `minimum > maximum`；
- `IntegerRange` 拒绝 `minimum > maximum`；
- `AllowedEnumValues` 拒绝空集合、空白和重复值并防御性复制；
- `NoParameterConstraint.INSTANCE` 只支持 `BOOLEAN`。

`ParameterDefinition` 的 canonical constructor 必须执行：非空/非白编码、类型与值匹配、定义单位与值单位匹配、约束类型匹配、默认值满足约束。`modifiable()` 和 `usedInCalculation()` 委托给 `ParameterUsage`。

```java
public record ParameterDefinition(
        String code,
        String displayName,
        ParameterType type,
        ParameterValue defaultValue,
        UnitCode unit,
        ParameterConstraint constraint,
        ParameterUsage usage) {
    public ParameterDefinition {
        requireText(code, "参数编码不能为空");
        requireText(displayName, "参数名称不能为空");
        Objects.requireNonNull(type, "参数类型不能为空");
        Objects.requireNonNull(defaultValue, "参数默认值不能为空");
        Objects.requireNonNull(unit, "参数单位不能为空");
        Objects.requireNonNull(constraint, "参数约束不能为空");
        Objects.requireNonNull(usage, "参数生效状态不能为空");
        if (defaultValue.type() != type || defaultValue.unit() != unit) {
            throw new IllegalArgumentException("参数默认值类型或单位与定义不一致: " + code);
        }
        if (constraint.supportedType() != type || !constraint.accepts(defaultValue)) {
            throw new IllegalArgumentException("参数默认值不满足定义约束: " + code);
        }
    }
    public boolean modifiable() { return usage.modifiable(); }
    public boolean usedInCalculation() { return usage.usedInCalculation(); }
    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }
}
```

- [ ] **Step 4: 运行参数定义测试确认 Green**

Run: `.\mvnw.cmd -Dtest=ParameterDefinitionTest test`

Expected: PASS。

- [ ] **Step 5: 写失败的完整快照和设备参数测试**

```java
@Test
void overridesProduceCompleteDefinitionOrderedSnapshot() {
    var definition = deviceDefinition(List.of(configurable(), fixed(), unused()));
    var snapshot = ParameterSnapshot.withOverrides(definition, Map.of(
            "setpoint", new DecimalParameterValue(8.0, UnitCode.CELSIUS)));
    assertEquals(List.of("setpoint", "rated-power", "legacy-value"),
            List.copyOf(snapshot.values().keySet()));
    assertEquals(3, snapshot.values().size());
}

@Test
void fixedUnknownAndWrongUnitOverridesAreRejected() {
    var definition = deviceDefinition(List.of(configurable(), fixed(), unused()));
    assertThrows(IllegalArgumentException.class, () -> ParameterSnapshot.withOverrides(
            definition, Map.of("rated-power",
                    new DecimalParameterValue(90.0, UnitCode.KILOWATT))));
    assertThrows(IllegalArgumentException.class, () -> ParameterSnapshot.withOverrides(
            definition, Map.of("missing", new BooleanParameterValue(true))));
    assertThrows(IllegalArgumentException.class, () -> ParameterSnapshot.withOverrides(
            definition, Map.of("setpoint",
                    new DecimalParameterValue(8.0, UnitCode.KILOWATT))));
}
```

- [ ] **Step 6: 运行快照测试并确认 Red**

Run: `.\mvnw.cmd -Dtest=ParameterSnapshotTest,DeviceContractTest test`

Expected: FAIL，`ParameterSnapshot` 和扩展后的 `DeviceDefinition` 尚不存在。

- [ ] **Step 7: 扩展 `DeviceDefinition` 并实现快照**

将 `DeviceDefinition` 参数列表追加到现有 record 组件末尾，并用以下完整 canonical constructor 替换现有实现：

```java
public record DeviceDefinition(DeviceModuleKey key, String displayName,
        List<PortDefinition> ports, TimeStepCapability timeStepCapability,
        List<ParameterDefinition> parameters) {
    public DeviceDefinition(DeviceModuleKey key, String displayName,
            List<PortDefinition> ports, TimeStepCapability timeStepCapability) {
        this(key, displayName, ports, timeStepCapability, List.of());
    }
    public DeviceDefinition {
        Objects.requireNonNull(key, "设备模块键不能为空");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("设备名称不能为空");
        }
        Objects.requireNonNull(ports, "设备端口不能为空");
        Objects.requireNonNull(parameters, "设备参数不能为空");
        ports = List.copyOf(ports);
        parameters = List.copyOf(parameters);
        if (ports.isEmpty()) throw new IllegalArgumentException("设备至少需要一个端口");
        ensureUniquePortIds(ports);
        ensureUniqueParameterCodes(parameters);
        Objects.requireNonNull(timeStepCapability, "设备时间步能力不能为空");
    }
    private static void ensureUniquePortIds(List<PortDefinition> ports) {
        var ids = new HashSet<String>();
        for (var port : ports) {
            Objects.requireNonNull(port, "设备端口不能包含空值");
            if (!ids.add(port.id())) throw new IllegalArgumentException("设备端口编号重复: " + port.id());
        }
    }
    private static void ensureUniqueParameterCodes(List<ParameterDefinition> parameters) {
        var codes = new HashSet<String>();
        for (var parameter : parameters) {
            Objects.requireNonNull(parameter, "设备参数不能包含空值");
            if (!codes.add(parameter.code())) {
                throw new IllegalArgumentException("设备参数编码重复: " + parameter.code());
            }
        }
    }
    public Optional<PortDefinition> findPort(String portId) {
        if (portId == null || portId.isBlank()) return Optional.empty();
        return ports.stream().filter(port -> port.id().equals(portId)).findFirst();
    }
    public Optional<ParameterDefinition> findParameter(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return parameters.stream().filter(parameter -> parameter.code().equals(code)).findFirst();
    }
}
```

`ParameterSnapshot` 使用 private constructor，防止绕过定义校验：

```java
public final class ParameterSnapshot {
    private final DeviceModuleKey moduleKey;
    private final Map<String, ParameterValue> values;

    private ParameterSnapshot(DeviceModuleKey moduleKey, Map<String, ParameterValue> values) {
        this.moduleKey = moduleKey;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static ParameterSnapshot fromDefaults(DeviceDefinition definition) {
        return withOverrides(definition, Map.of());
    }

    public static ParameterSnapshot withOverrides(
            DeviceDefinition definition, Map<String, ParameterValue> overrides) {
        Objects.requireNonNull(definition, "设备定义不能为空");
        Objects.requireNonNull(overrides, "参数覆盖集合不能为空");
        for (var entry : overrides.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("参数覆盖不能包含空白编码或空值");
            }
        }
        var resolved = new LinkedHashMap<String, ParameterValue>();
        for (var parameter : definition.parameters()) {
            var override = overrides.get(parameter.code());
            if (override != null && !parameter.modifiable()) {
                throw new IllegalArgumentException("参数不允许修改: " + parameter.code());
            }
            var value = override == null ? parameter.defaultValue() : override;
            validateValue(parameter, value);
            resolved.put(parameter.code(), value);
        }
        for (var code : overrides.keySet()) {
            if (definition.findParameter(code).isEmpty()) {
                throw new IllegalArgumentException("未知参数: " + code);
            }
        }
        return new ParameterSnapshot(definition.key(), resolved);
    }

    public static ParameterSnapshot restore(
            DeviceDefinition definition, Map<String, ParameterValue> completeValues) {
        Objects.requireNonNull(definition, "设备定义不能为空");
        Objects.requireNonNull(completeValues, "历史参数集合不能为空");
        if (completeValues.size() != definition.parameters().size()) {
            throw new IllegalArgumentException("历史参数快照必须包含完整参数集合");
        }
        var restored = new LinkedHashMap<String, ParameterValue>();
        for (var parameter : definition.parameters()) {
            var value = completeValues.get(parameter.code());
            if (value == null) throw new IllegalArgumentException("历史参数缺失: " + parameter.code());
            validateValue(parameter, value);
            if (!parameter.modifiable() && !parameter.defaultValue().equals(value)) {
                throw new IllegalArgumentException("固定参数与版本默认值不一致: " + parameter.code());
            }
            restored.put(parameter.code(), value);
        }
        return new ParameterSnapshot(definition.key(), restored);
    }

    private static void validateValue(ParameterDefinition definition, ParameterValue value) {
        Objects.requireNonNull(value, "参数值不能为空: " + definition.code());
        if (value.type() != definition.type()) {
            throw new IllegalArgumentException("参数类型不匹配: " + definition.code());
        }
        if (value.unit() != definition.unit()) {
            throw new IllegalArgumentException("参数单位不匹配: " + definition.code());
        }
        if (!definition.constraint().accepts(value)) {
            throw new IllegalArgumentException("参数值超出允许范围: " + definition.code());
        }
    }

    public DeviceModuleKey moduleKey() { return moduleKey; }
    public Map<String, ParameterValue> values() { return values; }
    public ParameterValue requiredValue(String code) {
        var value = values.get(code);
        if (value == null) throw new IllegalArgumentException("参数快照中不存在参数: " + code);
        return value;
    }
}
```

- [ ] **Step 8: 运行全部参数和设备静态测试**

Run:

```powershell
.\mvnw.cmd -Dtest=ParameterDefinitionTest,ParameterSnapshotTest,DeviceContractTest,PortDefinitionTest,TopologyValidatorTest test
```

Expected: PASS。

- [ ] **Step 9: Refactor 并提交**

```powershell
git add -- src/main/java/com/hvac/simulator/device/parameter src/main/java/com/hvac/simulator/device/DeviceDefinition.java src/test/java/com/hvac/simulator/device/parameter src/test/java/com/hvac/simulator/device/DeviceContractTest.java
git diff --cached --check
git commit -m "feat(engine): define device parameter snapshots"
```

### Task 4: 建立泛型状态、结果和中文稳定错误

**Files:**
- Create: runtime files from `DeviceState.java` through `DeviceRuntime.java` in File Map, excluding executor.
- Test: `DeviceRuntimeContractTest.java`

**Interfaces:**
- Consumes: `PortValue`、`ParameterSnapshot`、`DeviceModuleKey`。
- Produces: `DeviceRuntime<S>.calculate(...)` 和 `CalculationOutcome<S>`。

- [ ] **Step 1: 写失败的运行时契约测试**

测试必须覆盖：带时区时间、正步长、输入映射防御性复制、无状态对象、状态描述版本、成功/失败类型、错误中文名称及有序详情。

```java
@Test
void calculationInputDefensivelyCopiesValues() {
    var values = new LinkedHashMap<String, PortValue>();
    values.put("power-in", electrical());
    var input = new DeviceCalculationInput<>(context(), values,
            ParameterSnapshot.fromDefaults(definition()), StatelessDeviceState.INSTANCE);
    values.clear();
    assertEquals(List.of("power-in"), List.copyOf(input.portInputs().keySet()));
    assertThrows(UnsupportedOperationException.class, () -> input.portInputs().clear());
}

@Test
void errorProvidesStableCodeAndChineseTitle() {
    var error = new DeviceCalculationError(DeviceCalculationErrorCode.MISSING_INPUT,
            "缺少必需输入端口：power-in", KEY, context().simulationTime(),
            DeviceCalculationElementType.PORT, "power-in", Map.of("required", "true"));
    assertEquals("缺少输入", error.title());
    assertEquals(DeviceCalculationErrorCode.MISSING_INPUT, error.code());
}
```

- [ ] **Step 2: 运行测试并确认 Red**

Run: `.\mvnw.cmd -Dtest=DeviceRuntimeContractTest test`

Expected: FAIL，运行时契约类型尚不存在。

- [ ] **Step 3: 实现状态、时间和输入**

```java
public interface DeviceState {}

public enum StatelessDeviceState implements DeviceState { INSTANCE }

public record DeviceStateDescriptor<S extends DeviceState>(
        String stateCode, int schemaVersion, Class<S> stateType) {
    public DeviceStateDescriptor {
        if (stateCode == null || stateCode.isBlank()) throw new IllegalArgumentException("状态编码不能为空");
        if (schemaVersion <= 0) throw new IllegalArgumentException("状态结构版本必须是正整数");
        Objects.requireNonNull(stateType, "状态 Java 类型不能为空");
    }
    public boolean accepts(DeviceState state) { return stateType.isInstance(state); }
}

public record SimulationStepContext(ZonedDateTime simulationTime, Duration timeStep) {
    public SimulationStepContext {
        Objects.requireNonNull(simulationTime, "模拟时间不能为空");
        Objects.requireNonNull(timeStep, "计算步长不能为空");
        if (timeStep.isZero() || timeStep.isNegative()) {
            throw new IllegalArgumentException("计算步长必须是正数");
        }
    }
}
```

`DeviceCalculationInput` canonical constructor 对输入映射执行 `LinkedHashMap` 防御性复制；不要在此处检查静态端口规格，保持对象创建与单设备执行校验分离：

```java
public record DeviceCalculationInput<S extends DeviceState>(
        SimulationStepContext context,
        Map<String, PortValue> portInputs,
        ParameterSnapshot parameters,
        S previousState) {
    public DeviceCalculationInput {
        Objects.requireNonNull(context, "单步时间不能为空");
        Objects.requireNonNull(portInputs, "端口输入不能为空");
        Objects.requireNonNull(parameters, "参数快照不能为空");
        Objects.requireNonNull(previousState, "上一步设备状态不能为空");
        var copy = new LinkedHashMap<String, PortValue>();
        for (var entry : portInputs.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("端口输入不能包含空白编码或空值");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        portInputs = Collections.unmodifiableMap(copy);
    }
}
```

- [ ] **Step 4: 实现指标、结果、错误和 outcome**

`MetricScalar` 在一个文件中使用四个嵌套 record 覆盖设计确认的四种指标标量：

```java
public sealed interface MetricScalar permits MetricScalar.DecimalValue,
        MetricScalar.IntegerValue, MetricScalar.BooleanValue, MetricScalar.EnumValue {
    UnitCode unit();

    record DecimalValue(double value, UnitCode unit) implements MetricScalar {
        public DecimalValue {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("小数指标必须是有限值");
            Objects.requireNonNull(unit, "小数指标单位不能为空");
        }
    }
    record IntegerValue(long value, UnitCode unit) implements MetricScalar {
        public IntegerValue { Objects.requireNonNull(unit, "整数指标单位不能为空"); }
    }
    record BooleanValue(boolean value) implements MetricScalar {
        @Override public UnitCode unit() { return UnitCode.NONE; }
    }
    record EnumValue(String value) implements MetricScalar {
        public EnumValue {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("枚举指标编码不能为空");
        }
        @Override public UnitCode unit() { return UnitCode.NONE; }
    }
}

public record DeviceMetricValue(String code, MetricScalar value, QualityStatus quality) {
    public DeviceMetricValue {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("指标编码不能为空");
        Objects.requireNonNull(value, "指标值不能为空");
        Objects.requireNonNull(quality, "指标质量状态不能为空");
    }
}
```

`DeviceCalculationResult<S>` 对输出和指标映射有序防御性复制，拒绝空白键、空值及“映射键与指标内部 code 不一致”，并要求下一状态非空：

```java
public record DeviceCalculationResult<S extends DeviceState>(
        Map<String, PortValue> portOutputs,
        Map<String, DeviceMetricValue> metrics,
        S nextState) {
    public DeviceCalculationResult {
        Objects.requireNonNull(portOutputs, "端口输出不能为空");
        Objects.requireNonNull(metrics, "设备指标不能为空");
        Objects.requireNonNull(nextState, "下一设备状态不能为空");
        var outputCopy = new LinkedHashMap<String, PortValue>();
        for (var entry : portOutputs.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("端口输出不能包含空白编码或空值");
            }
            outputCopy.put(entry.getKey(), entry.getValue());
        }
        var metricCopy = new LinkedHashMap<String, DeviceMetricValue>();
        for (var entry : metrics.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null
                    || !entry.getKey().equals(entry.getValue().code())) {
                throw new IllegalArgumentException("指标映射键必须与非空指标编码一致");
            }
            metricCopy.put(entry.getKey(), entry.getValue());
        }
        portOutputs = Collections.unmodifiableMap(outputCopy);
        metrics = Collections.unmodifiableMap(metricCopy);
    }
}
```

```java
public enum DeviceCalculationErrorCode {
    MISSING_INPUT("缺少输入"),
    INCOMPATIBLE_VALUE_TYPE("端口值类型不兼容"),
    INCOMPATIBLE_UNIT("单位不兼容"),
    INCOMPATIBLE_MEDIUM("介质不兼容"),
    NON_FINITE_VALUE("数值不是有限值"),
    DIVISION_BY_ZERO("计算发生除零"),
    NUMERIC_OUT_OF_RANGE("数值超出范围"),
    UNSUPPORTED_TIME_STEP("不支持的计算步长");
    private final String title;
    DeviceCalculationErrorCode(String title) { this.title = title; }
    public String title() { return title; }
}

public enum DeviceCalculationElementType { DEVICE, PORT, PARAMETER, METRIC, STATE }
```

`DeviceCalculationError` 验证全部必需字段，对详情先放入 `TreeMap` 排序，再复制为只读 `LinkedHashMap`；`title()` 返回 `code.title()`。

```java
public sealed interface CalculationOutcome<S extends DeviceState>
        permits CalculationSuccess, CalculationFailure {}

public record CalculationSuccess<S extends DeviceState>(
        DeviceCalculationResult<S> result) implements CalculationOutcome<S> {
    public CalculationSuccess { Objects.requireNonNull(result, "计算成功结果不能为空"); }
}

public record CalculationFailure<S extends DeviceState>(
        DeviceCalculationError error) implements CalculationOutcome<S> {
    public CalculationFailure { Objects.requireNonNull(error, "计算错误不能为空"); }
}

public interface DeviceRuntime<S extends DeviceState> {
    DeviceModuleKey moduleKey();
    DeviceStateDescriptor<S> stateDescriptor();
    CalculationOutcome<S> calculate(DeviceCalculationInput<S> input);
}
```

- [ ] **Step 5: 运行契约测试确认 Green**

Run: `.\mvnw.cmd -Dtest=DeviceRuntimeContractTest test`

Expected: PASS。

- [ ] **Step 6: Refactor 并提交**

检查注释明确说明状态版本用于未来适配，不声称 JSON 或数据库已实现。

```powershell
git add -- src/main/java/com/hvac/simulator/device/runtime src/test/java/com/hvac/simulator/device/runtime/DeviceRuntimeContractTest.java
git diff --cached --check
git commit -m "feat(engine): define single-step device contract"
```

### Task 5: 实现公共单设备执行器和错误优先级

**Files:**
- Create: `src/main/java/com/hvac/simulator/device/runtime/SingleDeviceStepExecutor.java`
- Test: `src/test/java/com/hvac/simulator/device/runtime/SingleDeviceStepExecutorTest.java`

**Interfaces:**
- Consumes: `DeviceDefinition`、`DeviceRuntime<S>`、`DeviceCalculationInput<S>`。
- Produces: `<S extends DeviceState> CalculationOutcome<S> execute(...)`。

- [ ] **Step 1: 写失败的步长和缺失输入测试**

使用测试内部 `FakeState` record、`FakeRuntime` 和完整 `DeviceDefinition`，不引用 Gaia 夹具：

```java
@Test
void unsupportedTimeStepWinsBeforeMissingInput() {
    var input = input(Duration.ofMinutes(10), Map.of());
    var result = executor.execute(definition(), runtime(), input);
    assertFailureCode(result, DeviceCalculationErrorCode.UNSUPPORTED_TIME_STEP);
}

@Test
void missingRequiredInputReturnsChineseFailure() {
    var result = executor.execute(definition(), runtime(), input(Duration.ofMinutes(1), Map.of()));
    var failure = assertInstanceOf(CalculationFailure.class, result);
    assertEquals(DeviceCalculationErrorCode.MISSING_INPUT, failure.error().code());
    assertEquals("缺少输入", failure.error().title());
    assertEquals("power-in", failure.error().elementCode());
}
```

- [ ] **Step 2: 运行测试确认 Red**

Run: `.\mvnw.cmd -Dtest=SingleDeviceStepExecutorTest test`

Expected: FAIL，执行器尚不存在。

- [ ] **Step 3: 实现装配、步长和缺失输入校验**

```java
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
            return failure(DeviceCalculationErrorCode.UNSUPPORTED_TIME_STEP,
                    "设备不支持当前计算步长: " + input.context().timeStep(),
                    definition, input, DeviceCalculationElementType.DEVICE,
                    definition.key().deviceType(), Map.of("timeStep", input.context().timeStep().toString()));
        }
        for (var port : definition.ports()) {
            if (port.direction() != PortDirection.INPUT) continue;
            var value = input.portInputs().get(port.id());
            if (value == null && port.cardinality().required()) {
                return failure(DeviceCalculationErrorCode.MISSING_INPUT,
                        "缺少必需输入端口: " + port.id(), definition, input,
                        DeviceCalculationElementType.PORT, port.id(), Map.of("required", "true"));
            }
            if (value != null) {
                var validation = validateInputValue(port, value, definition, input);
                if (validation != null) return validation;
            }
        }
        var outcome = Objects.requireNonNull(runtime.calculate(input), "设备运行时不能返回空结果");
        if (outcome instanceof CalculationFailure<?>) return outcome;
        @SuppressWarnings("unchecked")
        var success = (CalculationSuccess<S>) outcome;
        return new CalculationSuccess<>(normalizeResult(definition, runtime, success.result()));
    }
}
```

`validateAssembly` 必须在预期错误检查前执行并抛异常：三个模块键一致、状态描述接受 previousState、输入映射不能包含未知或非 INPUT 端口。参数快照构造时已按定义验证；执行器再次确认其模块键与定义一致。

- [ ] **Step 4: 写失败的类型、单位、介质和质量状态测试**

```java
@Test
void reportsValueTypeBeforeUnitAndMedium() {
    var result = executeWith("setpoint-in", new StartStopSignalValue(
            StartStopCommand.START, QualityStatus.GOOD));
    assertFailureCode(result, DeviceCalculationErrorCode.INCOMPATIBLE_VALUE_TYPE);
}

@Test
void reportsUnitAndMediumSeparately() {
    assertFailureCode(executeWith("setpoint-in",
            new SetpointSignalValue(7.0, UnitCode.KILOWATT, QualityStatus.GOOD)),
            DeviceCalculationErrorCode.INCOMPATIBLE_UNIT);
    assertFailureCode(executeWaterWith(new WaterPortValue(12.0, 10.0,
            UnavailablePressure.INSTANCE,
            new FluidProperties(new FluidMedium("TEST_OTHER"), 1_020.0, 3_900.0),
            QualityStatus.GOOD)), DeviceCalculationErrorCode.INCOMPATIBLE_MEDIUM);
}

@ParameterizedTest
@EnumSource(QualityStatus.class)
void commonExecutorDoesNotRejectAnyQualityStatus(QualityStatus quality) {
    assertInstanceOf(CalculationSuccess.class,
            executeWith("power-in", electricalWithQuality(quality)));
}
```

测试介质编码 `TEST_OTHER` 只用于证明不兼容介质能够被识别，不代表平台已经发布另一种介质模型；正式首版介质常量仍只有 `FluidMedium.WATER`。

- [ ] **Step 5: 实现端口值校验**

`validateInputValue` 按以下精确顺序返回第一个失败：

1. `port.valueSpec().valueType().isInstance(value)`，否则 `INCOMPATIBLE_VALUE_TYPE`；
2. `SetpointSignalSpec.unit()` 与 `SetpointSignalValue.unit()`，否则 `INCOMPATIBLE_UNIT`；
3. `WaterPortValueSpec.medium()` 与 `WaterPortValue.fluidProperties().medium()`，否则 `INCOMPATIBLE_MEDIUM`；
4. `ModeSignalSpec.allowedModes()` 包含 `ModeSignalValue.modeCode()`，否则使用 `INCOMPATIBLE_VALUE_TYPE`，中文消息明确“运行模式不在端口允许集合”。

公共执行器不根据 `QualityStatus` 失败或修改输入。

- [ ] **Step 6: 写失败的成功结果、透传错误和实现缺陷测试**

覆盖：运行时 `CalculationFailure` 原样返回；输出按定义顺序、指标按编码排序；下一状态类型错误、缺失声明输出、未知输出、输出类型/单位/介质不符均抛 `IllegalStateException`；相同输入两次结果相等且遍历顺序一致。

```java
@Test
void normalizesSuccessfulResultDeterministically() {
    var first = executor.execute(definition(), runtimeWithReversedMaps(), validInput());
    var second = executor.execute(definition(), runtimeWithReversedMaps(), validInput());
    assertEquals(first, second);
    var result = ((CalculationSuccess<FakeState>) first).result();
    assertEquals(List.of("power-out", "signal-out"), List.copyOf(result.portOutputs().keySet()));
    assertEquals(List.of("efficiency", "runtime_seconds"), List.copyOf(result.metrics().keySet()));
}
```

- [ ] **Step 7: 实现成功结果后置校验和规范化**

`normalizeResult` 必须：

- 通过 `runtime.stateDescriptor().accepts(nextState)` 检查下一状态；
- 要求每个声明的 OUTPUT 端口都有值；
- 拒绝未知或 INPUT 输出键；
- 对输出执行与输入相同的类型、单位和介质匹配，但不匹配时抛 `IllegalStateException`，因为这是设备实现缺陷；
- 按 `DeviceDefinition.ports()` 中 OUTPUT 顺序重建 `LinkedHashMap`；
- 按指标编码字典序重建指标 `LinkedHashMap`；
- 返回新的不可变 `DeviceCalculationResult<S>`。

- [ ] **Step 8: 运行全部新契约测试和受影响回归**

Run:

```powershell
.\mvnw.cmd -Dtest=EnergyRuntimeValueTest,PortDefinitionTest,PortRuntimeContractTest,DeviceContractTest,ParameterDefinitionTest,ParameterSnapshotTest,DeviceRuntimeContractTest,SingleDeviceStepExecutorTest,TopologyValidatorTest test
```

Expected: PASS；测试不依赖当前时间、随机数、个人路径或外部服务。

- [ ] **Step 9: 注释质量审查、Refactor 和提交**

完整检查 `SingleDeviceStepExecutor` 及其调用的契约文件：注释说明预期错误与编程错误边界、固定校验顺序、单位和介质原因；删除重复方法名说明和任务历史。

```powershell
git add -- src/main/java/com/hvac/simulator/device/runtime/SingleDeviceStepExecutor.java src/main/java/com/hvac/simulator/energy/runtime/FluidMedium.java src/test/java/com/hvac/simulator/device/runtime/SingleDeviceStepExecutorTest.java src/test/java/com/hvac/simulator/energy/runtime/EnergyRuntimeValueTest.java
git diff --cached --check
git commit -m "feat(engine): validate single-device calculation"
```

### Task 6: 完成 Gaia 回归、项目事实同步和 Git 交付

**Files:**
- Modify: `PROJECT_GUIDE.md`
- Modify: `PROJECT_STATUS.md`

**Interfaces:**
- Consumes: Tasks 1–5 的实际代码和测试证据。
- Produces: 明确区分“单设备运行时契约已实现”与“具体设备和自由拓扑执行未实现”的项目事实。

- [ ] **Step 1: 运行 Gaia 1.0 定向回归**

Run: `.\mvnw.cmd -Dtest=GaiaParityTest test`

Expected: PASS；冻结 10,080 行、17 字段逐时间步对照没有退化。

- [ ] **Step 2: 运行完整测试**

Run: `.\mvnw.cmd test`

Expected: `BUILD SUCCESS`，现有 Gaia 测试、第一子项目测试和本子项目全部测试通过。

- [ ] **Step 3: 同步 `PROJECT_GUIDE.md`**

在实际包职责中登记：

```markdown
| `com.hvac.simulator.energy.runtime` | 规范单位及不可变电、水、控制信号运行值，不包含设备公式或单位换算 |
| `com.hvac.simulator.device.parameter` | 版本化参数定义和完整参数值快照 |
| `com.hvac.simulator.device.runtime` | 泛型设备状态、单步计算结果、稳定错误和单设备公共校验，不包含拓扑执行与求解 |
```

将结构链路扩展为“静态设备定义 → 参数和端口运行值 → 单设备一步计算契约”，并紧接着说明当前仍没有具体设备运行时实现，不能执行自由拓扑。

- [ ] **Step 4: 同步 `PROJECT_STATUS.md`**

在“当前已核验”中只根据本次代码和测试增加：

- 公共强类型电、水和控制信号运行值；
- 参数定义和完整快照；
- 泛型状态和单步设备计算契约；
- 中文稳定错误和公共单设备校验。

在“尚未完成”继续保留：具体设备公式、运行时目录、拓扑编译、设备排序、端口传播、闭环、守恒、水力、Gaia 1.1、Spring Boot、Vue、数据库和 MQTT。更新下一步时不得把本契约提升为可执行自由拓扑。

- [ ] **Step 5: 运行文档、范围、注释和差异检查**

```powershell
git diff --check
git status --short
git diff --name-only origin/main...HEAD
git diff -- src/main/java/com/hvac/simulator/energy/runtime src/main/java/com/hvac/simulator/device src/main/java/com/hvac/simulator/topology PROJECT_GUIDE.md PROJECT_STATUS.md
```

Expected:

- 没有 `pom.xml`、Gaia 1.0 生产包、冻结 CSV、参考资产、JAR、CSV、PNG 或运行输出变化；
- 没有个人路径、凭据和生成目录；
- 生产注释准确说明单位、状态、错误和确定性；
- 文档没有把 Gaia 1.1、具体设备或自由拓扑执行写成已实现。

- [ ] **Step 6: 明确暂存项目文档并提交**

```powershell
git add -- PROJECT_GUIDE.md PROJECT_STATUS.md
git diff --cached --check
git diff --cached
git commit -m "docs(project): record device runtime contract"
```

- [ ] **Step 7: 最终验证工作区和提交范围**

```powershell
git status --short --branch
git log --oneline origin/main..HEAD
git diff --stat origin/main...HEAD
```

Expected: 工作区干净；分支只包含设计、计划、本子项目生产代码、测试和两份项目事实文档。

- [ ] **Step 8: 推送任务分支**

```powershell
git push -u origin codex/device-runtime-contract
```

Expected: 远程任务分支创建或更新成功，不推送 `main`，不强推。

- [ ] **Step 9: 准备完整 PR 材料**

PR 必须包含：

- 标题：`feat(engine): 建立设备运行时契约与公共能量数据模型`
- 已实现：强类型运行值、参数快照、泛型状态、单步结果、中文稳定错误、单设备执行器；
- 非目标：具体设备公式、拓扑执行/求解、Gaia 1.1、Spring/Vue/数据库/MQTT、Maven/JAR/CLI；
- 验证：全部实际运行命令和结果；
- 未执行：JAR、CSV、PNG，并说明本次未改变相关范围；
- 风险：这是运行时契约基础，不代表自由拓扑已经可运行；
- Compare/PR 链接；未实际创建或合并 PR 时不得声称进入 `main`。

## Plan Self-Review

- 规格覆盖：电、水、控制信号、单位、方向、参数定义、完整快照、泛型状态、输入、输出、指标、稳定中文错误、不变性、确定性和序列化版本标识均有对应任务。
- 范围隔离：没有具体设备、Gaia 公式或测量模型、拓扑执行、求解、平台基础设施和构建结构变化。
- 类型一致：`PortValueSpec`、`ParameterSnapshot`、`DeviceRuntime<S>`、`CalculationOutcome<S>` 和 `SingleDeviceStepExecutor.execute(...)` 在各任务中的名称一致。
- 错误一致：端口值类型、单位和介质使用三个不同稳定编码；质量状态不由公共执行器拒绝。
- 基准保护：最终明确运行 `GaiaParityTest` 和完整测试，不更新冻结预期结果。
- 交付边界：设计和计划先由用户确认；计划确认后才进入生产代码和测试实现。
