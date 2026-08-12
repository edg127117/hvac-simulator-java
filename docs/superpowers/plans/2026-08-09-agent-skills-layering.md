# Agent Skills Layering Implementation Plan

> [!IMPORTANT]
> 文档类型：实施计划<br>
> 生命周期：冻结历史快照<br>
> 当前状态：[PROJECT_STATUS.md](../../../PROJECT_STATUS.md)<br>
> 历史目录：[docs/superpowers](../)<br>
> 使用限制：本文记录任务当时的实施安排，不用于判断功能当前是否已实现或已验证。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 HVAC 项目级仿真验证 Skill，并将仓库常驻规则精简为模型硬边界、全局 Skill 触发和项目专项注释要求。

**Architecture:** 通用注释质量与安全 PR 交付由已创建的个人全局 Skill 提供；本仓库只版本化 `hvac-simulation-verification`。`AGENTS.md` 保留禁止静默修模和版本隔离等不可突破约束，详细可执行仿真验证流程放入仓库级 Skill。

**Tech Stack:** Markdown、YAML、Git、Codex Skill Creator、Java 21/Maven Wrapper。

## Global Constraints

- 不修改 Java、Python 参考模型、冻结基准、测试、构建或 `PROJECT_STATUS.md` 当前状态。
- 不改变 Gaia 1.0/1.1 公式、参数、字段、随机顺序、时间语义或容差。
- 不把 `iot-platform-demo` 的路径、业务、数据源、端口或状态写入本仓库。
- 个人全局 Skill 只作为可用依赖，仓库仍保留开发者可读的模型安全硬边界。

---

### Task 1: 创建 HVAC 仿真验证 Skill

**Files:**
- Create: `.agents/skills/hvac-simulation-verification/SKILL.md`
- Create: `.agents/skills/hvac-simulation-verification/agents/openai.yaml`
- Modify: `AGENTS.md`
- Modify: `PROJECT_GUIDE.md`
- Modify: `docs/development/simulation-verification.md`

**Interfaces:**
- Consumes: 模型、参数、状态推进、气象、随机行为、输出、CSV、图表、模型版本或参考资产变化。
- Produces: 与范围匹配的定向测试、完整测试、基准对照、产物检查和明确结论。

- [ ] 使用 Skill Creator 的 `init_skill.py` 在 `.agents/skills` 初始化 `hvac-simulation-verification`。
- [ ] 在 frontmatter `description` 中覆盖公式、参数、气象、随机测量、状态推进、输出字段、CSV、图表、模型版本和参考基准触发词。
- [ ] 将现有 `simulation-verification.md` 的可执行流程迁入 Skill，保留可复现输入、忠实转换、逐时间步字段对照、容差依据、边界异常、JAR/CSV/图表分别验证和结论分级；原文件缩短为开发者可读的模型验证原则和 Skill 入口。
- [ ] 明确普通 Java 变化运行定向测试和完整 `./mvnw.cmd test`；打包、资源或可执行交付才补充 package/JAR/CSV/图表验证。
- [ ] 更新 `AGENTS.md` 为项目硬边界和 Skill 触发，删除完整流程重复与无条件读取三个入口文件的要求。
- [ ] 更新 `PROJECT_GUIDE.md` 同时导航到仓库 Skill 和精简后的开发者说明，避免维护两份完整流程。

### Task 2: 精简 HVAC 项目注释规则

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/development/code-comments.md`

**Interfaces:**
- Consumes: 个人全局 `code-comment-quality` 的通用流程和当前 Java 变化。
- Produces: HVAC 公式、单位、符号、时间步、状态推进、随机顺序和 Gaia 兼容专项检查。

- [ ] 从项目文档删除通用类/方法注释教程，只保留仿真领域非显然要求。
- [ ] 核心模型、公式、状态推进、随机行为和大范围重构检查完整变化文件与相关调用链。
- [ ] 普通生产 Java 修改只检查变更及受影响方法；注释、拼写和机械调整只核验变化附近。
- [ ] 在 `AGENTS.md` 保留生产代码必须使用全局注释 Skill 的触发句，并明确项目专项规则优先。

### Task 3: 记录、验证、提交和推送

**Files:**
- Verify: 本计划列出的全部变化文件。

**Interfaces:**
- Consumes: Task 1–2 的完整差异和已创建的个人全局 Skill。
- Produces: 可审查并继续使用现有 PR 的文档与 Skill 提交。

- [ ] 使用 `quick_validate.py` 校验 `hvac-simulation-verification` 和两个个人全局 Skill。
- [ ] 检查 Gaia 版本、忠实基准、逐行字段、容差、JAR、CSV、图表和禁止静默修模术语仍可定位。
- [ ] 检查仓库 Markdown 链接、Skill 路径、文件范围和 `git diff --check`。
- [ ] 本次无业务代码或模型变化，不运行无关 Maven 测试或重新生成仿真产物。
- [ ] 明确暂存文件，提交并推送现有 `docs/agents-instruction-layering` 分支；更新原 Compare/PR 材料。
