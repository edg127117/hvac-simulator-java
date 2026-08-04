# hvac-simulator-java 项目指南

## 1. 使用方式

处理本项目任务时按以下顺序读取信息：

1. 读取 [`AGENTS.md`](AGENTS.md)，确认 Git、测试、注释和文档管理规则。
2. 读取本文件，确认项目定位、第一阶段边界、参考基线和交付目标。
3. 读取 [`PROJECT_STATUS.md`](PROJECT_STATUS.md)，确认当前 Git 版本已经完成、尚未完成和需要核验的事项。
4. 只在任务需要时读取相关设计、实施计划、代码和测试。

本文件是稳定项目地图，不记录单次命令、临时排查过程、本机运行状态和短期任务进度。

## 2. 项目定位

本项目用于把 Gaia 1.0 中的办公建筑中央空调逐时间步仿真转换为 Java 实现，并形成可重复运行、可测试、可导出的仿真程序。

当前只确认第一阶段目标，不提前定义尚未确认的长期产品范围、平台集成方式或系统架构。

## 3. 第一阶段边界

第一阶段目标是完成 Java 转换并生成以下图表，供领导确认模型行为和展示结果：

- 温度：至少包含室内温度和室外温度；
- 负荷：至少包含冷负荷；
- 功率：至少包含系统总功率，必要时可补充主要设备功率；
- COP：至少包含冷机 COP。

第一阶段还应提供可复现的结构化仿真结果，便于核对图表数据和 Python、Java 数值差异。

第一阶段技术设计已经确认：

- 使用 Java 21、Maven Wrapper 和普通 Java CLI，不引入 Spring Boot；
- 使用 XChart 生成一张包含三个纵向子图的中文 PNG；
- 默认复现 2024 年 7 月 1 日至 7 月 7 日的 1 分钟基准；
- 同时提供 Python 基准气象模式和 Java 合成气象模式；
- 基准模式按全部时间步和字段进行数值对照；
- 详细架构、依赖、误差和测试设计查看 [`Gaia 1.0 Java 忠实转换设计`](docs/superpowers/specs/2026-08-04-gaia-java-port-design.md)。

上述技术方案已经在当前 Java 工程中实现。当前合并状态和仍需人工确认的事项以 `PROJECT_STATUS.md` 为准。

后续是否接入数据库、Web、IoT 平台或其他系统尚未确认，不得写成当前事实。

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

主要包职责如下：

| 包 | 职责 |
|---|---|
| `com.hvac.simulator.app` | CLI 参数解析、对象装配和输出摘要 |
| `com.hvac.simulator.config` | Gaia 默认参数和仿真时间配置 |
| `com.hvac.simulator.weather` | Python 基准气象加载和 Java 合成气象生成 |
| `com.hvac.simulator.model` | 建筑热平衡、冷机、水泵、冷却塔、管道和 FCU 计算 |
| `com.hvac.simulator.simulation` | 一分钟控制逻辑、状态推进和 17 字段结果 |
| `com.hvac.simulator.output` | 原子写入 UTF-8 CSV 和中文三联 PNG |

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
| [`AGENTS.md`](AGENTS.md) | 长期固定的 Git、测试、注释和文档管理规则 | 固定工作规则变化时 |
| [`PROJECT_GUIDE.md`](PROJECT_GUIDE.md) | 稳定项目地图、目标边界、数据链路和运行入口 | 稳定项目事实变化时 |
| [`PROJECT_STATUS.md`](PROJECT_STATUS.md) | 当前完成项、未完成项、风险和下一步 | 当前版本状态变化时 |
| [`Gaia 1.0 Java 忠实转换设计`](docs/superpowers/specs/2026-08-04-gaia-java-port-design.md) | 第一阶段架构、忠实兼容边界、误差和验收标准 | 第一阶段设计决策变化时 |
| `docs/superpowers/specs` | 经确认的任务设计和取舍 | 新功能或核心行为设计确认后 |
| `docs/superpowers/plans` | 任务实施步骤和验证方案 | 设计确认并进入实施前 |

设计和计划记录任务当时的上下文。判断当前行为时，应优先核验当前代码、自动化测试、`PROJECT_STATUS.md` 和已合并决策。

## 8. 维护规则

- 项目定位、稳定边界、核心数据链路、运行入口、输出契约或文档导航变化时更新本文件。
- 项目阶段、完成项、阻塞、风险、技术债或下一步变化时只更新 `PROJECT_STATUS.md`。
- 具体任务设计和实施步骤保存在任务级文档中，不复制到本文件。
- 单次命令、错误日志、本机路径、端口、进程和临时输出保留在当前会话。
- 发现文档与代码冲突时先核验代码和测试；证据不足时在 `PROJECT_STATUS.md` 标记为待核验。
