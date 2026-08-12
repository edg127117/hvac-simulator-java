[CmdletBinding()]
param(
    [switch]$Check,
    [string]$RepositoryRoot = ''
)

$ErrorActionPreference = 'Stop'
$RepositoryRoot = if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    Split-Path -Parent $PSScriptRoot
} else {
    $RepositoryRoot
}
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path

if ($Check) {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $configuredPath = (& git -C $root config --local --get core.hooksPath 2>$null | Select-Object -First 1)
    } finally {
        $ErrorActionPreference = $previousPreference
    }

    $configuredPath = if ($null -eq $configuredPath) { '' } else { $configuredPath.ToString().Trim() }
    if ($configuredPath -ne '.githooks') {
        Write-Output 'REPOSITORY_HOOKS_FAILED: core.hooksPath 必须为 .githooks'
        exit 1
    }

    Write-Output 'REPOSITORY_HOOKS_OK'
    exit 0
}

& git -C $root config --local core.hooksPath .githooks
if ($LASTEXITCODE -ne 0) {
    Write-Output 'REPOSITORY_HOOKS_FAILED: 无法设置 core.hooksPath'
    exit 1
}

Write-Output 'REPOSITORY_HOOKS_OK'
