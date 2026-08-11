$ErrorActionPreference = 'Stop'

$webRoot = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Split-Path -Parent $webRoot
$serverJar = Join-Path $repositoryRoot 'server\target\hvac-simulator-server-1.1.0-SNAPSHOT.jar'
$viteCli = Join-Path $webRoot 'node_modules\vite\bin\vite.js'
$playwright = Join-Path $webRoot 'node_modules\.bin\playwright.CMD'
$serverProcess = $null
$webProcess = $null
$testExitCode = 1

function Wait-HttpReady([string]$Uri, [int]$TimeoutSeconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                return
            }
        } catch {
            Start-Sleep -Milliseconds 300
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Service did not become ready within ${TimeoutSeconds} seconds: $Uri"
}

function New-BackgroundProcess([string]$FileName, [string[]]$Arguments, [string]$WorkingDirectory) {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FileName
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.Arguments = ($Arguments -join ' ')
    return [System.Diagnostics.Process]::Start($startInfo)
}

try {
    if (-not (Test-Path -LiteralPath $serverJar)) {
        throw "Missing server executable JAR. Run .\mvnw.cmd package first: $serverJar"
    }
    if (-not (Test-Path -LiteralPath $viteCli)) {
        throw "Missing web dependencies. Run pnpm install in the web directory first."
    }

    $java = (Get-Command java).Source
    $node = (Get-Command node).Source
    $serverProcess = New-BackgroundProcess $java @('-jar', $serverJar, '--server.port=18081') $repositoryRoot

    $previousProxyTarget = [Environment]::GetEnvironmentVariable('VITE_API_PROXY_TARGET', 'Process')
    [Environment]::SetEnvironmentVariable('VITE_API_PROXY_TARGET', 'http://127.0.0.1:18081', 'Process')
    $webProcess = New-BackgroundProcess $node @($viteCli, '--host', '127.0.0.1', '--port', '5174') $webRoot

    Wait-HttpReady 'http://127.0.0.1:18081/api/model-releases' 60
    Wait-HttpReady 'http://127.0.0.1:5174' 30

    Push-Location $webRoot
    try {
        & $playwright test --reporter=line --workers=1
        $testExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    [Environment]::SetEnvironmentVariable('VITE_API_PROXY_TARGET', $previousProxyTarget, 'Process')
    foreach ($process in @($webProcess, $serverProcess)) {
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    }
}

exit $testExitCode
