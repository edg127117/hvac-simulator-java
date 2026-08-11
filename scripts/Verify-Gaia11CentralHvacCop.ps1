param(
    [string]$SimulatorBaseUrl = 'http://127.0.0.1:8080',
    [string]$PlatformBaseUrl = 'http://127.0.0.1:8081/api',
    [string]$PlatformToken = $env:IOT_PLATFORM_TOKEN,
    [string]$BuildingId = 'BLD001',
    [string]$DeviceId = 'WCR1',
    [string]$BrokerHost = '127.0.0.1',
    [int]$BrokerPort = 1883
)

$ErrorActionPreference = 'Stop'
$SimulatorBaseUrl = $SimulatorBaseUrl.TrimEnd('/')
$PlatformBaseUrl = $PlatformBaseUrl.TrimEnd('/')

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Uri,
        [object]$Body,
        [hashtable]$Headers = @{}
    )
    $arguments = @{
        Method = $Method
        Uri = $Uri
        Headers = $Headers
        ContentType = 'application/json'
    }
    if ($null -ne $Body) {
        $arguments.Body = $Body | ConvertTo-Json -Depth 8
    }
    return Invoke-RestMethod @arguments
}

function Wait-Until {
    param(
        [scriptblock]$Action,
        [scriptblock]$Complete,
        [int]$TimeoutSeconds,
        [string]$Description
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $value = & $Action
        if (& $Complete $value) {
            return $value
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Description."
}

if ([string]::IsNullOrWhiteSpace($PlatformToken)) {
    throw 'Set IOT_PLATFORM_TOKEN or pass -PlatformToken with a BLD001-authorized JWT.'
}

$tcpClient = [System.Net.Sockets.TcpClient]::new()
try {
    $connect = $tcpClient.BeginConnect($BrokerHost, $BrokerPort, $null, $null)
    if (-not $connect.AsyncWaitHandle.WaitOne(3000) -or -not $tcpClient.Connected) {
        throw "MQTT broker is unavailable at ${BrokerHost}:$BrokerPort."
    }
    $tcpClient.EndConnect($connect)
} finally {
    $tcpClient.Dispose()
}

$platformHeaders = @{ Authorization = "Bearer $PlatformToken" }
$run = Invoke-JsonRequest POST "$SimulatorBaseUrl/api/simulation-runs" @{
    modelVersion = 'gaia-1.1'
    mode = 'BASELINE'
    seed = 20240806
    overrides = @{}
}
$runView = Wait-Until `
    { Invoke-JsonRequest GET "$SimulatorBaseUrl/api/simulation-runs/$($run.runId)" $null } `
    { param($value) $value.status -in @('COMPLETED', 'FAILED') } `
    120 'Gaia 1.1 simulation completion'
if ($runView.status -ne 'COMPLETED') {
    throw "Simulation failed: $($runView.errorCode) $($runView.errorMessage)"
}

$rows = Invoke-JsonRequest GET "$SimulatorBaseUrl/api/simulation-runs/$($run.runId)/rows?offset=0&limit=1000" $null
$activeRow = $rows.items | Where-Object { [double]$_.'measured_COP' -gt 0.0 } | Select-Object -First 1
if ($null -eq $activeRow) {
    throw 'No active Gaia 1.1 row was found in the first 1,000 time steps.'
}
$stepIndex = [array]::IndexOf([array]$rows.items, $activeRow)
$expectedCop = [double]$activeRow.'measured_COP'

$delivery = Invoke-JsonRequest POST "$SimulatorBaseUrl/api/simulation-runs/$($run.runId)/mqtt-deliveries" @{
    fromStep = $stepIndex
    toStep = $stepIndex
    timeMode = 'REBASE_TO_NOW'
    buildingId = $BuildingId
    deviceId = $DeviceId
}
$deliveryView = Wait-Until `
    { Invoke-JsonRequest GET "$SimulatorBaseUrl/api/simulation-runs/$($run.runId)/mqtt-deliveries/$($delivery.deliveryId)" $null } `
    { param($value) $value.status -in @('COMPLETED', 'PARTIAL_FAILED', 'FAILED') } `
    30 'MQTT delivery completion'
if ($deliveryView.status -ne 'COMPLETED' -or $deliveryView.successfulMessages -ne 4) {
    throw "MQTT delivery was not complete: $($deliveryView | ConvertTo-Json -Compress)"
}

$latest = Wait-Until `
    { Invoke-JsonRequest GET "$PlatformBaseUrl/hvac/buildings/$BuildingId/indicators/latest" $null $platformHeaders } `
    {
        param($value)
        $indicator = $value.data.indicators | Where-Object {
            $_.indicatorCode -eq 'WCR_COP' -and $_.status -eq 'SUCCESS' -and $null -ne $_.minuteStart
        } | Select-Object -First 1
        $null -ne $indicator -and $indicator.minuteStart -ge ([DateTimeOffset]::UtcNow.AddMinutes(-5).ToUnixTimeMilliseconds())
    } `
    90 'central HVAC WCR_COP calculation'

$cop = $latest.data.indicators | Where-Object { $_.indicatorCode -eq 'WCR_COP' } | Select-Object -First 1
$difference = [Math]::Abs([double]$cop.value - $expectedCop)
if ($difference -gt 0.02) {
    throw "WCR_COP mismatch. expected=$expectedCop actual=$($cop.value) difference=$difference"
}

$trendFrom = [long]$cop.minuteStart
$trendTo = $trendFrom + 120000
$trendUri = "$PlatformBaseUrl/hvac/buildings/$BuildingId/indicators/trends?indicatorIds=$([Uri]::EscapeDataString($cop.indicatorId))&from=$trendFrom&to=$trendTo"
$trend = Invoke-JsonRequest GET $trendUri $null $platformHeaders
$records = @($trend.data.series[0].records)
if ($records.Count -lt 1) {
    throw 'WCR_COP latest value exists, but the central platform trend contains no matching record.'
}

[pscustomobject]@{
    result = 'CENTRAL_HVAC_COP_VERIFIED'
    runId = $run.runId
    deliveryId = $delivery.deliveryId
    sourceStep = $stepIndex
    expectedMeasuredCop = $expectedCop
    platformWcrCop = [double]$cop.value
    absoluteDifference = $difference
    platformMinuteStart = [long]$cop.minuteStart
    trendRecordCount = $records.Count
    mqttMessages = $deliveryView.successfulMessages
}
