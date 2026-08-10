# hvac-simulator-java 项目指南

## 1. 使用方式

每个任务先读取 [`AGENTS.md`](AGENTS.md)，再按任务需要选择信息入口：

- 涉及项目定位、模型版本、参考基线、稳定架构、运行入口或输出契约时读取本文件；
- 涉及当前实现、未完成项、风险、技术债或下一步时读取 [`PROJECT_STATUS.md`](PROJECT_STATUS.md)；
- 只读取与任务直接相关的设计、实施计划、代码、测试和参考资产。

本文件是稳定项目地图，不记录单次命令、临时排查过程、本机运行状态和短期任务进度。

## 2. 项目定位

本项目以 Gaia 1.0 办公建筑中央空调逐时间步模型为起点，先形成可重复运行、可测试、可导出的纯 Java 仿真引擎，再演进为独立的通用自由拓扑仿真平台。平台允许用户通过独立设备模块和公共水、电、控制信号端口自由搭建综合能源系统；中央空调设备是第一批正式模块，后续再扩展其他能源设备。Gaia 1.0 是首个已实现模型版本；Gaia 1.1 是下一个已确认接入设计、尚未转换和验证的模型版本。后续 Python 模型通过基准冻结、Java 转换、一致性测试和版本发布进入平台。

正式平台保持独立部署，先将仿真任务、参数和结果保存到自己的 MySQL、TDengine 与文件存储，再通过可扩展协议适配器向外部平台发送。第一阶段为中央空调经济性调试提供设备和数据能力，但平台、内部消息和模型接口不与单一外部平台绑定。当前纯 Java 引擎已具备公共能源、设备静态契约、强类型运行值、参数快照、单步设备计算契约、公共单设备校验、拓扑图和基础结构校验；尚无具体设备运行时和拓扑执行能力。Spring Boot、Vue、数据库、可执行自由拓扑、权限和 MQTT 功能也尚未实现，不能写成当前能力。

## 3. Gaia 1.0 Java 转换阶段边界

Gaia 1.0 Java 转换阶段的目标是完成 Java 转换并生成以下图表，供领导确认模型行为和展示结果：

- 温度：至少包含室内温度和室外温度；
- 负荷：至少包含冷负荷；
- 功率：至少包含系统总功率，必要时可补充主要设备功率；
- COP：至少包含冷机 COP。

该阶段还应提供可复现的结构化仿真结果，便于核对图表数据和 Python、Java 数值差异。

该阶段技术设计已经确认：

- 使用 Java 21、Maven Wrapper 和普通 Java CLI，不引入 Spring Boot；
- 使用 XChart 生成一张包含三个纵向子图的中文 PNG；
- 默认复现 2024 年 7 月 1 日至 7 月 7 日的 1 分钟基准；
- 同时提供 Python 基准气象模式和 Java 合成气象模式；
- 基准模式按全部时间步和字段进行数值对照；
- 详细架构、依赖、误差和测试设计查看 [`Gaia 1.0 Java 忠实转换设计`](docs/superpowers/specs/2026-08-04-gaia-java-port-design.md)。

上述技术方案已经在当前 Java 工程中实现。当前合并状态和仍需人工确认的事项以 `PROJECT_STATUS.md` 为准。

正式平台已经从固定 HVAC 任务结构调整为通用自由拓扑目标。模块化单体、独立设备、公共能源端口、拓扑求解、五层时间、数据库、Web 工作台、权限和 MQTT 发送边界查看 [`通用自由拓扑仿真平台设计`](docs/superpowers/specs/2026-08-10-free-topology-simulation-platform-design.md)。平台已分阶段完成通用设备与拓扑静态基础，以及设备运行时契约与公共能量数据模型；现有可运行仿真仍是固定 Gaia 1.0 普通 Java CLI。此前的 [`独立 HVAC 仿真平台设计`](docs/superpowers/specs/2026-08-04-hvac-simulation-platform-design.md) 保留为历史设计，不再代表最终平台边界。

Gaia 1.1 增加模拟传感器、功率表、测量制冷量和测量 COP，并改变部分同名输出字段的业务语义。接入方案采用物理仿真、测量模型和测量派生三段式边界，详细设计查看 [`Gaia 1.1 模型接入设计`](docs/superpowers/specs/2026-08-06-gaia-1.1-integration-design.md)。当前只完成设计，尚未形成 Gaia 1.1 稳定基准或 Java 实现。

## 4. Gaia 1.0 参考基线

用户提供的 Gaia 1.0 Python 程序是第一阶段行为参考。当前只记录已经从脚本核对出的稳定信息：

- 程序模拟建筑热过程、气象、HVAC 设备和逐时间步状态变化；
- 全局默认时间步长为 1 分钟；
- 脚本主程序当前将演示范围调整为 2024 年 7 月 1 日至 7 月 7 日；
- 脚本输出 CSV 结果；
- 当前组合图包含室内与室外温度、冷负荷与系统总功率、冷机 COP。

稳定参考资产位于 `reference/gaia-1.0`，包含原始 Python 源文件、来源与环境说明及中文参考图；冻结的 10,080 行 Python 基准结果位于 `src/main/resources/gaia-baseline/python-results.csv`。项目不依赖原个人临时目录。

Java 转换默认保持 Gaia 1.0 的公式、参数、时间语义、单位和状态更新顺序。发现原模型疑似问题时，先保留对照证据并提出独立变更，不在转换过程中静默修正。

## 5. 实际架构和数据链路

第一阶段已经形成以下可验证链路：

`固定仿真参数与时间范围 → 气象序列 → 建筑热过程 → HVAC 系统计算 → 逐时间步结果 → 结构化数据文件 → 温度、负荷、功率和 COP 图表`

通用拓扑引擎基础另形成以下可验证结构链路，但尚未接入设备公式和求解执行：

`公共能源与端口 → 版本化设备静态契约 → 拓扑节点与连线 → 候选连线校验 → 启动前基础结构校验`

设备运行时公共层形成以下可验证单设备链路，但尚无具体设备运行时实现：

`设备静态定义 → 强类型端口值与完整参数快照 → 时间和上一步状态 → 单设备边界校验与调用 → 端口输出、指标、下一状态或稳定错误`

主要包职责如下：

| 包 | 职责 |
|---|---|
| `com.hvac.simulator.app` | CLI 参数解析、对象装配和输出摘要 |
| `com.hvac.simulator.config` | Gaia 默认参数和仿真时间配置 |
| `com.hvac.simulator.weather` | Python 基准气象加载和 Java 合成气象生成 |
| `com.hvac.simulator.model` | 建筑热平衡、冷机、水泵、冷却塔、管道和 FCU 计算 |
| `com.hvac.simulator.simulation` | 一分钟控制逻辑、状态推进和 17 字段结果 |
| `com.hvac.simulator.output` | 原子写入 UTF-8 CSV 和中文三联 PNG |
| `com.hvac.simulator.energy` | 公共电、水和控制信号能源类型，不包含具体设备公式 |
| `com.hvac.simulator.energy.runtime` | 规范单位及不可变电、水和控制信号运行值，不执行单位换算 |
| `com.hvac.simulator.device` | 版本化独立设备静态契约、端口、时间步能力和只读设备目录 |
| `com.hvac.simulator.device.parameter` | 参数定义、使用方式、范围和版本化完整值快照 |
| `com.hvac.simulator.device.runtime` | 泛型设备状态、单步输入输出、指标、稳定错误和单设备公共校验，不包含具体公式或拓扑求解 |
| `com.hvac.simulator.topology` | 不可变节点、端口连接图和基础结构校验，不包含完整水力或守恒求解 |

构建、测试和运行命令：

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target\hvac-simulator-java.jar --weather=baseline --output=output
java -jar target\hvac-simulator-java.jar --weather=synthetic --seed=42 --output=output-synthetic
```

不传参数时默认使用 `baseline`、随机种子 `42` 和 `output` 目录。每次运行生成 `hvac_simulation_results.csv` 和 `simulation_plot.png`；运行输出目录与 Maven `target` 均不进入 Git。

## 6. 第一阶段交付判断

第一阶段进入可供领导确认状态至少需要：

- Java 程序可以通过仓库记录的命令重复运行；
- 固定场景的时间序列长度、字段、单位和时间戳正确；
- 关键指标已完成 Python 与 Java 基准对照并记录容差；
- 温度、负荷、功率和 COP 图表来自同一份真实仿真结果；
- 图表标题、坐标轴、单位和图例清晰，中文正常显示；
- 自动化测试结果和仍需人工确认的视觉范围如实记录。

领导确认图表不等于模型已经完成工程验收。数值准确性、物理合理性、性能和长期运行仍应依据后续明确的验收范围判断。

## 7. 文档导航和生命周期

| 入口 | 用途 | 更新时机 |
|---|---|---|
| [`AGENTS.md`](AGENTS.md) | 核心工作规则和按任务读取的规范路由 | 固定规则或路由变化时 |
| [`PROJECT_GUIDE.md`](PROJECT_GUIDE.md) | 稳定项目地图、目标边界、数据链路和运行入口 | 稳定项目事实变化时 |
| [`PROJECT_STATUS.md`](PROJECT_STATUS.md) | 当前完成项、未完成项、风险和下一步 | 当前版本状态变化时 |
| [`.agents/skills/hvac-simulation-verification/SKILL.md`](.agents/skills/hvac-simulation-verification/SKILL.md) | Codex 执行模型、基准、产物和图表验证的完整矩阵 | 可执行仿真验证流程变化时 |
| [`仿真验证说明`](docs/development/simulation-verification.md) | 面向开发者的忠实转换、逐项对照、容差和证据边界 | 仿真验证原则变化时 |
| [`HVAC 仿真代码注释专项规则`](docs/development/code-comments.md) | 公式、单位、状态推进、随机顺序和模型兼容注释要求 | Java 仿真专项注释规则变化时 |
| [`Gaia 1.0 Java 忠实转换设计`](docs/superpowers/specs/2026-08-04-gaia-java-port-design.md) | 第一阶段架构、忠实兼容边界、误差和验收标准 | 第一阶段设计决策变化时 |
| [`独立 HVAC 仿真平台设计`](docs/superpowers/specs/2026-08-04-hvac-simulation-platform-design.md) | 早期固定 HVAC 平台设计，作为历史演进记录保留 | 不再更新，当前平台目标查看自由拓扑设计 |
| [`通用自由拓扑仿真平台设计`](docs/superpowers/specs/2026-08-10-free-topology-simulation-platform-design.md) | 自由组合设备、公共能源端口、拓扑求解、时间、数据、工作台和协议的当前正式平台设计 | 自由拓扑正式平台设计决策变化时 |
| [`设备运行时契约与公共能量数据模型设计`](docs/superpowers/specs/2026-08-10-device-runtime-contract-and-shared-energy-data-model-design.md) | 强类型运行值、参数、状态、单步结果、错误和公共执行边界 | 设备公共运行时契约变化时 |
| [`Gaia 1.1 模型接入设计`](docs/superpowers/specs/2026-08-06-gaia-1.1-integration-design.md) | Gaia 1.1 的版本差异、测量层、指标语义、基准和验收设计 | Gaia 1.1 接入决策变化时 |
| `docs/superpowers/specs` | 经确认的任务设计和取舍 | 新功能或核心行为设计确认后 |
| `docs/superpowers/plans` | 任务实施步骤和验证方案 | 设计确认并进入实施前 |

设计和计划记录任务当时的上下文。判断当前行为时，应优先核验当前代码、自动化测试、`PROJECT_STATUS.md` 和已合并决策。

## 8. 维护规则

- 项目定位、稳定边界、核心数据链路、运行入口、输出契约或文档导航变化时更新本文件。
- 项目阶段、完成项、阻塞、风险、技术债或下一步变化时只更新 `PROJECT_STATUS.md`。
- 具体任务设计和实施步骤保存在任务级文档中，不复制到本文件。
- 单次命令、错误日志、本机路径、端口、进程和临时输出保留在当前会话。
- 发现文档与代码冲突时先核验代码和测试；证据不足时在 `PROJECT_STATUS.md` 标记为待核验。
