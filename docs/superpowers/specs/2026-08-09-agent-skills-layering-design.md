# Agent Skills 分层整改设计

## 状态

已确认。

## 背景

上一轮已将根 `AGENTS.md` 精简为模型硬边界和按需文档路由，但通用 Git 交付、通用生产代码注释以及完整仿真验证步骤仍由常驻规则或普通文档承载。后续 Gaia 版本和平台开发会持续增长，需要把重复流程改成任务匹配后才加载的 Skill，同时保留数值基准与禁止静默修模边界。

## 目标

- 使用个人全局 `code-comment-quality` Skill 统一生产代码注释质量流程。
- 使用个人全局 `safe-pr-delivery` Skill 统一安全分支、提交、推送、PR 和合并后清理流程。
- 在本仓库增加项目级 `hvac-simulation-verification` Skill，封装 Python/Java 基准、逐行数值对比、容差、JAR、CSV 和图表验证。
- 根 `AGENTS.md` 只保留模型硬边界、Skill 触发条件和项目专项差异。
- 普通 Java 修改只检查变更及受影响方法的注释；核心模型和大范围重构检查完整文件。

## Skill 边界

### 个人全局 Skill

- `code-comment-quality`：创建、修改或审查生产代码时触发；通用检查职责、上下游、边界、单位、异常、副作用和既有注释时效。
- `safe-pr-delivery`：发生仓库写入、提交、推送、PR 或合并后清理时触发；服从当前仓库更具体的模型、验证和分支规则。

个人全局 Skill 不进入本仓库 Git。仓库自身继续保留足够的模型和 Git 最小硬约束，后续开发者无需安装个人 Skill 也不会丢失关键安全边界。

### 本仓库 Skill

`hvac-simulation-verification` 存放于 `.agents/skills/hvac-simulation-verification/` 并进入 Git。涉及模型公式、参数、状态推进、气象、随机行为、输出字段、CSV、图表、模型版本或参考基准时必须使用。

Skill 根据变化范围区分：定向测试、完整 Maven 测试、冻结 Python 基准、逐时间步字段对比、容差依据、JAR 执行、CSV 契约和图表视觉确认。忠实一致性、物理合理性和视觉验收必须分别表述。

## 注释检查分级

- 核心模型、公式、状态推进、时间语义、随机顺序、单位、兼容行为和大范围重构：检查完整变化文件以及相关调用链。
- 普通生产代码：检查变更及受影响方法、构造器和既有注释，不枚举无关简单方法。
- 注释、拼写、格式和不改变行为的机械调整：只核验变化附近的准确性。

公式、单位、正负号、时间步、状态副作用、Gaia 版本和基准兼容属于本仓库专项要求，继续保留在项目规则或项目 Skill 中，不进入个人全局 Skill。

## 文件变化

- 个人全局：创建两个 Skill，并在个人 `AGENTS.md` 增加触发规则。
- 本仓库：创建 `.agents/skills/hvac-simulation-verification/`。
- 精简 `AGENTS.md` 和 `docs/development/code-comments.md`。
- 将 `docs/development/simulation-verification.md` 的可执行流程迁入项目 Skill；普通文档可保留为项目知识入口，但不重复完整流程。
- 更新 `PROJECT_GUIDE.md` 的导航，不改变 `PROJECT_STATUS.md` 的实现状态。

## 验证

- 使用 Skill Creator 校验两个全局 Skill 和本仓库 Skill 的结构与触发描述。
- 以普通 Java 修改、核心公式修改、纯文档和 Git 交付示例核对 Skill 触发边界。
- 检查模型版本、忠实基准、逐行对比、容差、JAR、CSV、图表和禁止静默修模语义仍可定位。
- 检查 Markdown 链接、文件范围和 `git diff --check`。
- 本次不修改 Java、模型或参考资产，因此不机械运行无关 Maven 测试或生成图表。

## 非目标

- 不修改 Gaia 1.0 或 Gaia 1.1 模型行为。
- 不更新冻结基准、容差或参考资产。
- 不实施 Spring Boot、Vue 或正式平台。
- 不把另一个 IoT 项目的业务范围、数据源和状态写入本仓库 Skill。
