# Gaia 1.0 Java 忠实转换设计

> 状态：已确认、已实施，功能分支待合并和领导视觉确认。
>
> 本文记录第一阶段的目标、取舍和验收标准。当前代码是否已经实现，以 `PROJECT_STATUS.md`、当前代码和自动化测试为准。

## 1. 背景与目标

项目第一阶段把用户提供的 Gaia 1.0 Python 办公建筑中央空调仿真程序忠实转换为 Java，并生成温度、负荷、功率和 COP 中文图表供领导确认。

“忠实转换”表示保留原程序的参数、公式、计算顺序、状态推进、时间语义、输出字段和已知简化行为。第一阶段不以修正模型或改善曲线为目标。

第一阶段需要形成两种运行能力：

1. 基准复现模式：使用冻结的 Python 气象输入，由 Java 独立计算建筑和 HVAC 结果，用于逐时间步验证转换正确性并生成对照图。
2. Java 合成气象模式：由 Java 原生生成合成气象并运行完整仿真，用于证明程序已经脱离 Python 运行时。

## 2. 已核验的 Python 基线

### 2.1 源文件

- 文件名：`Gaia1.0.py`。
- 源文件行数：667 行。
- SHA-256：`D18064BBF6756EA42B694FA8526F5232D61834D2B29DC153748D5C9EC5C24BEF`。
- 源文件的个人临时绝对路径不进入仓库文档、配置或代码。

### 2.2 基准运行环境

- Python：3.12.13。
- NumPy：2.5.1。
- Pandas：3.0.1。
- Matplotlib：3.11.1。
- Python 原程序没有固定随机种子。
- 中文参考图只修复运行环境字体，不改变 CSV 数据或曲线布局。

### 2.3 基准运行结果

- 开始时间：2024-07-01 00:00。
- 结束时间：2024-07-07 23:59。
- 时间步长：1 分钟。
- 结果行数：10,080。
- 结果字段数：17。
- 原始输出文件名：`hvac_simulation_results.csv`、`simulation_plot.png`。

17 个字段按顺序为：

1. `datetime`
2. `T_outdoor`
3. `T_wb`
4. `solar`
5. `T_room`
6. `cooling_load_kW`
7. `chiller_power_kW`
8. `chw_pump_power_kW`
9. `cw_pump_power_kW`
10. `ct_fan_power_kW`
11. `terminal_fan_power_kW`
12. `total_power_kW`
13. `chiller_PLR`
14. `chiller_COP`
15. `T_chw_supply`
16. `T_cw_supply`
17. `pipe_heat_gain_kW`

## 3. 范围

### 3.1 包含范围

- Gaia 1.0 全部参数对象的 Java 表达。
- 合成气象生成公式。
- 建筑内部负荷、太阳得热、围护结构传热、渗透显热和一阶 RC 温度更新。
- 冷机、水泵、冷却塔、末端风机、管网热量和系统总功率计算。
- 逐分钟主仿真循环和跨时间步状态。
- 与 Python 相同的 17 字段 CSV。
- 与 Python 对应的中文三联图。
- Python—Java 全量数值对照测试。
- 可直接运行的完整 JAR。

### 3.2 不包含范围

- 修正 Gaia 的气象季节定位。
- 修正或重构原有物理模型。
- 平滑功率或 COP 曲线。
- 增加最小启停时间、设备状态保持或连续调节。
- Spring Boot、HTTP API、数据库、前端或 IoT 平台接入。
- 长期运行、参数标定和现场数据验收。

## 4. 技术方案

### 4.1 技术栈

- Java 21。
- Maven Wrapper 3.3.4，使用 Maven 3.9.16。
- XChart 4.0.3。
- JUnit 6.1.2，通过 JUnit BOM 管理测试依赖。
- Maven Compiler Plugin 3.15.0。
- Maven Surefire Plugin 3.5.5。
- Maven Shade Plugin 3.6.2。

不引入 Spring Boot、Lombok、第三方 CSV 库或数据库依赖。CSV 使用 Java 标准库写入，Shade Plugin 生成包含 XChart 的可执行 JAR。

### 4.2 包结构

```text
com.hvac.simulator
├─ app
│  └─ GaiaSimulatorApplication
├─ config
│  ├─ SimulationConfig
│  ├─ WeatherParameters
│  ├─ BuildingEnvelope
│  ├─ InternalLoad
│  └─ HvacParameters
├─ weather
│  ├─ WeatherSource
│  ├─ WeatherPoint
│  ├─ WeatherSeries
│  ├─ BaselineWeatherSource
│  └─ SyntheticWeatherGenerator
├─ model
│  ├─ BuildingThermalModel
│  ├─ HvacSystem
│  └─ HvacStepResult
├─ simulation
│  ├─ Simulator
│  ├─ SimulationStep
│  └─ SimulationResult
└─ output
   ├─ CsvResultWriter
   └─ GaiaChartRenderer
```

### 4.3 组件职责

`GaiaSimulatorApplication` 只负责解析运行模式、装配默认参数、启动仿真并调用输出组件，不承载公式。

`config` 中的对象使用不可变 Java `record` 或不可变类保存 Gaia 默认参数。参数名称、数值和单位与 Python 基线保持对应。

`WeatherSource` 提供统一气象边界：

```java
public interface WeatherSource {
    WeatherSeries load(SimulationConfig config);
}
```

`BaselineWeatherSource` 从类路径基准 CSV 读取 `datetime`、`T_outdoor`、`T_wb` 和 `solar`。`SyntheticWeatherGenerator` 使用 Java 实现 Gaia 合成气象公式，并接受固定随机种子。

`BuildingThermalModel` 负责内部得热和单步室温更新。`HvacSystem` 负责冷机、水泵、冷却塔、管道、末端及系统总功率。二者不负责文件和图表。

`Simulator` 组织逐分钟状态推进。`SimulationStep` 表示一个时间步的 17 字段结果，`SimulationResult` 保存完整顺序结果。

`CsvResultWriter` 输出 UTF-8 CSV。`GaiaChartRenderer` 使用相同结果生成中文三联 PNG，不重新计算任何指标。

## 5. 数据流和运行模式

### 5.1 公共计算链

```text
仿真配置
→ WeatherSource 生成或读取气象
→ Simulator 按时间顺序循环
→ BuildingThermalModel 与 HvacSystem 计算
→ SimulationStep
→ SimulationResult
→ CSV 与三联图
```

每个时间步严格保持以下顺序：

1. 读取当前干球温度、湿球温度和太阳辐射。
2. 按当前室温和原阈值判断是否供冷。
3. 计算供冷需求并限制到冷机额定容量。
4. 运行 HVAC 系统计算。
5. 使用当前供冷量更新室温。
6. 按原字段顺序记录结果。
7. 更新下一时间步使用的回水温度。

### 5.2 基准复现模式

基准 CSV 作为类路径资源保存在 `src/main/resources/gaia-baseline/python-results.csv`。Java 只读取气象和时间字段作为输入，其余 Python 字段只用于测试期预期值比较。

默认第一阶段运行命令计划为：

```powershell
java -jar target\hvac-simulator-java.jar --weather=baseline
```

Java 必须独立计算室温、负荷、功率、PLR、COP、水温和管道热量，禁止直接复制 Python 预期结果作为 Java 输出。

### 5.3 Java 合成气象模式

计划运行命令为：

```powershell
java -jar target\hvac-simulator-java.jar --weather=synthetic --seed=42
```

Java 转换年周期、日周期、湿球温差、太阳高度角、大气透明度和云量扰动。第一阶段保留从“仿真第 0 天”计算季节的行为，不改为实际日期的年内日序。

Java 随机数只要求固定种子可重复，不要求与未固定种子的 NumPy 序列逐点相同。基准复现通过冻结气象输入完成。

第一阶段验收后，正式运行默认值可以改为 `synthetic`；基准模式永久保留为回归验证工具。

## 6. 忠实兼容行为

第一阶段明确保留下列行为：

- 室温高于 26℃ 才启动供冷。
- 供冷量公式可能在一个时间步内把室温拉回设定值。
- 冷机停机时 COP 为 0。
- 一周基准中功率非零 138 分钟、启停 138 次，每次连续运行 1 分钟。
- 功率和 COP 折线因此呈现密集尖峰。
- 冷机最低 PLR 为 0.1，最高为 1.0。
- 供冷时至少开启一台 FCU。
- 冷机 COP 非正时钳制为 1.0。
- 回水温度直接沿用上一时间步结果。
- 管道热量函数的符号说明和调用含义保持原实现，不在转换中修正。
- `get_outdoor_air_load` 保留为未接入主循环的预留能力。
- 冷机计算的 `dt` 参数保持接口兼容，即使当前公式没有使用。

这些行为需要通过中文注释和特征测试固定。后续修正必须单独设计，不得与忠实转换混合。

## 7. 输出设计

默认输出目录为 `output/`，该目录不进入 Git：

```text
output/
├─ hvac_simulation_results.csv
└─ simulation_plot.png
```

CSV 使用与 Python 相同的 17 个字段和顺序，编码为 UTF-8，时间格式保持 `yyyy-MM-dd HH:mm:ss`。

PNG 目标尺寸为 1200×1000 左右，包含三个纵向折线子图：

1. 室温、室外温度，纵轴“温度 (℃)”。
2. 冷负荷、系统总功率，纵轴“功率 (kW)”。
3. 冷机 COP，纵轴“COP”，横轴“时间”。

颜色对应 Matplotlib 默认蓝色 `#1f77b4` 和橙色 `#ff7f0e`，图例位于右上方。Java 运行时检测“Microsoft YaHei”“SimHei”等中文字体；找不到可显示中文的字体时明确失败，不输出乱码图表。

输出先写入同目录临时文件，完整成功后再替换正式文件，避免留下半写入结果。

## 8. 错误处理

以下情况立即失败并给出包含文件、字段或时间点的中文错误：

- 基准 CSV 不存在或无法读取。
- 缺少必需字段或字段顺序不符合契约。
- 行数、首尾时间、时间间隔或时间戳连续性不符合预期。
- 数值无法解析或出现 NaN、正负无穷。
- 时间步长不是正数。
- 输出目录无法创建或文件无法完成原子替换。
- 未找到可用中文字体。
- 未识别的运行模式或参数。

程序不跳过错误行、不自动修改参数、不静默降级为其他气象模式，也不吞掉公式计算异常。

## 9. 测试设计

### 9.1 单元测试

- 默认参数值、单位和不可变性。
- 工作日和周末内部负荷。
- 建筑热平衡单步更新。
- 冷机停机、最低 PLR、额定负荷和 COP。
- 定频和变频水泵功率。
- 冷却塔温度和风机功率。
- 管道热量及现有符号行为。
- 无供冷需求时全部设备停机。
- 供冷时系统总功率组成。
- Java 合成气象相同种子可重复、不同种子有差异。

### 9.2 全量基准对照

基准复现测试运行完整 10,080 个时间步，比较全部字段。

必须完全一致：

- 10,080 行和 17 字段名称、顺序。
- 首尾时间和每分钟时间戳。
- 停机、运行状态和非零值出现位置。

浮点字段允许误差为：

```text
max(1×10⁻⁹, |Python 预期值| × 1×10⁻⁹)
```

失败信息必须包含时间戳、字段名、Python 值、Java 值、绝对误差和允许误差。

### 9.3 输出和端到端测试

- CSV 能重新读取，字段、行数、编码和时间正确。
- PNG 能被 Java 图像 API 读取，尺寸和三图组合正确。
- 图表包含五条数据序列、中文标签、单位和指定颜色。
- 从可执行入口运行后同时生成 CSV 和 PNG。
- `mvn test`、`mvn package` 和可执行 JAR 运行均成功。

不同图表库不要求 PNG 像素完全一致，但数据、布局、颜色、标签、单位和曲线位置必须对应。

## 10. 计划文件结构

```text
hvac-simulator-java/
├─ .gitignore
├─ pom.xml
├─ mvnw
├─ mvnw.cmd
├─ .mvn/wrapper/
├─ reference/gaia-1.0/
│  ├─ Gaia1.0.py
│  ├─ README.md
│  └─ python-reference-plot.png
├─ src/main/java/com/hvac/simulator/
├─ src/main/resources/gaia-baseline/
│  └─ python-results.csv
├─ src/test/java/com/hvac/simulator/
├─ docs/superpowers/specs/
├─ docs/superpowers/plans/
└─ output/
```

`reference/gaia-1.0/README.md` 记录源文件哈希、基准环境、未固定随机种子的事实、字体修复方式和已知模型问题。个人临时路径不进入该文件。

## 11. 验收标准

设计进入实施完成状态必须同时满足：

- Java 21 Maven 工程可以使用 Wrapper 构建。
- 所有参数、气象、建筑、HVAC 和主循环代码已经转换。
- 基准模式完整一周全部字段通过数值对照。
- 合成气象模式固定种子可重复运行。
- Java 生成 10,080 行、17 字段 CSV。
- Java 生成中文正常显示的三联 PNG。
- 功率和 COP 的原始一分钟尖峰没有被平滑或修正。
- 单元测试、完整回归、打包和 JAR 冒烟均通过。
- `PROJECT_GUIDE.md` 和 `PROJECT_STATUS.md` 与最终实现同步。
- 已实现、已测试、未验证和明确排除内容如实报告。

领导确认图表只表示第一阶段展示通过，不自动代表模型物理准确性、性能和现场适用性已经验收。

## 12. 后续演进边界

忠实转换完成后，可以单独设计以下演进，但不得混入第一阶段：

- 按实际日期生成季节气象。
- 改善建筑热容、传热面积和气象合理性。
- 增加设备状态保持、最小启停时间和连续调节。
- COP 停机状态改为空值而不是 0。
- 领导展示用的聚合或平滑图。
- Spring Boot API、数据库、Web 前端和 IoT 平台接入。

每项演进都必须重新定义对照基线和验收标准。
