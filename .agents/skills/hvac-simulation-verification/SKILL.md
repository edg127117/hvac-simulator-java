---
name: hvac-simulation-verification
description: Verify changes in hvac-simulator-java involving Java production code, HVAC formulas, parameters, weather, random measurements, state progression, time semantics, output fields, CSV files, charts, model versions, Python reference behavior, frozen baselines, packaging, or executable artifacts. Use to select, execute, and report the validation required for simulator changes.
---

# HVAC Simulation Verification

## Purpose

根据变化范围验证仿真行为、忠实转换和交付产物。数值一致、自动化测试、物理合理、图表可读和平台能力是不同结论，不能互相替代。

## Workflow

1. 读取仓库 `AGENTS.md`，检查当前差异、模型版本、受影响链路和现有测试。
2. 固定并记录模型版本、时间范围、时区、时间步、参数、初始状态、随机种子、字段顺序、单位和参考资产版本。
3. 先运行相关定向测试；涉及 Java 生产代码时再运行 `.\mvnw.cmd test`。
4. 只有打包、资源、CLI 或交付产物变化时，补充 package、JAR、CSV 和图表验证。
5. 按实际证据分别报告通过、失败、跳过、未执行和人工确认范围。

## Validation Paths

### Ordinary Java changes

- 运行变化附近的测试类或方法。
- 运行 `.\mvnw.cmd test`。
- 检查异常、边界和文件副作用；普通测试不得依赖个人目录、系统当前时间、不可控随机输入或真实外部服务。

### Model-sensitive changes

公式、参数、气象、随机测量、状态推进、时间语义、单位、输出字段、模型版本或参考基准变化时：

- 对照对应版本 Python 参考行为，保持公式、参数、随机顺序和状态更新顺序；
- 按时间戳、每一行和每个字段比较，不用图形或汇总值代替逐项对照；
- 分别记录逐时间步差异、汇总差异、允许误差和已批准版本差异；
- 浮点容差按指标公式规模、累计误差和业务意义设定并说明依据，禁止用过宽全局容差掩盖差异；
- 覆盖启停、死区、零/最小/额定负荷、容量上限、首末时间步、跨日、缺失参数、序列错位、NaN、无穷和除零；
- 发现原模型疑似问题时保存最小复现和差异证据，建立独立模型版本或任务，禁止在忠实转换中静默修正。

Gaia 1.1 等包含随机测量的版本，必须先冻结 Python 环境、依赖、随机基准、字段语义和参考图。同名字段跨版本语义变化时，用模型版本、指标编码和来源类型隔离。

### Packaging and artifacts

涉及打包、资源、CLI 或可执行交付时执行：

```powershell
.\mvnw.cmd package
java -jar target/hvac-simulator-java.jar --weather=baseline --output=output
java -jar target/hvac-simulator-java.jar --weather=synthetic --seed=42 --output=output-synthetic
```

分别验证：

- JAR：按记录命令运行，退出状态和摘要符合预期；
- CSV：UTF-8、非空，字段、顺序、单位、时间戳和数据点数量正确；
- 图表：来自本次同一份真实仿真结果，文件非空，序列、时间范围、标题、坐标轴、单位和图例完整；
- 视觉检查：单独确认布局、可读性、遮挡、中文字体、单位、尖峰和异常数据来源。

图表自动检查不能代替视觉确认；图表可读也不能证明数值或物理行为正确。

### Baselines, docs, and process

- 冻结基准更新必须有独立设计、差异说明和可复现生成证据，禁止为了测试通过直接覆盖预期结果。
- 稳定参考资产放入版本化 `reference/` 或测试资源；`target/`、运行输出和临时依赖不进入 Git。
- 纯文档或规则变化只运行 Markdown 链接、关键术语、Skill 校验、文件范围和 `git diff --check`，不机械运行无关模型测试。

## Reporting

明确区分：已实现、已执行自动化验证、与 Python 基准一致、已人工检查图表、物理合理性/性能/长期运行尚未验收，以及仅设计尚未实现的模型或平台能力。
