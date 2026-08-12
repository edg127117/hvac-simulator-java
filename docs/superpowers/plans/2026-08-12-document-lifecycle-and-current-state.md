# 文档生命周期与当前状态整改实施计划

> [!IMPORTANT]
> 文档类型：实施计划<br>
> 生命周期：冻结历史快照<br>
> 当前状态：[PROJECT_STATUS.md](../../../PROJECT_STATUS.md)<br>
> 历史目录：[docs/superpowers](../)<br>
> 使用限制：本文记录任务当时的实施安排，不用于判断功能当前是否已实现或已验证。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 冻结已确认设计和已批准计划，并建立不会被旧记录误导的当前状态判定规则。

**Architecture:** `AGENTS.md` 承载不可突破的文档生命周期规则，`PROJECT_GUIDE.md` 承载当前正式设计导航和历史文档使用边界，`PROJECT_STATUS.md` 只记录当前实现与验证状态。既有历史设计和计划保持不变，所有新决策通过新的带日期文档承接。

**Tech Stack:** Markdown、Git、PowerShell

## Global Constraints

- 不修改任何既有 `docs/superpowers/specs` 或 `docs/superpowers/plans` 正文。
- 已确认设计和已批准计划冻结；实现、测试、合并和勘误不得回写历史文档。
- 当前状态必须以当前代码、自动化测试、Git 合并关系和本轮实际验证证据为先。
- 旧计划未勾选项、旧设计未来描述不得单独把已完成功能判定为未实现。
- 本次不修改生产代码、测试、参考资产、模型行为或验收结论。
- 本次不增加 CI、Git Hook 或文档生成工具。

---

### Task 1: 固化历史文档冻结规则

**Files:**
- Modify: `AGENTS.md`
- Modify: `PROJECT_GUIDE.md`

**Interfaces:**
- Consumes: `docs/superpowers/specs/2026-08-12-document-lifecycle-and-current-state-design.md` 中确认的生命周期和证据顺序。
- Produces: 所有后续任务必须遵守的冻结规则、当前设计导航和历史记录使用边界。

- [ ] **Step 1: 在 `AGENTS.md` 增加当前状态判定规则**

在“建立任务上下文”中明确：判断实现状态必须核对当前代码、测试、Git 和 `PROJECT_STATUS.md`；旧设计、旧计划和未勾选项只提供历史背景，不能单独把已有证据支持的能力降级为未实现。

- [ ] **Step 2: 在 `AGENTS.md` 增加冻结和新文档承接规则**

在“信息分层与交付”中明确：设计经用户确认、计划经批准进入实施后冻结；后续实现、测试、合并、需求变化和勘误必须通过新文档、`PROJECT_GUIDE.md` 或 `PROJECT_STATUS.md` 承接，不得修改冻结正文。

- [ ] **Step 3: 在 `AGENTS.md` 增加交付差异检查**

要求交付前使用 Git 差异确认没有修改既有历史设计和计划；如果任务确需新的设计或计划，只允许新增带日期文件并说明承接关系。

- [ ] **Step 4: 重构 `PROJECT_GUIDE.md` 文档导航**

将导航区分为“当前有效入口”“当前正式设计”“冻结历史记录”，并加入：

- `docs/superpowers/specs/2026-08-12-document-lifecycle-and-current-state-design.md` 作为当前文档治理设计；
- `docs/superpowers/plans/2026-08-12-document-lifecycle-and-current-state.md` 作为本次冻结实施记录；
- 旧计划复选框和旧设计未来时态不代表当前状态；
- 正式设计变化时新建带日期设计并更新导航，不回写旧设计。

- [ ] **Step 5: 定向检查规则文本**

Run:

```powershell
rg -n "冻结|不得.*回写|旧计划|当前代码|PROJECT_STATUS|新建.*文档" AGENTS.md PROJECT_GUIDE.md
```

Expected: 两个文件同时包含冻结、状态证据、旧记录边界和新文档承接要求。

- [ ] **Step 6: 提交规则与导航整改**

```powershell
git add -- AGENTS.md PROJECT_GUIDE.md
git diff --cached --check
git diff --cached -- AGENTS.md PROJECT_GUIDE.md
git commit -m "docs(process): 冻结历史设计与计划"
```

Expected: 暂存和提交仅包含 `AGENTS.md`、`PROJECT_GUIDE.md`。

### Task 2: 修正当前状态并验证历史文件未被修改

**Files:**
- Modify: `PROJECT_STATUS.md`

**Interfaces:**
- Consumes: 当前 `origin/main` 的代码、测试和 Git 合并关系，以及 Task 1 建立的状态判定规则。
- Produces: 与当前主线一致的完成、未完成、验证边界、下一步和文档健康入口。

- [ ] **Step 1: 修正 `PROJECT_STATUS.md` 状态基准**

将“当前任务分支尚未合并”等过期表述改为：`main` 已包含版本独立参数目录、参数归属和可选冷却塔效率发送；不得把旧任务计划或旧分支状态当作当前未实现证据。

- [ ] **Step 2: 修正下一步和文档健康**

删除已经完成的旧任务分支 PR 审查/合并事项；保留冷却塔完整联调、真实现场验证、持久化和自由拓扑后续任务。文档健康增加本次冻结设计与实施记录，并明确既有设计和计划不再回写。

- [ ] **Step 3: 核对当前状态关键事实**

Run:

```powershell
git merge-base --is-ancestor origin/feature/gaia-versioned-parameters-tower-efficiency origin/main
rg -n "main.*已包含|历史|冻结|旧计划|尚未合并|PR 审查" PROJECT_STATUS.md
```

Expected: Git 命令返回 0；状态文档包含当前主线和历史边界，不再包含旧任务“尚未合并”或待 PR 审查表述。

- [ ] **Step 4: 验证既有历史设计和计划未被修改**

Run:

```powershell
git diff --name-only --diff-filter=M origin/main -- docs/superpowers/specs docs/superpowers/plans
```

Expected: 无输出。相对 `origin/main` 只允许新增本次设计和计划，不允许修改既有历史文件。

- [ ] **Step 5: 验证本地 Markdown 链接和最终差异**

Run:

```powershell
$files = @('AGENTS.md', 'PROJECT_GUIDE.md', 'PROJECT_STATUS.md')
$missing = foreach ($file in $files) {
    $content = Get-Content -LiteralPath $file -Raw -Encoding UTF8
    $base = Split-Path -Parent $file
    if (-not $base) { $base = '.' }
    foreach ($match in [regex]::Matches($content, '\]\(([^)#]+)(?:#[^)]+)?\)')) {
        $target = Join-Path $base $match.Groups[1].Value
        if (-not (Test-Path -LiteralPath $target)) { "$file -> $($match.Groups[1].Value)" }
    }
}
if ($missing) { $missing; exit 1 }
git diff --check origin/main
git status --short
```

Expected: 没有缺失链接，`git diff --check` 返回 0，状态只包含本次授权文档。

- [ ] **Step 6: 提交当前状态修正**

```powershell
git add -- PROJECT_STATUS.md
git diff --cached --check
git diff --cached -- PROJECT_STATUS.md
git commit -m "docs(status): 修正主线与历史记录边界"
```

Expected: 提交只包含 `PROJECT_STATUS.md`；不运行 Java、前端或仿真测试。

### Task 3: 完成交付检查

**Files:**
- Verify only: `AGENTS.md`
- Verify only: `PROJECT_GUIDE.md`
- Verify only: `PROJECT_STATUS.md`
- Verify only: `docs/superpowers/specs/2026-08-12-document-lifecycle-and-current-state-design.md`
- Verify only: `docs/superpowers/plans/2026-08-12-document-lifecycle-and-current-state.md`

**Interfaces:**
- Consumes: Task 1 和 Task 2 的提交结果。
- Produces: 可推送、可审查且不改写旧历史文档的任务分支。

- [ ] **Step 1: 检查分支范围和提交历史**

Run:

```powershell
git status --short --branch
git log --oneline origin/main..HEAD
git diff --stat origin/main...HEAD
git diff --name-status origin/main...HEAD
```

Expected: 工作区干净；提交和差异只属于本次文档治理任务。

- [ ] **Step 2: 再次检查冻结文件差异和敏感信息**

Run:

```powershell
git diff --name-only --diff-filter=M origin/main...HEAD -- docs/superpowers/specs docs/superpowers/plans
rg -n -i "password|token|secret|jwt|私钥" AGENTS.md PROJECT_GUIDE.md PROJECT_STATUS.md docs/superpowers/specs/2026-08-12-document-lifecycle-and-current-state-design.md docs/superpowers/plans/2026-08-12-document-lifecycle-and-current-state.md
```

Expected: 第一条命令无输出；第二条命令不出现真实凭据或敏感值。

- [ ] **Step 3: 推送并创建 PR**

```powershell
git push -u origin codex/freeze-historical-documents
$body = @"
## 变更范围
- 冻结已确认设计和已批准计划，后续变化只通过新文档承接
- 明确当前状态的证据顺序，禁止旧记录降级已完成功能
- 修正当前主线和历史记录边界

## 非目标
- 不修改生产代码、测试、参考资产或模型行为
- 不回写任何既有历史设计和计划

## 验证
- Markdown 本地链接检查通过
- 关键规则术语检查通过
- 既有历史设计和计划修改检查无输出
- git diff --check 通过
- 未运行 Java、前端或仿真测试：本次为纯文档治理整改
"@
gh pr create --base main --head codex/freeze-historical-documents --title "docs: 冻结历史文档并修正当前状态判定" --body $body
```

Expected: 分支推送成功并返回真实 PR URL；未创建 PR 前不得声称整改已进入 `main`。
