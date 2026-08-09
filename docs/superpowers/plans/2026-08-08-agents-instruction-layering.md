# AGENTS Instruction Layering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将根 `AGENTS.md` 精简为稳定路由，并把详细仿真验证和注释规范迁入按需文档，同时保持模型边界不变。

**Architecture:** 根规则只保存每个任务都必须加载的约束和条件式入口；稳定项目事实留在 `PROJECT_GUIDE.md`，详细验证与注释规范放在 `docs/development`。本次只改文档，不改变 Java 实现、模型、测试或参考资产。

**Tech Stack:** Markdown、PowerShell、Git、Java 21/Maven Wrapper（仅作为既有验证入口记录）。

## Global Constraints

- 任务从仓库根目录启动，不创建尚未实现模块的子目录 `AGENTS.md`。
- 根 `AGENTS.md` 目标不超过 8 KiB。
- 保留 Gaia 忠实转换、确定性测试、数值容差、图表验收和禁止静默修模规则。
- 不修改 Java 代码、构建配置、参考资产或 `PROJECT_STATUS.md` 的当前状态。

---

### Task 1: 建立详细规则文档

**Files:**
- Create: `docs/development/simulation-verification.md`
- Create: `docs/development/code-comments.md`

**Interfaces:**
- Consumes: 当前 `AGENTS.md` 的测试和注释章节。
- Produces: 模型任务和生产代码任务按需读取的完整规范。

- [ ] 提取并去重基准冻结、逐时间步对照、容差、边界、输出和图表验收规则。
- [ ] 提取并去重公式、单位、状态更新和 Java 注释检查规则。
- [ ] 使用 `rg` 核对模型关键术语均可定位。

### Task 2: 精简根规则和更新导航

**Files:**
- Modify: `AGENTS.md`
- Modify: `PROJECT_GUIDE.md`

**Interfaces:**
- Consumes: Task 1 的详细规则文档。
- Produces: 小于 8 KiB 的根规则和完整文档导航。

- [ ] 将 `AGENTS.md` 改写为强制读取顺序、核心 Git 约束、Gaia 硬边界和条件式路由。
- [ ] 在 `PROJECT_GUIDE.md` 登记新的开发规范入口，保持项目事实不变。
- [ ] 统计文件大小并核对关键语义。

### Task 3: 验证、提交和推送

**Files:**
- Verify: 本计划列出的全部变化文件。

**Interfaces:**
- Consumes: Task 1 和 Task 2 的完整差异。
- Produces: 可审查的任务分支和 PR 材料。

- [ ] 运行链接、关键语义、文件大小、`git diff --check` 和无关文件检查。
- [ ] 明确暂存文件并提交 `docs(process): layer project agent instructions`。
- [ ] 推送 `docs/agents-instruction-layering` 并提供 PR 材料。
