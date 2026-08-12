# 通用设备与拓扑引擎基础 Implementation Plan

> [!IMPORTANT]
> 文档类型：实施计划<br>
> 生命周期：冻结历史快照<br>
> 当前状态：[PROJECT_STATUS.md](../../../PROJECT_STATUS.md)<br>
> 历史目录：[docs/superpowers](../)<br>
> 使用限制：本文记录任务当时的实施安排，不用于判断功能当前是否已实现或已验证。

> 任务当时状态：已确认、已实施并完成自动化验证；分支发布和合并状态以当前 Git 与 `PROJECT_STATUS.md` 为准。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变 Gaia 1.0 公式、时间语义、输出和冻结基准的前提下，建立公共能源类型、独立设备静态契约、端口、不可变拓扑图，以及连线时和启动前的基础结构校验。

**Architecture:** 继续使用当前单 Maven 模块，在 `com.hvac.simulator.energy`、`com.hvac.simulator.device`、`com.hvac.simulator.device.port` 和 `com.hvac.simulator.topology` 中建立纯 Java 领域核心。设备模块通过版本化 `DeviceModuleKey` 和不可变 `DeviceDefinition` 声明端口与时间步能力；拓扑节点只引用设备模块键，校验器通过只读 `DeviceCatalog` 解析契约，不依赖 Spring、数据库、Web、MQTT 或 Gaia 具体公式。

**Tech Stack:** Java 21、Maven Wrapper 3.3.4、Maven 3.9.16、JUnit 6.1.2、Java 标准库。

## Global Constraints

- 本工作树只处理“通用设备与拓扑引擎基础”，不实现中央空调具体设备公式、拓扑执行图、闭环迭代、质量/能量求解、完整水力求解、参数运行时、结果指标、测量模型或任务编排。
- 不引入 Spring Boot、Vue、数据库、MQTT、HTTP、Kafka、权限、持久化或外部平台字段。
- 不修改 `com.hvac.simulator.config`、`model`、`simulation`、`weather`、`output`、`app` 中的 Gaia 1.0 生产行为，不修改 `src/main/resources/gaia-baseline/python-results.csv` 和 `reference/gaia-1.0`。
- 当前 Maven/JAR/CLI 布局保持不变；本子项目只建立逻辑引擎包。物理 `engine/server/web` 多模块迁移在引入 `server` 前另立任务，避免本次改变现有打包和运行入口。
- 公共能源类型固定为 `ELECTRICITY`、`CHILLED_WATER`、`CONDENSER_WATER`、`HOT_WATER` 和 `CONTROL_SIGNAL`；本阶段不引入未实现的燃气、蒸汽和储能模型。
- 水端口必须显式声明 `SUPPLY` 或 `RETURN`；电和控制信号端口必须使用 `NOT_APPLICABLE`。连接必须满足输出到输入、能源类型一致、水侧一致和端口连接数约束。
- 设备契约本阶段只覆盖设备类型、模块版本、显示名、端口和支持的计算步长范围；参数、状态、公式、指标和运行时输入输出在后续设备/求解子项目中扩展，不能把静态契约描述成设备已经可计算。
- 拓扑基础校验只覆盖标识、引用、方向、介质、水侧、重复连线、端口占用、必需端口和孤立节点；闭式回路、冷热源、泵、边界流量、控制关系、质量/能量守恒与收敛属于后续求解子项目。
- 所有集合进行防御性复制，公共值对象拒绝空值、空白标识和非法范围；校验问题保持确定性顺序并提供中文原因。
- Java 生产代码按 `code-comment-quality` 和 `docs/development/code-comments.md` 检查，只为非显然的契约边界、介质语义和校验原因写简洁中文注释。
- 每个功能任务遵循 Red → Green → Refactor；先运行定向测试确认失败，再写最小实现，再运行定向测试和受影响回归。
- 生产 Java 变化最终必须运行相关定向测试、`GaiaParityTest` 和完整 `.\mvnw.cmd test`；因为不改变打包、CLI、资源、CSV 和图表，本子项目不要求重新生成 JAR、CSV 或 PNG。

## File Map

**Create — public energy and port contract**

- `src/main/java/com/hvac/simulator/energy/EnergyType.java`: 公共能源/信号类型及水介质判断。
- `src/main/java/com/hvac/simulator/device/port/PortDirection.java`: 输入/输出方向。
- `src/main/java/com/hvac/simulator/device/port/WaterSide.java`: 供水、回水或不适用。
- `src/main/java/com/hvac/simulator/device/port/PortCardinality.java`: 可选/必需与单连接/多连接规则。
- `src/main/java/com/hvac/simulator/device/port/PortDefinition.java`: 不可变端口声明及局部不变量。
- `src/test/java/com/hvac/simulator/device/port/PortDefinitionTest.java`: 端口介质、水侧和连接数契约测试。

**Create — independent device contract**

- `src/main/java/com/hvac/simulator/device/DeviceModuleKey.java`: 设备类型与模块版本的稳定复合键。
- `src/main/java/com/hvac/simulator/device/TimeStepCapability.java`: 最小、默认和最大计算步长。
- `src/main/java/com/hvac/simulator/device/DeviceDefinition.java`: 设备显示信息、端口和时间能力快照。
- `src/main/java/com/hvac/simulator/device/DeviceModule.java`: 独立设备模块静态契约。
- `src/main/java/com/hvac/simulator/device/DeviceCatalog.java`: 拓扑校验使用的只读设备目录端口。
- `src/main/java/com/hvac/simulator/device/InMemoryDeviceCatalog.java`: 从设备模块集合构建的内存目录。
- `src/test/java/com/hvac/simulator/device/DeviceContractTest.java`: 设备键、时间步、端口唯一性和目录重复测试。

**Create — topology graph**

- `src/main/java/com/hvac/simulator/topology/TopologyNode.java`: 拓扑节点及其设备模块引用。
- `src/main/java/com/hvac/simulator/topology/TopologyEndpoint.java`: 节点端口引用。
- `src/main/java/com/hvac/simulator/topology/TopologyConnection.java`: 有向连接。
- `src/main/java/com/hvac/simulator/topology/TopologyGraph.java`: 防御性复制节点和连接的不可变图。
- `src/test/java/com/hvac/simulator/topology/TopologyGraphTest.java`: 图不可变性和局部值对象测试。

**Create — topology validation**

- `src/main/java/com/hvac/simulator/topology/validation/TopologyIssueSeverity.java`: `ERROR`/`WARNING`。
- `src/main/java/com/hvac/simulator/topology/validation/TopologyIssueCode.java`: 稳定错误编码。
- `src/main/java/com/hvac/simulator/topology/validation/TopologyIssue.java`: 中文问题及关联元素。
- `src/main/java/com/hvac/simulator/topology/validation/TopologyValidationResult.java`: 不可变问题集合和 `isValid()`。
- `src/main/java/com/hvac/simulator/topology/validation/TopologyValidator.java`: 候选连线和完整拓扑的基础校验。
- `src/test/java/com/hvac/simulator/topology/validation/TopologyValidatorTest.java`: 合法图、非法连线、占用、缺口和警告测试。

**Modify — verified project truth**

- `PROJECT_GUIDE.md`: 在“实际架构”中登记新增逻辑引擎包及明确未实现的求解边界。
- `PROJECT_STATUS.md`: 将第一子项目按实际测试证据标记为已实现，同时保留整个平台和具体设备未实现状态。

---

### Task 1: 建立公共能源与端口契约

**Files:**
- Create: `src/main/java/com/hvac/simulator/energy/EnergyType.java`
- Create: `src/main/java/com/hvac/simulator/device/port/PortDirection.java`
- Create: `src/main/java/com/hvac/simulator/device/port/WaterSide.java`
- Create: `src/main/java/com/hvac/simulator/device/port/PortCardinality.java`
- Create: `src/main/java/com/hvac/simulator/device/port/PortDefinition.java`
- Test: `src/test/java/com/hvac/simulator/device/port/PortDefinitionTest.java`

**Interfaces:**
- Consumes: Java `enum`、`record` 和现有中文异常风格。
- Produces: `EnergyType.isWater()`、`PortCardinality.accepts(int)`、`PortDefinition`。

- [ ] **Step 1: 写失败的端口契约测试**

```java
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
                "chw-out", "冷冻水出水", EnergyType.CHILLED_WATER,
                PortDirection.OUTPUT, WaterSide.NOT_APPLICABLE, PortCardinality.REQUIRED_SINGLE));
    }

    @Test
    void nonWaterPortRejectsWaterSide() {
        assertThrows(IllegalArgumentException.class, () -> new PortDefinition(
                "power-in", "供电输入", EnergyType.ELECTRICITY,
                PortDirection.INPUT, WaterSide.SUPPLY, PortCardinality.REQUIRED_SINGLE));
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
```

- [ ] **Step 2: 运行测试并确认先失败**

Run:

```powershell
.\mvnw.cmd -Dtest=PortDefinitionTest test
```

Expected: FAIL，`EnergyType` 或端口契约类型尚不存在。

- [ ] **Step 3: 实现最小公共类型**

```java
// EnergyType.java
package com.hvac.simulator.energy;

/** 拓扑端口共享的能源或信号介质；是否可连接仍由端口方向和水侧共同决定。 */
public enum EnergyType {
    ELECTRICITY(false),
    CHILLED_WATER(true),
    CONDENSER_WATER(true),
    HOT_WATER(true),
    CONTROL_SIGNAL(false);

    private final boolean water;

    EnergyType(boolean water) {
        this.water = water;
    }

    public boolean isWater() {
        return water;
    }
}
```

```java
// PortDirection.java
package com.hvac.simulator.device.port;

public enum PortDirection {
    INPUT,
    OUTPUT
}
```

```java
// WaterSide.java
package com.hvac.simulator.device.port;

public enum WaterSide {
    NOT_APPLICABLE,
    SUPPLY,
    RETURN
}
```

```java
// PortCardinality.java
package com.hvac.simulator.device.port;

public enum PortCardinality {
    OPTIONAL_SINGLE(0, 1),
    REQUIRED_SINGLE(1, 1),
    OPTIONAL_MULTIPLE(0, Integer.MAX_VALUE),
    REQUIRED_MULTIPLE(1, Integer.MAX_VALUE);

    private final int minimum;
    private final int maximum;

    PortCardinality(int minimum, int maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public boolean accepts(int connectionCount) {
        return connectionCount >= minimum && connectionCount <= maximum;
    }

    public boolean required() {
        return minimum > 0;
    }

    public boolean multiple() {
        return maximum > 1;
    }
}
```

```java
// PortDefinition.java
package com.hvac.simulator.device.port;

import com.hvac.simulator.energy.EnergyType;
import java.util.Objects;

/** 设备对外公开的连接点；水侧用于阻止供水管与回水管被误连。 */
public record PortDefinition(
        String id,
        String displayName,
        EnergyType energyType,
        PortDirection direction,
        WaterSide waterSide,
        PortCardinality cardinality) {

    public PortDefinition {
        requireText(id, "端口编号不能为空");
        requireText(displayName, "端口名称不能为空");
        Objects.requireNonNull(energyType, "端口能源类型不能为空");
        Objects.requireNonNull(direction, "端口方向不能为空");
        Objects.requireNonNull(waterSide, "端口水侧不能为空");
        Objects.requireNonNull(cardinality, "端口连接规则不能为空");
        if (energyType.isWater() == (waterSide == WaterSide.NOT_APPLICABLE)) {
            throw new IllegalArgumentException("水端口必须声明供回水侧，非水端口不得声明供回水侧");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
```

- [ ] **Step 4: 运行端口测试并检查注释**

Run:

```powershell
.\mvnw.cmd -Dtest=PortDefinitionTest test
```

Expected: PASS；异常消息为中文；简单枚举没有冗余逐项注释。

- [ ] **Step 5: 提交公共能源与端口契约**

```powershell
git add -- src/main/java/com/hvac/simulator/energy/EnergyType.java src/main/java/com/hvac/simulator/device/port/PortDirection.java src/main/java/com/hvac/simulator/device/port/WaterSide.java src/main/java/com/hvac/simulator/device/port/PortCardinality.java src/main/java/com/hvac/simulator/device/port/PortDefinition.java src/test/java/com/hvac/simulator/device/port/PortDefinitionTest.java
git diff --cached --check
git commit -m "feat(engine): define shared energy ports"
```

### Task 2: 建立独立设备静态契约和只读目录

**Files:**
- Create: `src/main/java/com/hvac/simulator/device/DeviceModuleKey.java`
- Create: `src/main/java/com/hvac/simulator/device/TimeStepCapability.java`
- Create: `src/main/java/com/hvac/simulator/device/DeviceDefinition.java`
- Create: `src/main/java/com/hvac/simulator/device/DeviceModule.java`
- Create: `src/main/java/com/hvac/simulator/device/DeviceCatalog.java`
- Create: `src/main/java/com/hvac/simulator/device/InMemoryDeviceCatalog.java`
- Test: `src/test/java/com/hvac/simulator/device/DeviceContractTest.java`

**Interfaces:**
- Consumes: `PortDefinition`。
- Produces: `DeviceModule.definition()`、`DeviceCatalog.find(DeviceModuleKey)`、`TimeStepCapability.supports(Duration)`。

- [ ] **Step 1: 写失败的设备契约测试**

```java
package com.hvac.simulator.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hvac.simulator.device.port.PortCardinality;
import com.hvac.simulator.device.port.PortDefinition;
import com.hvac.simulator.device.port.PortDirection;
import com.hvac.simulator.device.port.WaterSide;
import com.hvac.simulator.energy.EnergyType;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeviceContractTest {

    @Test
    void definitionRejectsDuplicatePortIds() {
        var port = electricityInput("power-in");
        assertThrows(IllegalArgumentException.class, () -> definition("CHILLER", List.of(port, port)));
    }

    @Test
    void timeStepCapabilityUsesClosedRange() {
        var capability = new TimeStepCapability(
                Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofMinutes(5));
        assertTrue(capability.supports(Duration.ofSeconds(1)));
        assertTrue(capability.supports(Duration.ofMinutes(5)));
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
    }

    private static DeviceDefinition definition(String type, List<PortDefinition> ports) {
        return new DeviceDefinition(
                new DeviceModuleKey(type, "1.0"), type,
                ports,
                new TimeStepCapability(Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofMinutes(5)));
    }

    private static PortDefinition electricityInput(String id) {
        return new PortDefinition(id, id, EnergyType.ELECTRICITY,
                PortDirection.INPUT, WaterSide.NOT_APPLICABLE, PortCardinality.REQUIRED_SINGLE);
    }
}
```

- [ ] **Step 2: 运行测试并确认先失败**

Run: `.\mvnw.cmd -Dtest=DeviceContractTest test`

Expected: FAIL，设备契约和目录类型尚不存在。

- [ ] **Step 3: 实现设备键、时间能力和定义**

```java
// DeviceModuleKey.java
package com.hvac.simulator.device;

public record DeviceModuleKey(String deviceType, String moduleVersion) {
    public DeviceModuleKey {
        requireText(deviceType, "设备类型不能为空");
        requireText(moduleVersion, "设备模块版本不能为空");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
```

```java
// TimeStepCapability.java
package com.hvac.simulator.device;

import java.time.Duration;
import java.util.Objects;

/** 声明设备可接受的统一任务计算步长；本类型不负责推进设备状态。 */
public record TimeStepCapability(Duration minimum, Duration defaultValue, Duration maximum) {
    public TimeStepCapability {
        Objects.requireNonNull(minimum, "最小计算步长不能为空");
        Objects.requireNonNull(defaultValue, "默认计算步长不能为空");
        Objects.requireNonNull(maximum, "最大计算步长不能为空");
        if (minimum.isZero() || minimum.isNegative()
                || defaultValue.compareTo(minimum) < 0
                || maximum.compareTo(defaultValue) < 0) {
            throw new IllegalArgumentException("计算步长必须满足 0 < 最小值 <= 默认值 <= 最大值");
        }
    }

    public boolean supports(Duration candidate) {
        Objects.requireNonNull(candidate, "待检查计算步长不能为空");
        return !candidate.isNegative() && !candidate.isZero()
                && candidate.compareTo(minimum) >= 0
                && candidate.compareTo(maximum) <= 0;
    }
}
```

```java
// DeviceDefinition.java
package com.hvac.simulator.device;

import com.hvac.simulator.device.port.PortDefinition;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 独立设备模块的静态公开契约；公式、状态和运行时值由后续执行契约承载。 */
public record DeviceDefinition(
        DeviceModuleKey key,
        String displayName,
        List<PortDefinition> ports,
        TimeStepCapability timeStepCapability) {

    public DeviceDefinition {
        Objects.requireNonNull(key, "设备模块键不能为空");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("设备名称不能为空");
        }
        Objects.requireNonNull(ports, "设备端口不能为空");
        ports = List.copyOf(ports);
        if (ports.isEmpty()) {
            throw new IllegalArgumentException("设备至少需要一个端口");
        }
        var ids = new HashSet<String>();
        for (var port : ports) {
            Objects.requireNonNull(port, "设备端口不能包含空值");
            if (!ids.add(port.id())) {
                throw new IllegalArgumentException("设备端口编号重复: " + port.id());
            }
        }
        Objects.requireNonNull(timeStepCapability, "设备时间步能力不能为空");
    }

    public Optional<PortDefinition> findPort(String portId) {
        return ports.stream().filter(port -> port.id().equals(portId)).findFirst();
    }
}
```

- [ ] **Step 4: 实现模块接口和内存目录**

```java
// DeviceModule.java
package com.hvac.simulator.device;

@FunctionalInterface
public interface DeviceModule {
    DeviceDefinition definition();
}
```

```java
// DeviceCatalog.java
package com.hvac.simulator.device;

import java.util.Optional;

@FunctionalInterface
public interface DeviceCatalog {
    Optional<DeviceDefinition> find(DeviceModuleKey key);
}
```

```java
// InMemoryDeviceCatalog.java
package com.hvac.simulator.device;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 为纯 Java 引擎装配已发布设备定义；重复版本键必须在启动装配阶段失败。 */
public final class InMemoryDeviceCatalog implements DeviceCatalog {
    private final Map<DeviceModuleKey, DeviceDefinition> definitions;

    private InMemoryDeviceCatalog(Map<DeviceModuleKey, DeviceDefinition> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    public static InMemoryDeviceCatalog fromModules(Collection<? extends DeviceModule> modules) {
        Objects.requireNonNull(modules, "设备模块集合不能为空");
        var definitions = new LinkedHashMap<DeviceModuleKey, DeviceDefinition>();
        for (var module : modules) {
            Objects.requireNonNull(module, "设备模块集合不能包含空值");
            var definition = Objects.requireNonNull(module.definition(), "设备模块定义不能为空");
            if (definitions.putIfAbsent(definition.key(), definition) != null) {
                throw new IllegalArgumentException("设备模块键重复: " + definition.key());
            }
        }
        return new InMemoryDeviceCatalog(definitions);
    }

    @Override
    public Optional<DeviceDefinition> find(DeviceModuleKey key) {
        Objects.requireNonNull(key, "设备模块键不能为空");
        return Optional.ofNullable(definitions.get(key));
    }
}
```

- [ ] **Step 5: 运行设备契约测试并提交**

Run: `.\mvnw.cmd -Dtest=DeviceContractTest test`

Expected: PASS。

```powershell
git add -- src/main/java/com/hvac/simulator/device/DeviceModuleKey.java src/main/java/com/hvac/simulator/device/TimeStepCapability.java src/main/java/com/hvac/simulator/device/DeviceDefinition.java src/main/java/com/hvac/simulator/device/DeviceModule.java src/main/java/com/hvac/simulator/device/DeviceCatalog.java src/main/java/com/hvac/simulator/device/InMemoryDeviceCatalog.java src/test/java/com/hvac/simulator/device/DeviceContractTest.java
git diff --cached --check
git commit -m "feat(engine): define versioned device contract"
```

### Task 3: 建立不可变拓扑图值对象

**Files:**
- Create: `src/main/java/com/hvac/simulator/topology/TopologyNode.java`
- Create: `src/main/java/com/hvac/simulator/topology/TopologyEndpoint.java`
- Create: `src/main/java/com/hvac/simulator/topology/TopologyConnection.java`
- Create: `src/main/java/com/hvac/simulator/topology/TopologyGraph.java`
- Test: `src/test/java/com/hvac/simulator/topology/TopologyGraphTest.java`

**Interfaces:**
- Consumes: `DeviceModuleKey`。
- Produces: `TopologyGraph.nodes()`、`TopologyGraph.connections()` 和稳定节点/端口引用。

- [ ] **Step 1: 写失败的拓扑图测试**

```java
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
    void endpointRejectsBlankNodeOrPort() {
        assertThrows(IllegalArgumentException.class, () -> new TopologyEndpoint(" ", "power-out"));
        assertThrows(IllegalArgumentException.class, () -> new TopologyEndpoint("source", " "));
    }

    private static TopologyNode node(String id) {
        return new TopologyNode(id, id, new DeviceModuleKey("TEST_DEVICE", "1.0"));
    }

    private static TopologyConnection connection(String id) {
        return new TopologyConnection(id,
                new TopologyEndpoint("source", "power-out"),
                new TopologyEndpoint("load", "power-in"));
    }
}
```

- [ ] **Step 2: 运行测试并确认先失败**

Run: `.\mvnw.cmd -Dtest=TopologyGraphTest test`

Expected: FAIL，拓扑值对象尚不存在。

- [ ] **Step 3: 实现节点、端点和连接**

```java
// TopologyNode.java
package com.hvac.simulator.topology;

import com.hvac.simulator.device.DeviceModuleKey;
import java.util.Objects;

public record TopologyNode(String id, String displayName, DeviceModuleKey moduleKey) {
    public TopologyNode {
        requireText(id, "拓扑节点编号不能为空");
        requireText(displayName, "拓扑节点名称不能为空");
        Objects.requireNonNull(moduleKey, "拓扑节点设备模块不能为空");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
```

```java
// TopologyEndpoint.java
package com.hvac.simulator.topology;

public record TopologyEndpoint(String nodeId, String portId) {
    public TopologyEndpoint {
        requireText(nodeId, "端点节点编号不能为空");
        requireText(portId, "端点端口编号不能为空");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
```

```java
// TopologyConnection.java
package com.hvac.simulator.topology;

import java.util.Objects;

public record TopologyConnection(String id, TopologyEndpoint source, TopologyEndpoint target) {
    public TopologyConnection {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("拓扑连线编号不能为空");
        }
        Objects.requireNonNull(source, "连线起点不能为空");
        Objects.requireNonNull(target, "连线终点不能为空");
    }
}
```

- [ ] **Step 4: 实现不可变拓扑图**

```java
package com.hvac.simulator.topology;

import java.util.List;
import java.util.Objects;

/** 保存用户方案的原始节点和连线；跨对象错误由校验器一次性汇总。 */
public record TopologyGraph(List<TopologyNode> nodes, List<TopologyConnection> connections) {
    public TopologyGraph {
        Objects.requireNonNull(nodes, "拓扑节点集合不能为空");
        Objects.requireNonNull(connections, "拓扑连线集合不能为空");
        nodes = List.copyOf(nodes);
        connections = List.copyOf(connections);
        if (nodes.stream().anyMatch(Objects::isNull) || connections.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("拓扑集合不能包含空值");
        }
    }
}
```

保留重复节点编号、重复连线编号和悬空引用进入 `TopologyGraph`，因为这些是启动前校验需要汇总并关联到画布对象的问题，不在构造时提前吞掉其他错误。

- [ ] **Step 5: 运行拓扑图测试并提交**

Run: `.\mvnw.cmd -Dtest=TopologyGraphTest test`

Expected: PASS。

```powershell
git add -- src/main/java/com/hvac/simulator/topology/TopologyNode.java src/main/java/com/hvac/simulator/topology/TopologyEndpoint.java src/main/java/com/hvac/simulator/topology/TopologyConnection.java src/main/java/com/hvac/simulator/topology/TopologyGraph.java src/test/java/com/hvac/simulator/topology/TopologyGraphTest.java
git diff --cached --check
git commit -m "feat(engine): add immutable topology graph"
```

### Task 4: 定义稳定校验结果并实现候选连线校验

**Files:**
- Create: `src/main/java/com/hvac/simulator/topology/validation/TopologyIssueSeverity.java`
- Create: `src/main/java/com/hvac/simulator/topology/validation/TopologyIssueCode.java`
- Create: `src/main/java/com/hvac/simulator/topology/validation/TopologyIssue.java`
- Create: `src/main/java/com/hvac/simulator/topology/validation/TopologyValidationResult.java`
- Create: `src/main/java/com/hvac/simulator/topology/validation/TopologyValidator.java`
- Test: `src/test/java/com/hvac/simulator/topology/validation/TopologyValidatorTest.java`

**Interfaces:**
- Consumes: `TopologyGraph`、`TopologyConnection`、`DeviceCatalog`、`PortDefinition`。
- Produces: `TopologyValidator.validateConnection(TopologyGraph, TopologyConnection)` 和稳定问题编码。

- [ ] **Step 1: 写候选连线失败测试和合法测试**

在 `TopologyValidatorTest` 建立两个测试设备定义：电源设备有 `power-out`，负载设备有 `power-in`；水源/负载分别使用冷冻水 `SUPPLY` 输出/输入。写出以下断言：

```java
@Test
void validOutputToInputConnectionPasses() {
    var result = validator().validateConnection(baseGraph(), powerConnection("line-1"));
    assertTrue(result.isValid());
    assertTrue(result.issues().isEmpty());
}

@Test
void connectionRejectsUnknownNodeWrongDirectionEnergyAndWaterSide() {
    assertHasCode(connectionToUnknownNode(), TopologyIssueCode.UNKNOWN_TARGET_NODE);
    assertHasCode(inputToOutputConnection(), TopologyIssueCode.INVALID_DIRECTION);
    assertHasCode(electricityToWaterConnection(), TopologyIssueCode.INCOMPATIBLE_ENERGY_TYPE);
    assertHasCode(supplyToReturnConnection(), TopologyIssueCode.INCOMPATIBLE_WATER_SIDE);
}

@Test
void secondConnectionRejectsOccupiedSinglePort() {
    var graph = new TopologyGraph(baseGraph().nodes(), List.of(powerConnection("existing")));
    var result = validator().validateConnection(graph, powerConnection("candidate"));
    assertHasCode(result, TopologyIssueCode.SOURCE_PORT_OCCUPIED);
    assertHasCode(result, TopologyIssueCode.TARGET_PORT_OCCUPIED);
}
```

测试辅助方法必须在该测试类中完整创建 `DeviceDefinition`、`InMemoryDeviceCatalog`、节点、端口和连接，不依赖 Gaia 测试夹具。

- [ ] **Step 2: 运行测试并确认先失败**

Run: `.\mvnw.cmd -Dtest=TopologyValidatorTest test`

Expected: FAIL，校验结果和校验器尚不存在。

- [ ] **Step 3: 实现校验结果类型**

```java
// TopologyIssueSeverity.java
package com.hvac.simulator.topology.validation;

public enum TopologyIssueSeverity {
    ERROR,
    WARNING
}
```

```java
// TopologyIssueCode.java
package com.hvac.simulator.topology.validation;

public enum TopologyIssueCode {
    DUPLICATE_NODE_ID,
    DUPLICATE_CONNECTION_ID,
    DUPLICATE_CONNECTION_ENDPOINTS,
    UNKNOWN_DEVICE_MODULE,
    UNKNOWN_SOURCE_NODE,
    UNKNOWN_TARGET_NODE,
    UNKNOWN_SOURCE_PORT,
    UNKNOWN_TARGET_PORT,
    SELF_CONNECTION,
    INVALID_DIRECTION,
    INCOMPATIBLE_ENERGY_TYPE,
    INCOMPATIBLE_WATER_SIDE,
    SOURCE_PORT_OCCUPIED,
    TARGET_PORT_OCCUPIED,
    REQUIRED_PORT_UNCONNECTED,
    ISOLATED_NODE
}
```

```java
// TopologyIssue.java
package com.hvac.simulator.topology.validation;

import java.util.Objects;

public record TopologyIssue(
        TopologyIssueCode code,
        TopologyIssueSeverity severity,
        String message,
        String elementId) {

    public TopologyIssue {
        Objects.requireNonNull(code, "拓扑问题编码不能为空");
        Objects.requireNonNull(severity, "拓扑问题级别不能为空");
        if (message == null || message.isBlank() || elementId == null || elementId.isBlank()) {
            throw new IllegalArgumentException("拓扑问题说明和关联元素不能为空");
        }
    }
}
```

```java
// TopologyValidationResult.java
package com.hvac.simulator.topology.validation;

import java.util.List;
import java.util.Objects;

public record TopologyValidationResult(List<TopologyIssue> issues) {
    public TopologyValidationResult {
        Objects.requireNonNull(issues, "拓扑问题集合不能为空");
        issues = List.copyOf(issues);
        if (issues.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("拓扑问题集合不能包含空值");
        }
    }

    public boolean isValid() {
        return issues.stream().noneMatch(issue -> issue.severity() == TopologyIssueSeverity.ERROR);
    }
}
```

- [ ] **Step 4: 实现候选连线校验**

`TopologyValidator` 构造器接收非空 `DeviceCatalog`。`validateConnection(graph, candidate)` 按固定顺序执行并返回全部可判断问题：

1. 候选连线编号是否已存在；同一起止端点是否已存在。
2. 起止节点是否存在且编号是否相同。
3. 起止节点引用的设备模块是否能从目录解析。
4. 起止端口是否存在。
5. 起点是否为 `OUTPUT`、终点是否为 `INPUT`。
6. `EnergyType` 是否一致；水介质的 `WaterSide` 是否一致。
7. 加入候选连线后，起止端口连接数量是否仍被各自 `PortCardinality` 接受。

未知节点、模块或端口会阻止依赖该对象的后续检查，但不会阻止另一端继续报告可独立判断的问题。所有错误使用候选连线编号作为 `elementId`，中文消息包含节点和端口编号。实现中使用私有 `indexUniqueNodes`、`resolvePort` 和 `connectionCount` 方法，不加入求解、自动修复或默认连接。

- [ ] **Step 5: 运行候选连线测试并提交**

Run: `.\mvnw.cmd -Dtest=TopologyValidatorTest test`

Expected: PASS；合法连接无问题，四类兼容错误和单连接占用均返回指定稳定编码。

```powershell
git add -- src/main/java/com/hvac/simulator/topology/validation src/test/java/com/hvac/simulator/topology/validation/TopologyValidatorTest.java
git diff --cached --check
git commit -m "feat(engine): validate topology connections"
```

### Task 5: 扩展启动前完整拓扑基础校验

**Files:**
- Modify: `src/main/java/com/hvac/simulator/topology/validation/TopologyValidator.java`
- Modify: `src/test/java/com/hvac/simulator/topology/validation/TopologyValidatorTest.java`

**Interfaces:**
- Consumes: Task 4 的问题编码和候选连线规则。
- Produces: `TopologyValidator.validateGraph(TopologyGraph)`。

- [ ] **Step 1: 写完整拓扑失败测试**

```java
@Test
void graphReportsDuplicateIdsAndUnknownModule() {
    var duplicate = new TopologyNode("source", "重复节点", SOURCE_KEY);
    var unknown = new TopologyNode("unknown", "未知设备", new DeviceModuleKey("UNKNOWN", "1.0"));
    var graph = new TopologyGraph(
            List.of(sourceNode(), duplicate, unknown),
            List.of(powerConnection("same"), powerConnection("same")));

    var result = validator().validateGraph(graph);

    assertHasCode(result, TopologyIssueCode.DUPLICATE_NODE_ID);
    assertHasCode(result, TopologyIssueCode.DUPLICATE_CONNECTION_ID);
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
```

- [ ] **Step 2: 运行测试并确认先失败**

Run: `.\mvnw.cmd -Dtest=TopologyValidatorTest test`

Expected: FAIL，`validateGraph` 尚未实现或未报告所需问题。

- [ ] **Step 3: 实现启动前基础校验**

`validateGraph(graph)` 必须按以下确定顺序汇总：

1. 按节点出现顺序报告重复节点编号。
2. 按连线出现顺序报告重复连线编号和完全相同的起止端点。
3. 对唯一节点解析设备模块，报告 `UNKNOWN_DEVICE_MODULE`。
4. 对每条连线复用 Task 4 的端点、方向、能源、水侧规则；图内既有连线不得被误报为“候选编号已存在”。
5. 汇总每个已解析端口的连接数；超过单连接上限时报占用错误，必需端口为零时报 `REQUIRED_PORT_UNCONNECTED`。
6. 节点没有任何有效或无效连线引用时报告 `ISOLATED_NODE`，级别为 `WARNING`；警告本身不使 `isValid()` 失败，但缺失必需端口仍是 `ERROR`。

重复标识导致索引不唯一时，后续语义检查只使用首次出现对象，同时保留重复标识错误，确保结果确定且不会抛出未说明异常。该方法不检查闭环、水泵、冷热源、边界流量、时间能力交集或守恒。

- [ ] **Step 4: 运行全部新引擎测试**

Run:

```powershell
.\mvnw.cmd -Dtest=PortDefinitionTest,DeviceContractTest,TopologyGraphTest,TopologyValidatorTest test
```

Expected: PASS；测试不依赖系统当前时间、随机输入、个人路径或外部服务。

- [ ] **Step 5: 提交完整拓扑基础校验**

```powershell
git add -- src/main/java/com/hvac/simulator/topology/validation/TopologyValidator.java src/test/java/com/hvac/simulator/topology/validation/TopologyValidatorTest.java
git diff --cached --check
git commit -m "feat(engine): validate topology graph structure"
```

### Task 6: 同步项目事实并完成回归与交付检查

**Files:**
- Modify: `PROJECT_GUIDE.md`
- Modify: `PROJECT_STATUS.md`

**Interfaces:**
- Consumes: Tasks 1–5 的实际代码和本次测试证据。
- Produces: 明确区分“基础契约已实现”和“设备计算/拓扑求解/平台能力未实现”的当前项目说明。

- [ ] **Step 1: 先运行 Gaia 1.0 定向回归**

Run:

```powershell
.\mvnw.cmd -Dtest=GaiaParityTest test
```

Expected: PASS；冻结的 10,080 行、17 字段 Gaia 1.0 逐时间步对照没有退化。

- [ ] **Step 2: 运行完整测试**

Run:

```powershell
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`，全部现有 Gaia 测试和新增引擎基础测试通过。

- [ ] **Step 3: 同步稳定架构和当前状态**

在 `PROJECT_GUIDE.md` 的实际包职责表中增加：

```markdown
| `com.hvac.simulator.energy` | 公共电、水和控制信号能源类型，不包含具体设备公式 |
| `com.hvac.simulator.device` | 版本化独立设备静态契约、时间步能力和只读设备目录 |
| `com.hvac.simulator.topology` | 不可变节点、端口连接图和基础结构校验，不包含完整水力或守恒求解 |
```

同时明确当前仍是单 Maven 模块纯 Java CLI，`server` 和 `web` 尚未建立。

在 `PROJECT_STATUS.md` 中新增经过本次测试证据支持的完成项：公共能源类型、设备静态契约、端口、拓扑图、候选连线校验和启动前基础结构校验；在尚未完成项继续列出具体中央空调设备模块、执行图、闭环迭代、守恒、完整水力、Spring Boot、Vue、数据库和 MQTT。不得把本子项目写成自由拓扑平台已可运行。

- [ ] **Step 4: 执行注释、范围和差异检查**

Run:

```powershell
git diff --check
git status --short
git diff --name-only origin/main...HEAD
git diff -- src/main/java/com/hvac/simulator/energy src/main/java/com/hvac/simulator/device src/main/java/com/hvac/simulator/topology PROJECT_GUIDE.md PROJECT_STATUS.md
```

Expected:

- 仅出现本计划列出的新包、测试、计划和两份项目文档；
- 无 `target/`、`output/`、CSV、PNG、个人路径或敏感信息；
- Gaia 1.0 原生产包和冻结基准没有内容差异；
- 关键契约注释说明边界和原因，简单值对象无重复注释。

- [ ] **Step 5: 提交文档和最终检查**

```powershell
git add -- PROJECT_GUIDE.md PROJECT_STATUS.md
git diff --cached --check
git diff --cached
git commit -m "docs(project): record topology engine foundation"
```

- [ ] **Step 6: 推送任务分支并准备 PR 材料**

```powershell
git status --short --branch
git log --oneline origin/main..HEAD
git push -u origin codex/generic-topology-engine-foundation
```

Expected: 工作区干净，任务分支只领先本子项目提交，远程分支创建成功。交付材料必须包含：目标、实现范围、明确非目标、定向测试、Gaia 1.0 回归、完整测试、未执行的 JAR/CSV/PNG 原因、风险和 Compare/PR 链接；未实际创建或合并 PR 时不得声称已进入 `main`。

## Plan Self-Review

- 规格覆盖：公共能源、独立设备静态契约、端口、拓扑图、即时连线校验和启动前基础结构校验均有对应任务。
- 范围隔离：没有 Spring Boot、Vue、数据库、MQTT、设备公式、Gaia 1.1 实现、执行图、守恒或完整水力求解。
- 基准保护：没有计划修改 Gaia 1.0 源码、冻结 CSV、参考资产、输出字段、时间语义或容差；明确运行 `GaiaParityTest` 和完整测试。
- 类型一致：`DeviceModuleKey`、`DeviceDefinition`、`DeviceCatalog`、`PortDefinition` 和 `TopologyValidator` 的名称和签名在各任务中一致。
- 交付边界：计划文件先由用户确认；确认后才执行 Tasks 1–6 的编码、测试、提交和推送。
