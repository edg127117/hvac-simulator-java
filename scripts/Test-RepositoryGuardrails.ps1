[CmdletBinding()]
param(
    [ValidateSet('WorkingTree', 'Staged', 'PullRequest')]
    [string]$Mode = 'WorkingTree',
    [string]$BaseRef = 'origin/main',
    [AllowEmptyString()]
    [string]$PullRequestBody = '',
    [string]$RepositoryRoot = ''
)

$ErrorActionPreference = 'Stop'
$RepositoryRoot = if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    Split-Path -Parent $PSScriptRoot
} else {
    $RepositoryRoot
}
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$violations = [System.Collections.Generic.List[string]]::new()
$markerRelativePath = 'docs/superpowers/.history-guardrails-enabled'
$specsRelativePath = 'docs/superpowers/specs'
$plansRelativePath = 'docs/superpowers/plans'

function Add-Violation {
    param([string]$Message)
    $script:violations.Add($Message)
}

function Test-HistoricalDocument {
    param(
        [System.IO.FileInfo]$File,
        [ValidateSet('设计记录', '实施计划')]
        [string]$ExpectedType
    )

    $lines = @(Get-Content -LiteralPath $File.FullName -Encoding UTF8)
    $headerLines = @($lines | Select-Object -First 12)
    $requirements = @(
        '> [!IMPORTANT]'
        "> 文档类型：$ExpectedType<br>"
        '> 生命周期：冻结历史快照<br>'
        '> 当前状态：[PROJECT_STATUS.md](../../../PROJECT_STATUS.md)<br>'
        '> 历史目录：[docs/superpowers](../)<br>'
    )

    foreach ($requirement in $requirements) {
        if ($headerLines -notcontains $requirement) {
            Add-Violation "$($File.FullName): 缺少历史文档标识 '$requirement'"
        }
    }
    if (-not ($headerLines | Where-Object { $_ -like '> 使用限制：*' })) {
        Add-Violation "$($File.FullName): 缺少历史文档使用限制"
    }

    $currentStatusTarget = Join-Path $File.Directory.FullName '../../../PROJECT_STATUS.md'
    if (-not (Test-Path -LiteralPath $currentStatusTarget)) {
        Add-Violation "$($File.FullName): 当前状态链接目标不存在"
    }
    $historyTarget = Join-Path $File.Directory.FullName '../'
    if (-not (Test-Path -LiteralPath $historyTarget)) {
        Add-Violation "$($File.FullName): 历史目录链接目标不存在"
    }
}

function Test-HistoricalDocuments {
    $specsPath = Join-Path $root $specsRelativePath
    $plansPath = Join-Path $root $plansRelativePath
    if (-not (Test-Path -LiteralPath $specsPath)) {
        Add-Violation "缺少历史设计目录: $specsRelativePath"
    } else {
        Get-ChildItem -LiteralPath $specsPath -Filter '*.md' -File | Sort-Object FullName | ForEach-Object {
            Test-HistoricalDocument $_ '设计记录'
        }
    }
    if (-not (Test-Path -LiteralPath $plansPath)) {
        Add-Violation "缺少历史计划目录: $plansRelativePath"
    } else {
        Get-ChildItem -LiteralPath $plansPath -Filter '*.md' -File | Sort-Object FullName | ForEach-Object {
            Test-HistoricalDocument $_ '实施计划'
        }
    }
}

function Test-CurrentEntries {
    $agentsPath = Join-Path $root 'AGENTS.md'
    if (-not (Test-Path -LiteralPath $agentsPath)) {
        Add-Violation '缺少 AGENTS.md'
    } else {
        $agentsText = Get-Content -LiteralPath $agentsPath -Raw -Encoding UTF8
        if ($agentsText -notmatch '<!-- current-status-source: PROJECT_STATUS\.md -->') {
            Add-Violation 'AGENTS.md 缺少当前状态来源标识'
        }
        $isolationPattern = '状态评估默认排除\s+`docs/superpowers/specs`\s+与\s+`docs/superpowers/plans`'
        if ($agentsText -notmatch $isolationPattern) {
            Add-Violation 'AGENTS.md 缺少历史目录检索隔离规则'
        }
        $dynamicPattern = '当前可运行实现|当前只有接入设计|仍是待实施目标|尚未形成.*实现'
        if ($agentsText -match $dynamicPattern) {
            Add-Violation "AGENTS.md 包含动态能力状态: $($Matches[0])"
        }
    }

    foreach ($entry in @('PROJECT_GUIDE.md', 'PROJECT_STATUS.md')) {
        if (-not (Test-Path -LiteralPath (Join-Path $root $entry))) {
            Add-Violation "缺少当前项目入口: $entry"
        }
    }

    $guidePath = Join-Path $root 'PROJECT_GUIDE.md'
    if (Test-Path -LiteralPath $guidePath) {
        $guideText = Get-Content -LiteralPath $guidePath -Raw -Encoding UTF8
        foreach ($match in [regex]::Matches($guideText, '\]\(([^)#]+)(?:#[^)]+)?\)')) {
            $link = $match.Groups[1].Value
            if ($link -match '^(https?:|mailto:)') {
                continue
            }
            $target = Join-Path (Split-Path -Parent $guidePath) $link
            if (-not (Test-Path -LiteralPath $target)) {
                Add-Violation "PROJECT_GUIDE.md 本地链接不存在: $link"
            }
        }
    }
}

function Test-CurrentMarker {
    $markerPath = Join-Path $root $markerRelativePath
    if (-not (Test-Path -LiteralPath $markerPath)) {
        Add-Violation "缺少 Guardrails 启用标识: $markerRelativePath"
        return
    }
    $actual = (Get-Content -LiteralPath $markerPath -Raw -Encoding UTF8) -replace "`r`n", "`n"
    if ($actual.Trim() -ne "enabled: true`nschema: 1") {
        Add-Violation "$markerRelativePath 内容必须严格为 enabled: true 和 schema: 1"
    }
}

function Test-BaseAndFrozenDiff {
    if ($Mode -eq 'WorkingTree') {
        return
    }

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & git -C $root cat-file -e "$BaseRef`:$markerRelativePath" 2>$null
        $baseHasMarker = $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $previousPreference
    }

    $paths = @($specsRelativePath, $plansRelativePath, $markerRelativePath)
    if ($Mode -eq 'PullRequest') {
        $changes = @(& git -C $root diff --name-status --find-renames "$BaseRef...HEAD" -- @paths)
    } else {
        $mergeBase = (& git -C $root merge-base HEAD $BaseRef 2>$null | Select-Object -First 1)
        if (-not $mergeBase) {
            Add-Violation "无法计算暂存检查基线: $BaseRef"
            return
        }
        $changes = @(& git -C $root diff --cached --name-status --find-renames $mergeBase -- @paths)
    }
    if ($LASTEXITCODE -ne 0) {
        Add-Violation "无法读取相对基线 $BaseRef 的 Git 差异"
        return
    }

    if (-not $baseHasMarker) {
        return
    }

    foreach ($line in $changes) {
        if (-not $line) {
            continue
        }
        $parts = @($line -split "`t")
        $status = $parts[0]
        $changedPaths = @($parts | Select-Object -Skip 1)
        if ($changedPaths -contains $markerRelativePath) {
            Add-Violation "Guardrails 启用后不得修改或删除 $markerRelativePath"
        }
        foreach ($changedPath in $changedPaths) {
            $isHistorical = $changedPath -like "$specsRelativePath/*" -or $changedPath -like "$plansRelativePath/*"
            if ($isHistorical -and $status -ne 'A') {
                Add-Violation "冻结历史文件不得修改、删除或重命名: $line"
            }
        }
    }
}

function Test-PullRequestSections {
    if ($Mode -ne 'PullRequest') {
        return
    }

    foreach ($heading in @('状态影响', '检查范围', '文档同步', '历史文档', '验证分级')) {
        $escapedHeading = [regex]::Escape($heading)
        $pattern = "(?ms)^##\s+$escapedHeading\s*`r?`n(.*?)(?=^##\s+|\z)"
        $match = [regex]::Match($PullRequestBody, $pattern)
        if (-not $match.Success) {
            Add-Violation "PR 正文缺少章节: ## $heading"
            continue
        }
        if ($match.Groups[1].Value -notmatch '(?im)^\s*-\s*\[[xX]\]') {
            Add-Violation "PR 正文章节未选择任何结论: ## $heading"
        }
    }
}

Test-HistoricalDocuments
Test-CurrentEntries
Test-CurrentMarker
Test-BaseAndFrozenDiff
Test-PullRequestSections

if ($violations.Count -gt 0) {
    Write-Output 'REPOSITORY_GUARDRAILS_FAILED'
    $violations | ForEach-Object { Write-Output " - $_" }
    exit 1
}

Write-Output 'REPOSITORY_GUARDRAILS_OK'
