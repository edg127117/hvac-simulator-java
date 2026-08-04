# Gaia 1.0 Java 忠实转换 Implementation Plan

> 状态：已实施、功能分支已发布，合并状态以 `PROJECT_STATUS.md` 和当前 Git 为准。

> 实施说明：功能、测试和运行验证均已完成；计划中要求单独保存“先失败”日志的步骤未保留独立证据，因此对应复选框保持未勾选。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用 Java 21 忠实转换 Gaia 1.0 仿真，完成 Python 基准全量对照、Java 合成气象、17 字段 CSV、中文三联图和可执行 JAR。

**Architecture:** 普通 Java CLI 通过 `WeatherSource` 切换基准气象和 Java 合成气象；`Simulator` 只编排 `BuildingThermalModel` 与 `HvacSystem`，输出组件只消费 `SimulationResult`。Python 基准 CSV 同时提供四列气象输入和其余字段预期值，Java 禁止复制预期计算结果。

**Tech Stack:** Java 21、Maven 3.9.16、Maven Wrapper 3.3.4、XChart 4.0.3、JUnit 6.1.2、Surefire 3.5.5、Compiler Plugin 3.15.0、Shade Plugin 3.6.2。

## Global Constraints

- 保留 Gaia 1.0 的默认参数、公式、计算顺序、1 分钟步长和 2024-07-01 至 2024-07-07 基准。
- 不修正季节定位、管道热量符号、回水温度沿用、1 分钟启停、停机 COP=0 等现有行为。
- 基准模式完整比较 10,080 行和 17 字段；数值容差为 `max(1e-9, abs(expected) * 1e-9)`。
- 基准模式默认运行；合成模式使用固定 Java 随机种子并保持可重复。
- CSV、PNG 先写临时文件再原子替换；错误不能静默跳过或降级。
- 生产类和关键公式使用简洁中文注释，说明单位、符号、物理假设和兼容原因。
- 不引入 Spring Boot、Lombok、第三方 CSV 库、数据库或 Web 依赖。
- `output/`、`target/` 和临时文件不得提交。

---

### Task 1: 建立 Maven 工程和参考基线

**Files:**
- Create: `pom.xml`
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Create: `mvnw`
- Create: `mvnw.cmd`
- Create: `reference/gaia-1.0/Gaia1.0.py`
- Create: `reference/gaia-1.0/README.md`
- Create: `reference/gaia-1.0/python-reference-plot.png`
- Create: `src/main/resources/gaia-baseline/python-results.csv`
- Create: `src/test/java/com/hvac/simulator/BuildSmokeTest.java`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: 已确认设计和当前 Python 基准文件。
- Produces: Java 21 Maven 构建、可追踪 Python 基线、JUnit 测试入口。

- [x] **Step 1: 写构建冒烟测试**

```java
package com.hvac.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class BuildSmokeTest {
    @Test
    void usesJava21OrNewer() {
        assertEquals(21, Runtime.version().feature());
    }
}
```

- [x] **Step 2: 创建 `pom.xml` 和 Wrapper**

`pom.xml` 设置 `maven.compiler.release=21`，导入 `junit-bom:6.1.2`，依赖 `xchart:4.0.3` 和测试范围 `junit-jupiter`；固定 Compiler 3.15.0、Surefire 3.5.5、Shade 3.6.2，Shade 主类为 `com.hvac.simulator.app.GaiaSimulatorApplication`。

Run:

```powershell
mvn wrapper:wrapper -Dmaven=3.9.16 -Dtype=only-script
.\mvnw.cmd test
```

Expected: `BuildSmokeTest` 通过，Wrapper 使用 Maven 3.9.16。

- [x] **Step 3: 纳入参考文件并核验**

复制 Gaia 源文件、Python 基准 CSV 和中文参考图到计划路径。`reference/gaia-1.0/README.md` 写入：源文件 SHA-256 `D18064BBF6756EA42B694FA8526F5232D61834D2B29DC153748D5C9EC5C24BEF`、Python 3.12.13、NumPy 2.5.1、Pandas 3.0.1、Matplotlib 3.11.1、随机种子未固定和字体修复边界。

Run:

```powershell
Get-FileHash reference\gaia-1.0\Gaia1.0.py -Algorithm SHA256
Import-Csv src\main\resources\gaia-baseline\python-results.csv | Measure-Object
```

Expected: 哈希完全一致，CSV 为 10,080 行。

- [x] **Step 4: 提交构建与基线**

```powershell
git add -- .gitignore pom.xml mvnw mvnw.cmd .mvn/wrapper reference/gaia-1.0 src/main/resources/gaia-baseline src/test/java/com/hvac/simulator/BuildSmokeTest.java
git diff --cached --check
git commit -m "chore(build): establish Java simulator baseline"
```

### Task 2: 定义不可变配置和气象契约

**Files:**
- Create: `src/main/java/com/hvac/simulator/config/SimulationConfig.java`
- Create: `src/main/java/com/hvac/simulator/config/WeatherParameters.java`
- Create: `src/main/java/com/hvac/simulator/config/BuildingEnvelope.java`
- Create: `src/main/java/com/hvac/simulator/config/InternalLoad.java`
- Create: `src/main/java/com/hvac/simulator/config/HvacParameters.java`
- Create: `src/main/java/com/hvac/simulator/weather/WeatherSource.java`
- Create: `src/main/java/com/hvac/simulator/weather/WeatherPoint.java`
- Create: `src/main/java/com/hvac/simulator/weather/WeatherSeries.java`
- Create: `src/test/java/com/hvac/simulator/config/GaiaDefaultsTest.java`

**Interfaces:**
- Consumes: Java 标准时间类型。
- Produces: `SimulationConfig.gaiaDemo(long)`、全部 `gaiaDefaults()`、`WeatherSource.load(SimulationConfig)`。

- [x] **Step 1: 写默认参数测试**

```java
@Test
void gaiaDemoUsesConfirmedRange() {
    var config = SimulationConfig.gaiaDemo(42L);
    assertEquals(LocalDateTime.of(2024, 7, 1, 0, 0), config.start());
    assertEquals(LocalDateTime.of(2024, 7, 7, 23, 59), config.end());
    assertEquals(1, config.dtMinutes());
    assertEquals(10_080, config.expectedSteps());
}

@Test
void gaiaHvacDefaultsPreserveRatedValues() {
    var p = HvacParameters.gaiaDefaults();
    assertEquals(1400.0, p.chillerRatedCapacityKw());
    assertEquals(6.0, p.chillerRatedCop());
    assertEquals(25.0, p.coolingSetpointC());
    assertEquals(2.0, p.deadbandC());
}
```

- [ ] **Step 2: 验证测试先失败**

Run: `.\mvnw.cmd -Dtest=GaiaDefaultsTest test`

Expected: FAIL，配置类型尚不存在。

- [x] **Step 3: 实现配置和气象契约**

```java
public record SimulationConfig(LocalDateTime start, LocalDateTime end, int dtMinutes, long randomSeed) {
    public SimulationConfig {
        if (start == null || end == null || end.isBefore(start) || dtMinutes <= 0) {
            throw new IllegalArgumentException("仿真时间范围或步长无效");
        }
    }

    public static SimulationConfig gaiaDemo(long seed) {
        return new SimulationConfig(
                LocalDateTime.of(2024, 7, 1, 0, 0),
                LocalDateTime.of(2024, 7, 7, 23, 59), 1, seed);
    }

    public int expectedSteps() {
        return Math.toIntExact(Duration.between(start, end).toMinutes() / dtMinutes + 1);
    }
}
```

`WeatherPoint` 使用 `timestamp`、`dryBulbC`、`wetBulbC`、`solarGlobalWPerM2`；`WeatherSeries` 防御性复制列表并检查非空和有限值。

- [x] **Step 4: 运行配置测试并提交**

Run: `.\mvnw.cmd -Dtest=GaiaDefaultsTest test`

Expected: PASS。

```powershell
git add -- src/main/java/com/hvac/simulator/config src/main/java/com/hvac/simulator/weather/WeatherSource.java src/main/java/com/hvac/simulator/weather/WeatherPoint.java src/main/java/com/hvac/simulator/weather/WeatherSeries.java src/test/java/com/hvac/simulator/config/GaiaDefaultsTest.java
git commit -m "feat(config): define Gaia simulation parameters"
```

### Task 3: 实现两种气象来源

**Files:**
- Create: `src/main/java/com/hvac/simulator/weather/BaselineWeatherSource.java`
- Create: `src/main/java/com/hvac/simulator/weather/SyntheticWeatherGenerator.java`
- Create: `src/test/java/com/hvac/simulator/weather/BaselineWeatherSourceTest.java`
- Create: `src/test/java/com/hvac/simulator/weather/SyntheticWeatherGeneratorTest.java`

**Interfaces:**
- Consumes: `SimulationConfig`、`WeatherParameters`、`WeatherSource`。
- Produces: 两个 `WeatherSource` 实现，均返回按时间升序的 `WeatherSeries`。

- [x] **Step 1: 写基准加载和合成可重复测试**

```java
@Test
void baselineLoadsConfirmedWeatherColumns() throws Exception {
    var series = new BaselineWeatherSource("gaia-baseline/python-results.csv")
            .load(SimulationConfig.gaiaDemo(42L));
    assertEquals(10_080, series.points().size());
    assertEquals(LocalDateTime.of(2024, 7, 1, 0, 0), series.points().getFirst().timestamp());
}

@Test
void syntheticWeatherRepeatsWithSameSeed() throws Exception {
    var config = SimulationConfig.gaiaDemo(42L);
    assertEquals(
            new SyntheticWeatherGenerator(WeatherParameters.gaiaDefaults()).load(config),
            new SyntheticWeatherGenerator(WeatherParameters.gaiaDefaults()).load(config));
}
```

- [ ] **Step 2: 运行并确认失败**

Run: `.\mvnw.cmd -Dtest=BaselineWeatherSourceTest,SyntheticWeatherGeneratorTest test`

Expected: FAIL，两个实现尚不存在。

- [x] **Step 3: 实现基准加载器**

按 UTF-8 读取完整 17 字段表头，只构造四个气象字段；逐行检查时间戳等于 `config.start().plusMinutes(index * dtMinutes)`，检查 10,080 行和所有气象数值有限。异常信息包含资源名、行号和字段。

- [x] **Step 4: 实现合成气象生成器**

使用长度为 `expectedSteps()` 的数组分三阶段生成：全部干球高斯扰动、全部湿球高斯扰动、全部云量均匀扰动。公式逐项使用 Gaia 的 `18 - 12*cos(...)`、`5*sin(...)`、湿差、太阳赤纬、时角、大气质量和非负截断；随机源为 `new Random(config.randomSeed())`。

- [x] **Step 5: 运行测试并提交**

Run: `.\mvnw.cmd -Dtest=BaselineWeatherSourceTest,SyntheticWeatherGeneratorTest test`

Expected: PASS。

```powershell
git add -- src/main/java/com/hvac/simulator/weather src/test/java/com/hvac/simulator/weather
git commit -m "feat(weather): add baseline and synthetic sources"
```

### Task 4: 转换建筑热模型

**Files:**
- Create: `src/main/java/com/hvac/simulator/model/InternalGains.java`
- Create: `src/main/java/com/hvac/simulator/model/BuildingThermalModel.java`
- Create: `src/test/java/com/hvac/simulator/model/BuildingThermalModelTest.java`

**Interfaces:**
- Consumes: `BuildingEnvelope`、`InternalLoad`。
- Produces: `internalGains`、`outdoorAirLoad`、`netSensibleGainWithoutHvac`、`step`。

- [x] **Step 1: 写公式特征测试**

```java
@Test
void weekdayOfficeHourUsesFullInternalLoad() {
    var model = new BuildingThermalModel(BuildingEnvelope.gaiaDefaults(), InternalLoad.gaiaDefaults());
    var gains = model.internalGains(9.0, true);
    assertEquals(305_000.0, gains.sensibleW(), 1e-9);
    assertEquals(55_000.0, gains.latentW(), 1e-9);
}

@Test
void stepUsesGaiaEulerBalance() {
    var model = new BuildingThermalModel(BuildingEnvelope.gaiaDefaults(), InternalLoad.gaiaDefaults());
    double actual = model.step(25.0, 10.0, 0.0, 0.0, true, 0.0, 60.0);
    assertEquals(19.7998, actual, 1e-9);
}
```

- [ ] **Step 2: 运行并确认失败**

Run: `.\mvnw.cmd -Dtest=BuildingThermalModelTest test`

Expected: FAIL，模型尚不存在。

- [x] **Step 3: 实现建筑模型**

固定墙面积 3000 m²、窗面积 1500 m²、屋顶面积 1200 m²、空气密度 1.2 kg/m³、比热 1005 J/(kg·K)，保留工作日 8-18 时系数 1.0、周末 10-16 时系数 0.5。`step` 严格按内部显热、太阳得热、围护传热、渗透显热、供冷量和欧拉积分顺序计算。

- [x] **Step 4: 运行测试并提交**

Run: `.\mvnw.cmd -Dtest=BuildingThermalModelTest test`

Expected: PASS。

```powershell
git add -- src/main/java/com/hvac/simulator/model/InternalGains.java src/main/java/com/hvac/simulator/model/BuildingThermalModel.java src/test/java/com/hvac/simulator/model/BuildingThermalModelTest.java
git commit -m "feat(model): port Gaia building thermal balance"
```

### Task 5: 转换 HVAC 系统

**Files:**
- Create: `src/main/java/com/hvac/simulator/model/ChillerResult.java`
- Create: `src/main/java/com/hvac/simulator/model/CoolingTowerResult.java`
- Create: `src/main/java/com/hvac/simulator/model/HvacStepResult.java`
- Create: `src/main/java/com/hvac/simulator/model/HvacSystem.java`
- Create: `src/test/java/com/hvac/simulator/model/HvacSystemTest.java`

**Interfaces:**
- Consumes: `HvacParameters` 和单步负荷、温度、回水状态。
- Produces: 冷机、水泵、冷却塔、管道、末端和总功率的 `HvacStepResult`。

- [x] **Step 1: 写停机与运行特征测试**

```java
@Test
void noDemandStopsAllEquipmentAndSetsCopToZero() {
    var result = new HvacSystem(HvacParameters.gaiaDefaults())
            .simulate(0.0, 25.0, 10.0, 5.0, 60.0, 12.0);
    assertEquals(0.0, result.systemTotalPowerKw());
    assertEquals(0.0, result.chillerCop());
    assertEquals(7.0, result.chilledWaterSupplyC());
}

@Test
void chillerClampsPlrToGaiaMinimum() {
    var result = new HvacSystem(HvacParameters.gaiaDefaults())
            .calculateChiller(10.0, 32.0, 60.0);
    assertEquals(0.1, result.plr());
}
```

- [ ] **Step 2: 运行并确认失败**

Run: `.\mvnw.cmd -Dtest=HvacSystemTest test`

Expected: FAIL，HVAC 类型尚不存在。

- [x] **Step 3: 实现 HVAC 公式**

逐项转换 `calc_chiller`、`calc_pump_power`、`calc_cooling_tower`、`calc_pipe_heat_loss` 和 `system_simulation`。保留管道 `heatLoss=(fluid-ambient)/resistance` 的负号结果、冷却侧先用 COP≈5 估算再计算实际散热、至少一台 FCU、回水沿用上一时刻和 `dtSeconds` 未参与冷机公式。

- [x] **Step 4: 补充数值断言并运行**

为额定负荷、变频水泵、冷却塔和管道热量添加来自 Python 单方法计算的固定预期值，容差 `1e-9`。

Run: `.\mvnw.cmd -Dtest=HvacSystemTest test`

Expected: PASS。

- [x] **Step 5: 提交 HVAC 模型**

```powershell
git add -- src/main/java/com/hvac/simulator/model src/test/java/com/hvac/simulator/model/HvacSystemTest.java
git commit -m "feat(model): port Gaia HVAC system"
```

### Task 6: 实现逐分钟仿真和全量基准对照

**Files:**
- Create: `src/main/java/com/hvac/simulator/simulation/SimulationStep.java`
- Create: `src/main/java/com/hvac/simulator/simulation/SimulationResult.java`
- Create: `src/main/java/com/hvac/simulator/simulation/Simulator.java`
- Create: `src/test/java/com/hvac/simulator/TestFixtures.java`
- Create: `src/test/java/com/hvac/simulator/simulation/SimulatorTest.java`
- Create: `src/test/java/com/hvac/simulator/simulation/GaiaParityTest.java`

**Interfaces:**
- Consumes: `SimulationConfig`、`WeatherSeries`、`BuildingThermalModel`、`HvacSystem`。
- Produces: 按时间排序、固定 17 字段语义的 `SimulationResult`，以及后续输出测试复用的 `TestFixtures.runBaseline()`。

- [x] **Step 1: 写长度、启停和全量对照测试**

```java
@Test
void baselineProducesConfirmedTimeline() throws Exception {
    var result = TestFixtures.runBaseline();
    assertEquals(10_080, result.steps().size());
    assertEquals(LocalDateTime.of(2024, 7, 1, 0, 0), result.steps().getFirst().timestamp());
    assertEquals(LocalDateTime.of(2024, 7, 7, 23, 59), result.steps().getLast().timestamp());
    assertEquals(138, result.steps().stream().filter(s -> s.totalPowerKw() > 0).count());
}
```

`GaiaParityTest` 逐行解析同一基准 CSV，对 16 个数值字段执行：

```java
double tolerance = Math.max(1e-9, Math.abs(expected) * 1e-9);
assertEquals(expected, actual, tolerance,
        () -> "时间=" + timestamp + ", 字段=" + field + ", 允许误差=" + tolerance);
```

`TestFixtures.runBaseline()` 使用 `SimulationConfig.gaiaDemo(42L)`、`BaselineWeatherSource` 和全部 Gaia 默认参数运行一次完整仿真，供仿真、CSV、图表和 CLI 测试复用。

- [ ] **Step 2: 运行并确认失败**

Run: `.\mvnw.cmd -Dtest=SimulatorTest,GaiaParityTest test`

Expected: FAIL，仿真类型尚不存在。

- [x] **Step 3: 实现仿真主循环**

初始室温 25℃、初始回水 12℃。当 `roomC > coolingSetpointC + deadbandC / 2` 时，按 Python 的内部得热、太阳得热、围护传热、渗透和一步回设定值公式计算负供冷量，并限制到 `-ratedCapacityKw*1000`；依次调用 HVAC、建筑 `step`、记录结果和更新回水。

- [x] **Step 4: 运行全量基准并修正转换差异**

Run: `.\mvnw.cmd -Dtest=SimulatorTest,GaiaParityTest test`

Expected: 10,080 行全部字段通过，非零功率分钟数为 138。

- [x] **Step 5: 提交仿真主链**

```powershell
git add -- src/main/java/com/hvac/simulator/simulation src/test/java/com/hvac/simulator/simulation
git commit -m "feat(simulation): reproduce Gaia minute loop"
```

### Task 7: 实现原子 CSV 输出

**Files:**
- Create: `src/main/java/com/hvac/simulator/output/CsvResultWriter.java`
- Create: `src/test/java/com/hvac/simulator/output/CsvResultWriterTest.java`

**Interfaces:**
- Consumes: `SimulationResult`、目标 `Path`。
- Produces: UTF-8、17 字段、10,080 行的 `hvac_simulation_results.csv`。

- [x] **Step 1: 写 CSV 契约测试**

```java
@TempDir Path tempDir;

@Test
void writesGaiaHeaderAndAllRows() throws Exception {
    Path target = tempDir.resolve("hvac_simulation_results.csv");
    new CsvResultWriter().write(TestFixtures.runBaseline(), target);
    var lines = Files.readAllLines(target, StandardCharsets.UTF_8);
    assertEquals(SimulationStep.CSV_HEADER, lines.getFirst());
    assertEquals(10_081, lines.size());
}
```

- [ ] **Step 2: 运行并确认失败**

Run: `.\mvnw.cmd -Dtest=CsvResultWriterTest test`

Expected: FAIL，写入器尚不存在。

- [x] **Step 3: 实现标准库 CSV 写入**

使用 `BufferedWriter` 和 `Locale.ROOT`，时间格式 `yyyy-MM-dd HH:mm:ss`，数值使用 `Double.toString`。先写入同目录唯一 `.tmp` 文件，关闭成功后使用 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`；文件系统不支持原子移动时只退化为 `REPLACE_EXISTING`，不保留半写入正式文件。

- [x] **Step 4: 运行测试并提交**

Run: `.\mvnw.cmd -Dtest=CsvResultWriterTest test`

Expected: PASS。

```powershell
git add -- src/main/java/com/hvac/simulator/output/CsvResultWriter.java src/test/java/com/hvac/simulator/output/CsvResultWriterTest.java
git commit -m "feat(output): write Gaia-compatible CSV"
```

### Task 8: 实现中文三联图

**Files:**
- Create: `src/main/java/com/hvac/simulator/output/GaiaChartRenderer.java`
- Create: `src/test/java/com/hvac/simulator/output/GaiaChartRendererTest.java`

**Interfaces:**
- Consumes: `SimulationResult`、PNG 目标 `Path`。
- Produces: 三行一列、约 1200×1000 的 `simulation_plot.png`。

- [x] **Step 1: 写图片输出测试**

```java
@Test
void writesReadableThreePanelPng() throws Exception {
    Path target = tempDir.resolve("simulation_plot.png");
    new GaiaChartRenderer().write(TestFixtures.runBaseline(), target);
    var image = ImageIO.read(target.toFile());
    assertNotNull(image);
    assertEquals(1200, image.getWidth());
    assertTrue(image.getHeight() >= 990 && image.getHeight() <= 1010);
}
```

- [ ] **Step 2: 运行并确认失败**

Run: `.\mvnw.cmd -Dtest=GaiaChartRendererTest test`

Expected: FAIL，渲染器尚不存在。

- [x] **Step 3: 实现 XChart 三联图**

创建三个 1200×333 `XYChart`，X 轴为 epoch milliseconds 并通过格式化函数显示日期。序列和颜色固定为：室温蓝、室外温度橙、冷负荷蓝、系统总功率橙、COP 蓝；关闭标记点，图例右上，字体依次选择 Microsoft YaHei、SimHei、Microsoft JhengHei，并验证能显示“温度功率时间”。使用 `BitmapEncoder.saveBitmap(charts, 3, 1, outputStream, PNG)` 写临时文件后替换正式文件。

- [x] **Step 4: 运行测试并人工查看**

Run: `.\mvnw.cmd -Dtest=GaiaChartRendererTest test`

Expected: PASS，图片可读取且中文不为方框。

- [x] **Step 5: 提交图表实现**

```powershell
git add -- src/main/java/com/hvac/simulator/output/GaiaChartRenderer.java src/test/java/com/hvac/simulator/output/GaiaChartRendererTest.java
git commit -m "feat(output): render Gaia Chinese chart"
```

### Task 9: 实现 CLI、打包和端到端运行

**Files:**
- Create: `src/main/java/com/hvac/simulator/app/GaiaSimulatorApplication.java`
- Create: `src/test/java/com/hvac/simulator/app/GaiaSimulatorApplicationTest.java`

**Interfaces:**
- Consumes: `--weather=baseline|synthetic`、`--seed=<long>`、`--output=<path>`。
- Produces: 同目录 CSV、PNG 和明确中文运行摘要。

- [x] **Step 1: 写 CLI 测试**

```java
@Test
void baselineRunCreatesBothArtifacts() throws Exception {
    int exitCode = new GaiaSimulatorApplication().run(new String[] {
            "--weather=baseline", "--output=" + tempDir
    });
    assertEquals(0, exitCode);
    assertTrue(Files.size(tempDir.resolve("hvac_simulation_results.csv")) > 0);
    assertTrue(Files.size(tempDir.resolve("simulation_plot.png")) > 0);
}
```

- [ ] **Step 2: 运行并确认失败**

Run: `.\mvnw.cmd -Dtest=GaiaSimulatorApplicationTest test`

Expected: FAIL，应用入口尚不存在。

- [x] **Step 3: 实现参数解析和装配**

默认 `weather=baseline`、`seed=42`、`output=output`。未知参数、未知模式和非法 seed 抛出中文 `IllegalArgumentException`；`run` 成功返回 0，`main` 捕获异常、打印 `仿真失败：<原因>` 并以非零状态退出。

- [x] **Step 4: 运行测试、打包和 JAR 冒烟**

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target\hvac-simulator-java.jar --weather=baseline --output=output
```

Expected: 测试全绿；JAR 运行生成 10,080 行 CSV 和中文三联 PNG。

- [x] **Step 5: 提交 CLI 和打包结果**

```powershell
git add -- src/main/java/com/hvac/simulator/app src/test/java/com/hvac/simulator/app pom.xml
git commit -m "feat(app): add runnable Gaia simulator CLI"
```

### Task 10: 完成文档、注释审查和最终验证

**Files:**
- Modify: `PROJECT_GUIDE.md`
- Modify: `PROJECT_STATUS.md`
- Modify: `docs/superpowers/specs/2026-08-04-gaia-java-port-design.md`
- Modify: `docs/superpowers/plans/2026-08-04-gaia-java-port.md`

**Interfaces:**
- Consumes: 已完成代码、测试、CSV、PNG 和运行证据。
- Produces: 与当前实现一致的稳定项目地图、状态和完成记录。

- [x] **Step 1: 完成生产代码注释检查**

逐文件检查所有生产类、构造器和方法。核心类说明上下游职责；公式方法说明单位、正负号、原假设和忠实兼容原因；简单访问器不机械添加注释。交付报告列出每个生产文件及关键方法判断。

- [x] **Step 2: 更新项目文档**

`PROJECT_GUIDE.md` 增加实际包结构、运行命令、输出路径和双气象模式；`PROJECT_STATUS.md` 只把有代码和测试证据的事项标记完成，并保留领导视觉确认、物理准确性和分支保护为未验证；设计和计划状态改为“已实施”。

- [x] **Step 3: 运行最终验证**

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target\hvac-simulator-java.jar --weather=baseline --output=output
java -jar target\hvac-simulator-java.jar --weather=synthetic --seed=42 --output=output-synthetic
git diff --check
git status --short
```

Expected: 全部测试、打包和两种模式通过；只存在本任务文件；`output/`、`output-synthetic/` 和 `target/` 未进入暂存区。

- [x] **Step 4: 视觉核对 Java 图表**

比较 Java `output/simulation_plot.png` 与 `reference/gaia-1.0/python-reference-plot.png`：三联布局、五条序列、蓝橙颜色、中文标签、时间范围和尖峰位置一致；记录像素级一致不属于验收要求。

- [x] **Step 5: 提交文档和最终状态**

```powershell
git add -- PROJECT_GUIDE.md PROJECT_STATUS.md docs/superpowers/specs/2026-08-04-gaia-java-port-design.md docs/superpowers/plans/2026-08-04-gaia-java-port.md
git diff --cached --check
git commit -m "docs(simulator): record Java port delivery"
```

- [x] **Step 6: 推送任务分支并准备 PR**

```powershell
git push -u origin feature/gaia-java-port
git rev-list --left-right --count origin/main...HEAD
git diff --name-only origin/main...HEAD
```

Expected: 推送成功，分支不落后 `origin/main`，差异只包含 Gaia Java 转换、基准资产、测试和同步文档。
