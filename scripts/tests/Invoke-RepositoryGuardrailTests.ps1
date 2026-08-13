[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$guardrailScript = (Resolve-Path (Join-Path $PSScriptRoot '..\Test-RepositoryGuardrails.ps1')).Path
$hookInstallerScript = Join-Path $PSScriptRoot '..\Install-RepositoryHooks.ps1'
$repositoryHook = Join-Path $PSScriptRoot '..\..\.githooks\pre-commit'
$pullRequestTemplate = Join-Path $PSScriptRoot '..\..\.github\pull_request_template.md'
$failures = [System.Collections.Generic.List[string]]::new()
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$utf8Bom = [System.Text.UTF8Encoding]::new($true)

function Write-TextFile {
    param(
        [string]$Path,
        [string]$Content,
        [ValidateSet('LF', 'CRLF')]
        [string]$Newline = 'LF',
        [switch]$Bom
    )

    $normalized = $Content -replace "`r?`n", "`n"
    if ($Newline -eq 'CRLF') {
        $normalized = $normalized -replace "`n", "`r`n"
    }
    $encoding = if ($Bom) { $utf8Bom } else { $utf8NoBom }
    [System.IO.File]::WriteAllText($Path, $normalized, $encoding)
}

function Get-HistoricalDocument {
    param(
        [ValidateSet('设计记录', '实施计划')]
        [string]$Type,
        [string]$Title = '测试文档'
    )

    return @(
        "# $Title"
        ''
        '> [!IMPORTANT]'
        "> 文档类型：$Type<br>"
        '> 生命周期：冻结历史快照<br>'
        '> 当前状态：[PROJECT_STATUS.md](../../../PROJECT_STATUS.md)<br>'
        '> 历史目录：[docs/superpowers](../)<br>'
        '> 使用限制：本文记录任务当时的设计或计划，不用于判断功能当前是否已实现或已验证。'
        ''
        '## 历史正文'
        ''
        '任务当时状态允许保留。'
    ) -join "`n"
}

function Get-ValidPullRequestBody {
    return @(
        '## 状态影响'
        '- [x] 文档治理状态变化'
        '## 检查范围'
        '- [x] 当前入口、历史文档和 Git 差异'
        '## 文档同步'
        '- [x] 已同步当前项目文档'
        '## 历史文档'
        '- [x] 只执行允许的迁移或新增'
        '## 验证分级'
        '- [x] Guardrails 自动化测试'
    ) -join "`n"
}

function Get-UnifiedPullRequestBody {
    return @(
        '## 变更内容'
        '统一 PR 合同。'
        '## 状态影响'
        '- [x] 无状态变化'
        '说明：仅调整流程。'
        '## 验证结果'
        '- [x] 本次仅文档/流程检查'
        '专项范围：不适用'
        '实际命令与结果：Guardrail 通过。'
        '未执行及原因：未运行无关模型测试。'
        '## 注释检查'
        '风险级别：不涉及生产代码'
        '检查范围：仅检查流程文件。'
        '结论：无需修改生产代码注释。'
        '## 文档与 ADR'
        '- [x] 无需更新当前文档'
        '- [x] 未修改既有冻结历史文件'
        '说明：当前状态未变化。'
        '## 风险与未验证项'
        '风险：无。'
        '未验证项：无。'
    ) -join "`n"
}

function Invoke-Git {
    param([string]$Root, [Parameter(ValueFromRemainingArguments)][string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & git -C $Root @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) {
        throw "git $($Arguments -join ' ') failed in $Root`: $($output -join ' ')"
    }
}

function New-TestRepository {
    param(
        [string]$Name,
        [bool]$MarkerEnabled = $true,
        [ValidateSet('LF', 'CRLF')]
        [string]$Newline = 'LF',
        [switch]$Bom
    )

    $root = Join-Path ([System.IO.Path]::GetTempPath()) "hvac-guardrails-$Name-$([guid]::NewGuid())"
    New-Item -ItemType Directory -Path $root | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $root 'docs/superpowers/specs') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $root 'docs/superpowers/plans') -Force | Out-Null

    Write-TextFile (Join-Path $root 'PROJECT_STATUS.md') "# 当前状态`n" $Newline -Bom:$Bom
    Write-TextFile (Join-Path $root 'PROJECT_GUIDE.md') "# 项目指南`n" $Newline -Bom:$Bom
    $agentsContent = @(
        '# 规则'
        ''
        '<!-- current-status-source: PROJECT_STATUS.md -->'
        ''
        '状态评估默认排除 `docs/superpowers/specs` 与 `docs/superpowers/plans`。'
    ) -join "`n"
    Write-TextFile (Join-Path $root 'AGENTS.md') $agentsContent $Newline -Bom:$Bom
    Write-TextFile (Join-Path $root 'docs/superpowers/specs/example-design.md') (Get-HistoricalDocument '设计记录') $Newline -Bom:$Bom
    Write-TextFile (Join-Path $root 'docs/superpowers/plans/example-plan.md') (Get-HistoricalDocument '实施计划') $Newline -Bom:$Bom
    if ($MarkerEnabled) {
        Write-TextFile (Join-Path $root 'docs/superpowers/.history-guardrails-enabled') "enabled: true`nschema: 1`n" $Newline -Bom:$Bom
    }

    Invoke-Git $root init --initial-branch=main
    Invoke-Git $root config core.autocrlf false
    Invoke-Git $root config user.email 'guardrails@example.invalid'
    Invoke-Git $root config user.name 'Repository Guardrails Test'
    Invoke-Git $root add -- .
    Invoke-Git $root commit -m baseline
    Invoke-Git $root branch base
    Invoke-Git $root switch -c feature
    return $root
}

function Remove-TestRepository {
    param([string]$Root)

    $fullRoot = [System.IO.Path]::GetFullPath($Root)
    $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    $insideTemp = $fullRoot.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase)
    $expectedName = (Split-Path -Leaf $fullRoot) -like 'hvac-guardrails-*'
    if (-not $insideTemp -or -not $expectedName) {
        throw "Refusing to remove unexpected test path: $fullRoot"
    }
    Remove-Item -LiteralPath $fullRoot -Recurse -Force
}

function Invoke-Guardrails {
    param(
        [string]$Root,
        [ValidateSet('WorkingTree', 'Staged', 'PullRequest')]
        [string]$Mode = 'WorkingTree',
        [string]$BaseRef = 'base',
        [string]$PullRequestBody = ''
    )

    $arguments = @(
        '-NoProfile'
        '-ExecutionPolicy'
        'Bypass'
        '-File'
        $guardrailScript
        '-RepositoryRoot'
        $Root
        '-Mode'
        $Mode
        '-BaseRef'
        $BaseRef
    )
    if ($Mode -eq 'PullRequest') {
        $arguments += @('-PullRequestBody', $PullRequestBody)
    }
    $output = & powershell.exe @arguments 2>&1
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = ($output | Out-String) }
}

function Invoke-GuardrailCase {
    param(
        [string]$Name,
        [scriptblock]$Arrange,
        [int]$ExpectedExitCode,
        [ValidateSet('WorkingTree', 'Staged', 'PullRequest')]
        [string]$Mode = 'WorkingTree',
        [bool]$MarkerEnabled = $true,
        [string]$PullRequestBody = '',
        [ValidateSet('LF', 'CRLF')]
        [string]$Newline = 'LF',
        [switch]$Bom
    )

    $root = New-TestRepository $Name $MarkerEnabled $Newline -Bom:$Bom
    try {
        & $Arrange $root
        $result = Invoke-Guardrails $root $Mode 'base' $PullRequestBody
        if ($result.ExitCode -ne $ExpectedExitCode) {
            $failures.Add("$Name expected $ExpectedExitCode but got $($result.ExitCode): $($result.Output.Trim())")
        }
    } catch {
        $failures.Add("$Name threw: $($_.Exception.Message)")
    } finally {
        Remove-TestRepository $root
    }
}

function Invoke-HookInstaller {
    param(
        [string]$Root,
        [switch]$Check
    )

    $arguments = @(
        '-NoProfile'
        '-ExecutionPolicy'
        'Bypass'
        '-File'
        $hookInstallerScript
        '-RepositoryRoot'
        $Root
    )
    if ($Check) {
        $arguments += '-Check'
    }
    $output = & powershell.exe @arguments 2>&1
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = ($output | Out-String) }
}

$noChange = { param($root) }

Invoke-GuardrailCase 'valid-lf' $noChange 0
Invoke-GuardrailCase 'valid-crlf-bom' $noChange 0 -Newline CRLF -Bom

Invoke-GuardrailCase 'missing-type' {
    param($root)
    $path = Join-Path $root 'docs/superpowers/specs/example-design.md'
    (Get-Content -LiteralPath $path -Raw -Encoding UTF8).Replace('> 文档类型：设计记录<br>', '') |
        Set-Content -LiteralPath $path -Encoding UTF8
} 1

Invoke-GuardrailCase 'wrong-type' {
    param($root)
    $path = Join-Path $root 'docs/superpowers/specs/example-design.md'
    (Get-Content -LiteralPath $path -Raw -Encoding UTF8).Replace('文档类型：设计记录', '文档类型：实施计划') |
        Set-Content -LiteralPath $path -Encoding UTF8
} 1

foreach ($case in @(
    @{ Name = 'missing-lifecycle'; Text = '> 生命周期：冻结历史快照<br>' },
    @{ Name = 'missing-current-link'; Text = '> 当前状态：[PROJECT_STATUS.md](../../../PROJECT_STATUS.md)<br>' },
    @{ Name = 'missing-history-link'; Text = '> 历史目录：[docs/superpowers](../)<br>' },
    @{ Name = 'missing-usage'; Text = '> 使用限制：本文记录任务当时的设计或计划，不用于判断功能当前是否已实现或已验证。' }
)) {
    $removeText = $case.Text
    Invoke-GuardrailCase $case.Name {
        param($root)
        $path = Join-Path $root 'docs/superpowers/specs/example-design.md'
        (Get-Content -LiteralPath $path -Raw -Encoding UTF8).Replace($removeText, '') |
            Set-Content -LiteralPath $path -Encoding UTF8
    } 1
}

Invoke-GuardrailCase 'broken-current-link' {
    param($root)
    $path = Join-Path $root 'docs/superpowers/specs/example-design.md'
    (Get-Content -LiteralPath $path -Raw -Encoding UTF8).Replace('../../../PROJECT_STATUS.md', '../../../MISSING_STATUS.md') |
        Set-Content -LiteralPath $path -Encoding UTF8
} 1

Invoke-GuardrailCase 'dynamic-agents-status' {
    param($root)
    Add-Content -LiteralPath (Join-Path $root 'AGENTS.md') -Encoding UTF8 -Value '当前可运行实现是旧版本。'
} 1

Invoke-GuardrailCase 'new-compliant-design' {
    param($root)
    Write-TextFile (Join-Path $root 'docs/superpowers/specs/new-design.md') (Get-HistoricalDocument '设计记录' '新设计')
    Invoke-Git $root add -- docs/superpowers/specs/new-design.md
    Invoke-Git $root commit -m add-design
} 0 -Mode PullRequest -PullRequestBody (Get-ValidPullRequestBody)

foreach ($operation in @('modify', 'delete', 'rename')) {
    Invoke-GuardrailCase "frozen-$operation" {
        param($root)
        $path = Join-Path $root 'docs/superpowers/specs/example-design.md'
        if ($operation -eq 'modify') { Add-Content -LiteralPath $path -Encoding UTF8 -Value 'changed' }
        if ($operation -eq 'delete') { Remove-Item -LiteralPath $path }
        if ($operation -eq 'rename') { Move-Item -LiteralPath $path -Destination (Join-Path (Split-Path $path) 'renamed-design.md') }
        Invoke-Git $root add --all
        Invoke-Git $root commit -m $operation
    } 1 -Mode PullRequest -PullRequestBody (Get-ValidPullRequestBody)
}

foreach ($operation in @('modify', 'delete')) {
    Invoke-GuardrailCase "marker-$operation" {
        param($root)
        $marker = Join-Path $root 'docs/superpowers/.history-guardrails-enabled'
        if ($operation -eq 'modify') { Set-Content -LiteralPath $marker -Encoding UTF8 -Value "enabled: true`nschema: 2" }
        if ($operation -eq 'delete') { Remove-Item -LiteralPath $marker }
        Invoke-Git $root add --all
        Invoke-Git $root commit -m "marker-$operation"
    } 1 -Mode PullRequest -PullRequestBody (Get-ValidPullRequestBody)
}

Invoke-GuardrailCase 'initial-migration' {
    param($root)
    Add-Content -LiteralPath (Join-Path $root 'docs/superpowers/specs/example-design.md') -Encoding UTF8 -Value 'migration note'
    Write-TextFile (Join-Path $root 'docs/superpowers/.history-guardrails-enabled') "enabled: true`nschema: 1`n"
    Invoke-Git $root add --all
    Invoke-Git $root commit -m migration
} 0 -Mode PullRequest -MarkerEnabled:$false -PullRequestBody (Get-ValidPullRequestBody)

$legacyBody = Get-ValidPullRequestBody
Invoke-GuardrailCase 'pr-legacy-body-compatible' $noChange 0 -Mode PullRequest -PullRequestBody $legacyBody

$validBody = Get-UnifiedPullRequestBody
foreach ($heading in @('变更内容', '状态影响', '验证结果', '注释检查', '文档与 ADR', '风险与未验证项')) {
    $bodyWithoutSection = [regex]::Replace(
        $validBody,
        "(?ms)^## $([regex]::Escape($heading))\r?\n.*?(?=^## |\z)",
        ''
    )
    Invoke-GuardrailCase "pr-missing-$heading" $noChange 1 -Mode PullRequest -PullRequestBody $bodyWithoutSection
}

$uncheckedBody = $validBody.Replace('- [x] 无状态变化', '- [ ] 无状态变化')
Invoke-GuardrailCase 'pr-unchecked-section' $noChange 1 -Mode PullRequest -PullRequestBody $uncheckedBody

$missingFieldBody = $validBody.Replace('未验证项：无。', '未验证项：')
Invoke-GuardrailCase 'pr-missing-field' $noChange 1 -Mode PullRequest -PullRequestBody $missingFieldBody

if (-not (Test-Path -LiteralPath $pullRequestTemplate)) {
    $failures.Add('pull-request-template missing')
} else {
    $templateText = [System.IO.File]::ReadAllText($pullRequestTemplate)
    foreach ($heading in @('变更内容', '状态影响', '验证结果', '注释检查', '文档与 ADR', '风险与未验证项')) {
        if ($templateText -notmatch ('(?m)^##\s+' + [regex]::Escape($heading) + '\s*$')) {
            $failures.Add("pull-request-template missing heading: $heading")
        }
    }
    foreach ($field in @('说明：', '专项范围：', '实际命令与结果：', '未执行及原因：', '风险级别：', '检查范围：', '结论：', '风险：', '未验证项：')) {
        if (-not $templateText.Contains($field)) {
            $failures.Add("pull-request-template missing field: $field")
        }
    }
}

if (-not (Test-Path -LiteralPath $hookInstallerScript) -or -not (Test-Path -LiteralPath $repositoryHook)) {
    $failures.Add('hook-files-missing expected Hook and installer implementation files')
} else {
    $hookText = [System.IO.File]::ReadAllText($repositoryHook)
    if ($hookText.Contains("`r")) {
        $failures.Add('hook-line-endings expected LF-only content')
    }
    foreach ($requiredText in @(
        'command -v pwsh',
        'Test-RepositoryGuardrails.ps1 -Mode Staged -BaseRef origin/main',
        'powershell.exe -NoProfile -ExecutionPolicy Bypass'
    )) {
        if (-not $hookText.Contains($requiredText)) {
            $failures.Add("hook-content missing: $requiredText")
        }
    }

    $hookTestRoot = New-TestRepository 'repository-hook'
    try {
        $checkBeforeInstall = Invoke-HookInstaller $hookTestRoot -Check
        if ($checkBeforeInstall.ExitCode -eq 0) {
            $failures.Add('hook-check-before-install expected nonzero exit code')
        }

        $installResult = Invoke-HookInstaller $hookTestRoot
        if ($installResult.ExitCode -ne 0) {
            $failures.Add("hook-install expected 0 but got $($installResult.ExitCode): $($installResult.Output.Trim())")
        }
        $configuredPath = (& git -C $hookTestRoot config --local --get core.hooksPath | Select-Object -First 1)
        $configuredPath = if ($null -eq $configuredPath) { '' } else { $configuredPath.ToString().Trim() }
        if ($configuredPath -ne '.githooks') {
            $failures.Add("hook-install configured unexpected path: $configuredPath")
        }

        $checkAfterInstall = Invoke-HookInstaller $hookTestRoot -Check
        if ($checkAfterInstall.ExitCode -ne 0 -or $checkAfterInstall.Output -notmatch 'REPOSITORY_HOOKS_OK') {
            $failures.Add("hook-check-after-install failed: $($checkAfterInstall.Output.Trim())")
        }

        New-Item -ItemType Directory -Path (Join-Path $hookTestRoot 'scripts') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $hookTestRoot '.githooks') -Force | Out-Null
        Copy-Item -LiteralPath $guardrailScript -Destination (Join-Path $hookTestRoot 'scripts/Test-RepositoryGuardrails.ps1')
        Copy-Item -LiteralPath $repositoryHook -Destination (Join-Path $hookTestRoot '.githooks/pre-commit')
        Invoke-Git $hookTestRoot update-ref refs/remotes/origin/main base
        Add-Content -LiteralPath (Join-Path $hookTestRoot 'docs/superpowers/specs/example-design.md') -Encoding UTF8 -Value 'forbidden change'
        Invoke-Git $hookTestRoot add -- docs/superpowers/specs/example-design.md

        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $hookOutput = & git -C $hookTestRoot commit -m forbidden-change 2>&1
            $hookExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousPreference
        }
        if ($hookExitCode -eq 0 -or ($hookOutput | Out-String) -notmatch '冻结历史文件') {
            $failures.Add("hook-frozen-change expected rejection: $($hookOutput | Out-String)")
        }
    } catch {
        $failures.Add("repository-hook threw: $($_.Exception.Message)")
    } finally {
        Remove-TestRepository $hookTestRoot
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Output "FAILED: $_" }
    exit 1
}

Write-Output 'REPOSITORY_GUARDRAIL_TESTS_OK'
exit 0
