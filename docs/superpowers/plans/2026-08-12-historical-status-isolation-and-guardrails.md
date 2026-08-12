# 历史状态隔离与自动防复发实施计划

> [!IMPORTANT]
> 文档类型：实施计划<br>
> 生命周期：冻结历史快照<br>
> 当前状态：[PROJECT_STATUS.md](../../../PROJECT_STATUS.md)<br>
> 历史目录：[docs/superpowers](../)<br>
> 使用限制：本文记录本次任务确认的实施安排，不用于判断功能当前是否已实现或已验证。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让旧设计和旧计划不能再被误用为当前状态，并以本地 Hook、GitHub CI、PR 模板和 required check 自动防止同类问题复发。

**Architecture:** 当前能力只从代码、测试、Git、`PROJECT_STATUS.md` 和稳定项目地图判定；历史设计与计划统一成为带醒目警告的冻结快照。PowerShell Guardrails 作为唯一规则引擎，由隔离测试、本地 Hook 和 GitHub Actions 共同调用，并用基线分支中的启用标识把一次性迁移与后续冻结检查分开。

**Tech Stack:** Markdown、PowerShell 7/Windows PowerShell 5.1、Git hooks、GitHub Actions、GitHub branch protection/ruleset

## Global Constraints

- 不修改 Java、Vue、仿真公式、参数、参考资产或业务行为。
- 当前状态必须先核对代码、自动化测试、Git 合并关系和实际验证，再读取 `PROJECT_STATUS.md` 与 `PROJECT_GUIDE.md`。
- 状态评估默认排除 `docs/superpowers/specs` 与 `docs/superpowers/plans`。
- 一次性迁移只允许增加历史警告、重命名任务当时状态标签、移除冒充当前入口的动态能力总表，并修正三个当前入口。
- 不重新勾选旧计划，不改写设计决策、接口合同、兼容边界、验收标准和实施步骤。
- Guardrails 启用标识进入 `main` 后，既有历史文件不得修改、删除或重命名；新文件必须从创建时符合统一警告格式。
- 纯文档与仓库流程整改不运行无关 Java、前端或仿真测试。

---

### Task 1: 以隔离测试建立 Guardrails 规则引擎

**Files:**
- Create: `scripts/Test-RepositoryGuardrails.ps1`
- Create: `scripts/tests/Invoke-RepositoryGuardrailTests.ps1`

**Interfaces:**
- Consumes: Git 工作树、索引、`origin/main` 基线、历史 Markdown 文件和可选 PR 正文。
- Produces: `Test-RepositoryGuardrails.ps1 -Mode PullRequest -BaseRef origin/main -PullRequestBody $body`，并支持 `WorkingTree`、`Staged` 两种模式；成功返回 0，违规返回 1 并列出文件与规则。

- [ ] **Step 1: 创建失败场景测试框架**

在 `scripts/tests/Invoke-RepositoryGuardrailTests.ps1` 中建立临时 Git 仓库 helper：

```powershell
$ErrorActionPreference = 'Stop'
$guardrailScript = (Resolve-Path (Join-Path $PSScriptRoot '..\Test-RepositoryGuardrails.ps1')).Path
$failures = [System.Collections.Generic.List[string]]::new()

function New-TestRepository([string]$Name) {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) "hvac-guardrails-$Name-$([guid]::NewGuid())"
    New-Item -ItemType Directory -Path $root | Out-Null
    git -C $root init --initial-branch=main | Out-Null
    git -C $root config user.email 'guardrails@example.invalid'
    git -C $root config user.name 'Repository Guardrails Test'
    New-Item -ItemType Directory -Path (Join-Path $root 'docs/superpowers/specs') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $root 'docs/superpowers/plans') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $root 'PROJECT_STATUS.md') -Encoding utf8 -Value '# 当前状态'
    Set-Content -LiteralPath (Join-Path $root 'PROJECT_GUIDE.md') -Encoding utf8 -Value '# 项目指南'
    Set-Content -LiteralPath (Join-Path $root 'AGENTS.md') -Encoding utf8 -Value @'
# 规则
<!-- current-status-source: PROJECT_STATUS.md -->
状态评估默认排除 `docs/superpowers/specs` 与 `docs/superpowers/plans`。
'@
    return $root
}

function Invoke-GuardrailCase([string]$Name, [scriptblock]$Arrange, [int]$ExpectedExitCode) {
    $root = New-TestRepository $Name
    try {
        & $Arrange $root
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $guardrailScript -RepositoryRoot $root
        if ($LASTEXITCODE -ne $ExpectedExitCode) {
            $failures.Add("$Name expected $ExpectedExitCode but got $LASTEXITCODE")
        }
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force
    }
}
```

加入缺少脚本规则、缺少历史警告、错误文档类型、缺少两个链接、错误链接目标、缺少使用限制、动态 `AGENTS.md` 状态和缺失 PR 字段的失败用例。测试末尾若 `$failures.Count -gt 0` 则输出全部失败并 `exit 1`，否则输出 `REPOSITORY_GUARDRAIL_TESTS_OK`。

- [ ] **Step 2: 运行测试确认规则引擎尚不完整**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/Invoke-RepositoryGuardrailTests.ps1
```

Expected: 非零退出，至少报告历史警告或 PR 正文场景未按预期失败。

- [ ] **Step 3: 实现 Guardrails 参数和统一错误收集**

`scripts/Test-RepositoryGuardrails.ps1` 使用以下入口：

```powershell
[CmdletBinding()]
param(
    [ValidateSet('WorkingTree', 'Staged', 'PullRequest')]
    [string]$Mode = 'WorkingTree',
    [string]$BaseRef = 'origin/main',
    [AllowEmptyString()]
    [string]$PullRequestBody = '',
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$violations = [System.Collections.Generic.List[string]]::new()

function Add-Violation([string]$Message) {
    $script:violations.Add($Message)
}
```

脚本定位全部 `docs/superpowers/specs/*.md` 和 `docs/superpowers/plans/*.md`，按目录要求文档类型分别为“设计记录”和“实施计划”。每份文件前 12 行必须包含：

```text
> [!IMPORTANT]
> 文档类型：设计记录<br>
> 生命周期：冻结历史快照<br>
> 当前状态：[PROJECT_STATUS.md](../../../PROJECT_STATUS.md)<br>
> 历史目录：[docs/superpowers](../)<br>
> 使用限制：
```

对两个 Markdown 链接按文件目录解析并使用 `Test-Path -LiteralPath` 验证目标存在。

- [ ] **Step 4: 实现冻结差异和一次性迁移判定**

启用标识固定为 `docs/superpowers/.history-guardrails-enabled`。PullRequest/Staged 模式先执行：

```powershell
$baseHasMarker = $false
git -C $RepositoryRoot cat-file -e "$BaseRef`:docs/superpowers/.history-guardrails-enabled" 2>$null
if ($LASTEXITCODE -eq 0) { $baseHasMarker = $true }
```

基线已有标识时，通过 `git diff --name-status --find-renames` 检查 `docs/superpowers/specs` 和 `docs/superpowers/plans`：只允许 `A`，拒绝 `M`、`D`、`R`、`C`、`T`。同时拒绝删除或修改启用标识。基线没有标识时允许本次迁移修改，但要求当前工作树必须新增内容为：

```text
enabled: true
schema: 1
```

- [ ] **Step 5: 实现当前入口和 PR 正文检查**

`AGENTS.md` 必须包含：

```text
<!-- current-status-source: PROJECT_STATUS.md -->
状态评估默认排除 `docs/superpowers/specs` 与 `docs/superpowers/plans`
```

并禁止以下动态状态表达重新进入 `AGENTS.md`：

```regex
当前可运行实现|当前只有接入设计|仍是待实施目标|尚未形成.*实现
```

PullRequest 模式要求 PR 正文存在五个二级标题，且每节至少有一个 `- [x]` 或 `- [X]`：

```text
## 状态影响
## 检查范围
## 文档同步
## 历史文档
## 验证分级
```

- [ ] **Step 6: 补齐编码、链接、冻结和 PR 测试矩阵**

在隔离仓库中覆盖：LF、CRLF、UTF-8 BOM；新增合规设计/计划通过；缺失任一标识失败；链接目标缺失失败；基线启用后修改、删除、重命名冻结文件失败；删除/修改启用标识失败；动态 `AGENTS.md` 状态失败；五个 PR 章节分别缺失或未选择失败；基线未启用时一次迁移通过，启用后不能再次迁移。

- [ ] **Step 7: 运行测试并提交规则引擎**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/Invoke-RepositoryGuardrailTests.ps1
```

Expected: `REPOSITORY_GUARDRAIL_TESTS_OK`，退出码 0。

Commit:

```powershell
git add -- scripts/Test-RepositoryGuardrails.ps1 scripts/tests/Invoke-RepositoryGuardrailTests.ps1
git diff --cached --check
git commit -m "test(process): 建立历史状态 Guardrails"
```

### Task 2: 迁移当前入口与全部历史文档

**Files:**
- Modify: `AGENTS.md`
- Modify: `PROJECT_GUIDE.md`
- Modify: `PROJECT_STATUS.md`
- Create: `docs/superpowers/.history-guardrails-enabled`
- Modify: all pre-existing Markdown files under `docs/superpowers/specs`
- Modify: all pre-existing Markdown files under `docs/superpowers/plans`
- Verify unchanged body decisions in every migrated design and plan

**Interfaces:**
- Consumes: Task 1 的统一警告 schema 和迁移模式。
- Produces: 当前入口无过期动态能力、全部历史文件可见标识完整、Guardrails 进入启用状态。

- [ ] **Step 1: 修正 `AGENTS.md` 动态状态污染**

在“建立任务上下文”加入：

```markdown
<!-- current-status-source: PROJECT_STATUS.md -->

涉及当前能力、完成情况、范围、风险或下一步时，状态评估默认排除 `docs/superpowers/specs` 与 `docs/superpowers/plans`。只有用户明确要求追溯历史、核对设计理由或验证兼容边界时才读取历史目录；即使读取当前正式设计，也不得把其中的历史完成状态作为当前结论。
```

删除“当前可运行实现是 Java 21……Spring Boot、Vue、MQTT 待实施”和“Gaia 1.1 当前只有接入设计”两条过期动态状态，替换为稳定边界：模型版本之间参数、字段、语义和冻结基准必须隔离；具体完成状态统一查看 `PROJECT_STATUS.md`。

- [ ] **Step 2: 迁移 11 份设计记录**

为以下文件标题后添加“设计记录”统一警告块，其中新设计已经合规，只做核验：

```text
docs/superpowers/specs/2026-08-04-gaia-java-port-design.md
docs/superpowers/specs/2026-08-04-hvac-simulation-platform-design.md
docs/superpowers/specs/2026-08-06-gaia-1.1-integration-design.md
docs/superpowers/specs/2026-08-08-agents-instruction-layering-design.md
docs/superpowers/specs/2026-08-09-agent-skills-layering-design.md
docs/superpowers/specs/2026-08-10-device-runtime-contract-and-shared-energy-data-model-design.md
docs/superpowers/specs/2026-08-10-free-topology-simulation-platform-design.md
docs/superpowers/specs/2026-08-11-gaia-versioned-parameters-and-tower-efficiency-design.md
docs/superpowers/specs/2026-08-12-document-lifecycle-and-current-state-design.md
docs/superpowers/specs/2026-08-12-parameter-panel-spacing-design.md
docs/superpowers/specs/2026-08-12-historical-status-isolation-and-guardrails-design.md
```

将原有 `> 状态：` 改为 `> 任务当时状态：`，将原有 `## 状态` 改为 `## 任务当时状态`。正文中的设计状态机、运行状态、质量状态和接口状态术语不得替换。

- [ ] **Step 3: 迁移 9 份实施计划**

为以下文件标题后添加“实施计划”统一警告块，其中本计划从创建时已经合规：

```text
docs/superpowers/plans/2026-08-04-gaia-java-port.md
docs/superpowers/plans/2026-08-08-agents-instruction-layering.md
docs/superpowers/plans/2026-08-09-agent-skills-layering.md
docs/superpowers/plans/2026-08-10-device-runtime-contract-and-shared-energy-data-model.md
docs/superpowers/plans/2026-08-10-gaia-1.1-platform-mvp.md
docs/superpowers/plans/2026-08-10-generic-device-topology-engine-foundation.md
docs/superpowers/plans/2026-08-12-document-lifecycle-and-current-state.md
docs/superpowers/plans/2026-08-12-gaia-versioned-parameters-tower-efficiency.md
docs/superpowers/plans/2026-08-12-historical-status-isolation-and-guardrails.md
```

将原有 `> 状态：` 改为 `> 任务当时状态：`，保留全部复选框原状。

- [ ] **Step 4: 更新当前导航和状态入口**

`PROJECT_GUIDE.md` 的当前正式设计增加本设计；文档生命周期明确统一警告 schema、状态检索隔离和 Guardrails 入口。`PROJECT_STATUS.md` 的文档健康增加本设计、计划、Guardrails、Hook/CI 当前验证层级；不得把 CI 存在写成 required check 已启用，除非已经取得 GitHub 设置证据。

- [ ] **Step 5: 新增 Guardrails 启用标识**

创建 `docs/superpowers/.history-guardrails-enabled`：

```text
enabled: true
schema: 1
```

- [ ] **Step 6: 用迁移模式验证真实仓库**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-RepositoryGuardrails.ps1 -Mode PullRequest -BaseRef origin/main -PullRequestBody @'
## 状态影响
- [x] 文档治理状态变化
## 检查范围
- [x] 当前入口、历史设计、历史计划和 Git 差异
## 文档同步
- [x] 已同步 PROJECT_GUIDE.md 与 PROJECT_STATUS.md
## 历史文档
- [x] 本 PR 是启用标识前唯一一次受控迁移
## 验证分级
- [x] Guardrails 自动化和文档检查
'@
```

Expected: `REPOSITORY_GUARDRAILS_OK`。

- [ ] **Step 7: 检查迁移范围并提交**

Run:

```powershell
rg -L -F '> 生命周期：冻结历史快照<br>' docs/superpowers/specs docs/superpowers/plans
rg -n "^(> 状态：|## 状态)$" docs/superpowers/specs docs/superpowers/plans
git diff --check
git diff --stat
```

Expected: 前两条无输出；差异只包含当前入口、历史 Markdown 和启用标识。

Commit:

```powershell
git add -- AGENTS.md PROJECT_GUIDE.md PROJECT_STATUS.md docs/superpowers/.history-guardrails-enabled docs/superpowers/specs docs/superpowers/plans
git diff --cached --check
git commit -m "docs(process): 隔离历史状态与当前事实"
```

### Task 3: 接入 Hook、CI 和 PR 文档同步

**Files:**
- Create: `.githooks/pre-commit`
- Create: `scripts/Install-RepositoryHooks.ps1`
- Create: `.github/workflows/repository-guardrails.yml`
- Create: `.github/pull_request_template.md`
- Modify: `scripts/tests/Invoke-RepositoryGuardrailTests.ps1`
- Modify: `PROJECT_GUIDE.md`
- Modify: `PROJECT_STATUS.md`

**Interfaces:**
- Consumes: Task 1 的 Guardrails CLI 和 Task 2 的已启用历史 schema。
- Produces: 本地提交拦截、GitHub PR 检查、标准 PR 文档同步字段和安装/验证入口。

- [ ] **Step 1: 增加 Hook 与安装脚本失败测试**

隔离测试验证：未设置 `core.hooksPath` 时安装检查失败；安装后值严格为 `.githooks`；Hook 调用 `Test-RepositoryGuardrails.ps1 -Mode Staged -BaseRef origin/main`；违规暂存历史文件时 Hook 返回非零。

- [ ] **Step 2: 创建跨 Windows/PowerShell 版本 Hook**

`.githooks/pre-commit`：

```sh
#!/bin/sh
set -eu
if command -v pwsh >/dev/null 2>&1; then
  exec pwsh -NoProfile -File scripts/Test-RepositoryGuardrails.ps1 -Mode Staged -BaseRef origin/main
fi
exec powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-RepositoryGuardrails.ps1 -Mode Staged -BaseRef origin/main
```

确保文件以 LF 保存并具有 Git executable bit。

- [ ] **Step 3: 创建仓库级 Hook 安装/检查入口**

`scripts/Install-RepositoryHooks.ps1` 支持 `-Check`：普通模式执行 `git config --local core.hooksPath .githooks`；检查模式读取实际值，不等于 `.githooks` 时返回 1，成功输出 `REPOSITORY_HOOKS_OK`。

- [ ] **Step 4: 创建 PR 模板**

`.github/pull_request_template.md` 使用五个固定章节，每节提供互斥或分级复选项，并要求填写证据：

```markdown
## 状态影响
- [ ] 新增能力
- [ ] 完成既有任务
- [ ] 部分完成或仍有未验证项
- [ ] 无状态变化

## 检查范围
- [ ] 已核对当前代码、测试和 Git
- [ ] 已核对外部环境；不适用时已说明

## 文档同步
- [ ] 已更新 `PROJECT_STATUS.md`
- [ ] 已更新 `PROJECT_GUIDE.md`
- [ ] 无需更新，并已说明原因

## 历史文档
- [ ] 只新增带日期记录，未回写冻结文件
- [ ] 本 PR 不涉及设计或实施计划

## 验证分级
- [ ] 自动化测试
- [ ] 本地运行或浏览器验证
- [ ] 外部联调
- [ ] 现场验证
- [ ] 本次仅文档/流程检查
```

- [ ] **Step 5: 创建 GitHub Actions 工作流**

`.github/workflows/repository-guardrails.yml`：

```yaml
name: Repository Guardrails

on:
  pull_request:
  push:
    branches: [main]

jobs:
  repository-guardrails:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - name: Run guardrail regression tests
        shell: pwsh
        run: ./scripts/tests/Invoke-RepositoryGuardrailTests.ps1
      - name: Validate pull request
        if: github.event_name == 'pull_request'
        shell: pwsh
        env:
          PR_BODY: ${{ github.event.pull_request.body }}
        run: ./scripts/Test-RepositoryGuardrails.ps1 -Mode PullRequest -BaseRef "origin/${{ github.base_ref }}" -PullRequestBody $env:PR_BODY
      - name: Validate main
        if: github.event_name == 'push'
        shell: pwsh
        run: ./scripts/Test-RepositoryGuardrails.ps1 -Mode WorkingTree
```

- [ ] **Step 6: 运行完整 Guardrails 与 Hook 验证**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/Invoke-RepositoryGuardrailTests.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-RepositoryHooks.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-RepositoryHooks.ps1 -Check
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-RepositoryGuardrails.ps1 -Mode WorkingTree
```

Expected: 依次输出 `REPOSITORY_GUARDRAIL_TESTS_OK`、`REPOSITORY_HOOKS_OK` 和 `REPOSITORY_GUARDRAILS_OK`。

- [ ] **Step 7: 更新入口并提交自动化接入**

`PROJECT_GUIDE.md` 记录三个可执行命令和 CI 名称；`PROJECT_STATUS.md` 只标记“脚本、测试、Hook 和工作流已实现并本地验证”，required check 仍标记待合并后配置。

Commit:

```powershell
git add -- .githooks/pre-commit scripts/Install-RepositoryHooks.ps1 .github/workflows/repository-guardrails.yml .github/pull_request_template.md scripts/tests/Invoke-RepositoryGuardrailTests.ps1 PROJECT_GUIDE.md PROJECT_STATUS.md
git diff --cached --check
git commit -m "ci(process): 自动检查历史状态与文档同步"
```

### Task 4: 交付、CI 核验与合并后保护

**Files:**
- Verify only: all files changed by Tasks 1-3
- External setting after merge: GitHub `main` branch protection/ruleset

**Interfaces:**
- Consumes: 三个实现提交和 GitHub Actions 工作流。
- Produces: 可审查 PR、真实 CI 结果、合并后 required check 和清理后的主线。

- [ ] **Step 1: 最终本地验证**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/Invoke-RepositoryGuardrailTests.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-RepositoryHooks.ps1 -Check
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-RepositoryGuardrails.ps1 -Mode WorkingTree
git diff --check origin/main...HEAD
git status --short --branch
git log --oneline origin/main..HEAD
git diff --name-status origin/main...HEAD
```

Expected: 三个 OK 标记；工作区干净；差异只属于文档治理、Hook、CI、模板和测试。

- [ ] **Step 2: 推送并创建正式 PR**

推送 `codex/historical-status-guardrails`，创建目标为 `main` 的非草稿 PR。PR 正文必须勾选五个固定章节，并如实说明未运行 Java、前端和仿真测试。

- [ ] **Step 3: 等待并核验 GitHub CI**

核对 `Repository Guardrails / repository-guardrails` 真实通过；失败时按日志修复脚本、迁移或 PR 字段，不绕过检查。

- [ ] **Step 4: 用户确认合并后启用 required check**

刷新并验证 PR 提交已经进入 `origin/main`，再在 GitHub `main` 分支保护或 Ruleset 中把 `Repository Guardrails / repository-guardrails` 设为 required。不得在没有设置页面或 API 证据时写成已启用。

- [ ] **Step 5: 验证违规 PR 无法合并并清理**

使用不触碰生产代码的临时验证分支修改一份冻结计划，确认 Guardrails 失败且 GitHub 显示合并受阻；随后删除该临时远程分支。最后快进本地 `main`，删除已合并任务分支和远程跟踪引用，并确认工作区干净。
