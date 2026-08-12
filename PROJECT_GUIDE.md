# hvac-simulator-java 项目指南

## 1. 使用方式

每个任务先读取 [`AGENTS.md`](AGENTS.md)，再按任务需要选择信息入口：

- 涉及项目定位、模型版本、参考基线、稳定架构、运行入口或输出契约时读取本文件；
- 涉及当前实现、未完成项、风险、技术债或下一步时读取 [`PROJECT_STATUS.md`](PROJECT_STATUS.md)；
- 只读取与任务直接相关的设计、实施计划、代码、测试和参考资产。

本文件是稳定项目地图，不记录单次命令、临时排查过程、本机运行状态和短期任务进度。

## 2. 项目定位

本项目以 Gaia 1.0 办公建筑中央空调逐时间步模型为起点，先形成可重复运行、可测试、可导出的纯 Java 仿真引擎，再演进为独立的通用自由拓扑仿真平台。平台允许用户通过独立设备模块和公共水、电、控制信号端口自由搭建综合能源系统；中央空调设备是第一批正式模块，后续再扩展其他能源设备。Gaia 1.0 与 Gaia 1.1 均已形成独立 Java 模型版本；新 Python 模型继续通过基准冻结、Java 转换、一致性测试和版本发布进入平台。

正式平台保持独立部署，长期目标是将仿真任务、参数和结果保存到自己的 MySQL、TDengine 与文件存储，再通过可扩展协议适配器向外部平台发送。第一阶段为中央空调经济性调试提供设备和数据能力，但平台、内部消息和模型接口不与单一外部平台绑定。当前纵向切片已经提供进程内 Spring Boot 任务 API、Vue 工作台和独立 MQTT 发送适配器；数据库持久化、权限、可执行自由拓扑和完整设备运行时仍未实现。

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

正式平台已经从固定 HVAC 任务结构调整为通用自由拓扑目标。模块化单体、独立设备、公共能源端口、拓扑求解、五层时间、数据库、Web 工作台、权限和 MQTT 发送边界查看 [`通用自由拓扑仿真平台设计`](docs/superpowers/specs/2026-08-10-free-topology-simulation-platform-design.md)。平台已分阶段完成通用设备与拓扑静态基础、设备运行时契约与公共能量数据模型，并增加 Gaia 1.0/1.1 固定模型纵向切片；固定模型运行不等于自由拓扑执行已经完成。此前的 [`独立 HVAC 仿真平台设计`](docs/superpowers/specs/2026-08-04-hvac-simulation-platform-design.md) 保留为历史设计，不再代表最终平台边界。

Gaia 1.1 增加模拟传感器、功率表、测量制冷量和测量 COP，并改变部分同名输出字段的业务语义。接入实现采用物理仿真、测量模型和测量派生三段式边界，详细设计查看 [`Gaia 1.1 模型接入设计`](docs/superpowers/specs/2026-08-06-gaia-1.1-integration-design.md)。冻结基准和 Java 实现按模型版本与 Gaia 1.0 隔离。

## 4. Gaia 1.0 参考基线

用户提供的 Gaia 1.0 Python 程序是第一阶段行为参考。当前只记录已经从脚本核对出的稳定信息：

- 程序模拟建筑热过程、气象、HVAC 设备和逐时间步状态变化；
- 全局默认时间步长为 1 分钟；
- 脚本主程序当前将演示范围调整为 2024 年 7 月 1 日至 7 月 7 日；
- 脚本输出 CSV 结果；
- 当前组合图包含室内与室外温度、冷负荷与系统总功率、冷机 COP。

稳定参考资产位于 `reference/gaia-1.0`，包含原始 Python 源文件、来源与环境说明及中文参考图；冻结的 10,080 行 Python 基准结果位于 `src/main/resources/gaia-baseline/python-results.csv`。项目不依赖原个人临时目录。

Java 转换默认保持 Gaia 1.0 的公式、参数、时间语义、单位和状态更新顺序。发现原模型疑似问题时，先保留对照证据并提出独立变更，不在转换过程中静默修正。

### 4.1 Gaia 1.1 参考基线与输出契约

Gaia 1.1 原始 Python 文件、固定依赖、冻结脚本、资产哈希和五联参考图位于 `reference/gaia-1.1`。固定种子为 `20240810`，时间范围为 2024 年 7 月 1 日至 7 月 7 日，步长 1 分钟，共 10,080 行和 30 字段。冻结气象、标准化随机抽样与 Python 预期结果分别位于 `engine/src/main/resources/gaia-baseline/gaia-1.1` 和 `engine/src/test/resources/gaia-baseline/gaia-1.1`。

正式运行链路不启动 Python，也不读取 Python 30 字段结果。Java 使用冻结气象和随机抽样独立执行物理层、测量层和测量派生层；基准测试再按时间戳、行和全部数值字段与 Python 结果对照。Gaia 1.1 输出包含温度、负荷、功率、理论/测量 COP 和冷冻/冷却水温，Java CLI 生成 30 字段 CSV 与五联图。

## 5. 实际架构和数据链路

第一阶段已经形成以下可验证链路：

`模型版本与参数快照 → 气象和版本化随机序列 → 建筑热过程 → HVAC 物理计算 → Gaia 1.1 测量与派生 → 逐时间步结果 → CSV、图表或任务 API`

平台纵向切片形成以下可验证链路：

`Vue 版本/模式/独立参数目录 → Spring Boot 进程内任务 → Java Gaia 1.0/1.1 → 五组结果曲线 → 独立 MQTT 适配器 → 中央空调可选指标测点合同`

Gaia 1.0 与 Gaia 1.1 分别发布完整参数目录；同名公共参数在每个版本中独立声明，Gaia 1.1 另发布测量噪声等版本专属参数。API 使用 `COMMON`、`VERSION_SPECIFIC` 标识归属，页面切换版本时整体替换参数值，不能把两个版本的同名参数自动合并为同一发布定义。

MQTT 仅为已完成的 Gaia 1.1 任务发送所选指标的真实输入。缺失 `targets` 时保持兼容，只发送 `WCR_COP` 的 `WCR1_TWin`、`WCR1_TWout`、`WCR1_Flow` 和 `WCR1_PPE`；显式选择 `TOWER_EFF` 时，只在冷却塔风机和冷却水流量均大于零的时间步发送 `TOWER1_TCWin`、`TOWER1_TCWout` 和 `TOWER1_TWB`。默认主题为 `device/data/up`、QoS 1、非保留消息。适配器不伪造电压、电流、功率因数、泵压或风系统测点。中央平台本地验收入口分别为 `scripts/Verify-Gaia11CentralHvacCop.ps1` 和 `scripts/Verify-Gaia11CentralHvacTowerEfficiency.ps1`；外部 Broker、平台服务、授权 JWT 以及平台分钟补偿链路必须可用。

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
| `com.hvac.simulator.simulation` | 一分钟控制逻辑、状态推进和版本化 17/30 字段结果 |
| `com.hvac.simulator.measurement` | Gaia 1.1 随机抽样、测量扰动和测量派生边界 |
| `com.hvac.simulator.release` | Gaia 1.0/1.1 版本目录、真实默认参数和最终参数快照 |
| `com.hvac.simulator.output` | 原子写入 UTF-8 CSV 和中文三联/五联 PNG |
| `com.hvac.simulator.energy` | 公共电、水和控制信号能源类型，不包含具体设备公式 |
| `com.hvac.simulator.energy.runtime` | 规范单位及不可变电、水和控制信号运行值，不执行单位换算 |
| `com.hvac.simulator.device` | 版本化独立设备静态契约、端口、时间步能力和只读设备目录 |
| `com.hvac.simulator.device.parameter` | 参数定义、使用方式、范围和版本化完整值快照 |
| `com.hvac.simulator.device.runtime` | 泛型设备状态、单步输入输出、指标、稳定错误和单设备公共校验，不包含具体公式或拓扑求解 |
| `com.hvac.simulator.topology` | 不可变节点、端口连接图和基础结构校验，不包含完整水力或守恒求解 |
| `server` | Spring Boot 模型目录、进程内任务、结果查询和 MQTT 投递编排 |
| `web` | Vue 版本切换、参数编辑、运行监控、五组结果曲线和 MQTT 投递状态 |

构建、测试和运行命令：

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar engine\target\hvac-simulator-engine.jar --model=gaia-1.0 --weather=baseline --output=output
java -jar engine\target\hvac-simulator-engine.jar --model=gaia-1.1 --output=output-gaia-1.1
java -jar server\target\hvac-simulator-server-1.1.0-SNAPSHOT.jar
cd web
pnpm install
pnpm run dev
pnpm run test:e2e
```

CLI 不传模型版本时保持 Gaia 1.0 兼容。Gaia 1.0 生成 17 字段 CSV 和三联图；Gaia 1.1 生成 30 字段 CSV 和五联图。Spring Boot 当前使用进程内任务仓库，重启不恢复任务。运行输出、前端依赖/构建目录与 Maven `target` 均不进入 Git。

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

### 7.1 当前有效入口

| 入口 | 用途 | 更新时机 |
|---|---|---|
| [`AGENTS.md`](AGENTS.md) | 核心工作规则、文档生命周期和按任务读取的规范路由 | 固定规则或路由变化时 |
| [`PROJECT_GUIDE.md`](PROJECT_GUIDE.md) | 稳定项目地图、目标边界、当前正式设计导航、数据链路和运行入口 | 稳定项目事实或正式设计入口变化时 |
| [`PROJECT_STATUS.md`](PROJECT_STATUS.md) | 当前完成项、未完成项、验证边界、风险和下一步 | 当前版本状态变化时 |
| [`.agents/skills/hvac-simulation-verification/SKILL.md`](.agents/skills/hvac-simulation-verification/SKILL.md) | Codex 执行模型、基准、产物和图表验证的完整矩阵 | 可执行仿真验证流程变化时 |
| [`仿真验证说明`](docs/development/simulation-verification.md) | 面向开发者的忠实转换、逐项对照、容差和证据边界 | 仿真验证原则变化时 |
| [`HVAC 仿真代码注释专项规则`](docs/development/code-comments.md) | 公式、单位、状态推进、随机顺序和模型兼容注释要求 | Java 仿真专项注释规则变化时 |

### 7.2 当前正式设计

| 设计 | 当前边界 |
|---|---|
| [`Gaia 1.0 Java 忠实转换设计`](docs/superpowers/specs/2026-08-04-gaia-java-port-design.md) | Gaia 1.0 第一阶段架构、忠实兼容边界、误差和验收标准 |
| [`通用自由拓扑仿真平台设计`](docs/superpowers/specs/2026-08-10-free-topology-simulation-platform-design.md) | 当前正式平台目标；自由组合设备、公共能源端口、拓扑求解、时间、数据、工作台和协议边界 |
| [`设备运行时契约与公共能量数据模型设计`](docs/superpowers/specs/2026-08-10-device-runtime-contract-and-shared-energy-data-model-design.md) | 强类型运行值、参数、状态、单步结果、错误和公共执行边界 |
| [`Gaia 1.1 模型接入设计`](docs/superpowers/specs/2026-08-06-gaia-1.1-integration-design.md) | Gaia 1.1 的版本差异、测量层、指标语义、基准和验收边界 |
| [`版本化参数与冷却塔效率接入设计`](docs/superpowers/specs/2026-08-11-gaia-versioned-parameters-and-tower-efficiency-design.md) | 独立参数目录、参数归属和可选冷却塔真实测点合同 |
| [`文档生命周期与当前状态判定设计`](docs/superpowers/specs/2026-08-12-document-lifecycle-and-current-state-design.md) | 历史设计/计划冻结、当前状态证据顺序和新文档承接规则 |
| [`历史状态隔离与自动防复发设计`](docs/superpowers/specs/2026-08-12-historical-status-isolation-and-guardrails-design.md) | 历史快照显式标识、当前状态检索隔离、冻结校验和本地/CI 防复发边界 |

当前正式设计同样是冻结记录。正式决策变化时必须新建带日期设计，明确补充、替代或废止范围，再更新本节导航；不得回写旧设计正文。

### 7.3 冻结历史记录

| 记录 | 历史用途 |
|---|---|
| [`独立 HVAC 仿真平台设计`](docs/superpowers/specs/2026-08-04-hvac-simulation-platform-design.md) | 早期固定 HVAC 平台设计；已由自由拓扑平台设计承接，不再代表当前最终平台边界 |
| [`Gaia 1.1 平台 MVP 实施计划`](docs/superpowers/plans/2026-08-10-gaia-1.1-platform-mvp.md) | 固定模型纵向切片任务当时的文件、接口、测试和阶段验收安排 |
| [`文档生命周期与当前状态整改实施计划`](docs/superpowers/plans/2026-08-12-document-lifecycle-and-current-state.md) | 本次规则整改的实施步骤和验证安排 |
| [`历史状态隔离与自动防复发实施计划`](docs/superpowers/plans/2026-08-12-historical-status-isolation-and-guardrails.md) | 历史文档迁移、Guardrails、Hook、CI 和 PR 模板的任务当时实施安排 |
| `docs/superpowers/specs` | 用户确认后的任务设计记录，确认后冻结正文 |
| `docs/superpowers/plans` | 用户批准并进入实施后的任务计划，批准后冻结正文 |

冻结设计和计划只记录任务当时的上下文。旧计划未勾选项、旧设计未来时态和当时的未实现描述均不代表当前状态，也不能推翻当前代码、自动化测试和 Git 合并证据。设计完成、计划勾选或历史说明同样不能单独证明功能已经实现或验收。

每份历史文档必须在标题后声明文档类型、冻结生命周期、当前状态入口、历史目录和使用限制。`docs/superpowers/.history-guardrails-enabled` 启用后，既有历史文档和标识文件禁止修改、删除或重命名；后续决策只能新建设计或计划承接。

仓库级检查入口为 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-RepositoryGuardrails.ps1 -Mode WorkingTree`。它验证历史文档标识与链接、当前状态入口和动态状态禁写规则；暂存与 PR 模式还验证冻结差异和 PR 文档同步结论。

## 8. 维护规则

- 项目定位、稳定边界、核心数据链路、运行入口、输出契约或文档导航变化时更新本文件。
- 项目阶段、完成项、阻塞、风险、技术债或下一步变化时只更新 `PROJECT_STATUS.md`。
- 具体任务设计和实施步骤保存在任务级文档中，不复制到本文件；设计确认或计划批准后不得回写，后续变化新建文档承接。
- 单次命令、错误日志、本机路径、端口、进程和临时输出保留在当前会话。
- 发现文档与代码冲突时先核验代码和测试；证据不足时在 `PROJECT_STATUS.md` 标记为待核验。
