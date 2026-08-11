param(
    [string]$SimulatorBaseUrl = 'http://127.0.0.1:8080',
    [string]$PlatformBaseUrl = 'http://127.0.0.1:8081/api',
    [string]$PlatformToken = $env:IOT_PLATFORM_TOKEN,
    [string]$BuildingId = 'BLD001',
    [string]$DeviceId = 'WCR1',
    [string]$BrokerHost = '127.0.0.1',
    [int]$BrokerPort = 1883,
    [ValidateRange(3, 60)]
    [int]$ActiveSteps = 10,
    [ValidateRange(3, 60)]
    [int]$MinimumTrendPoints = 3
)

$ErrorActionPreference = 'Stop'
$SimulatorBaseUrl = $SimulatorBaseUrl.TrimEnd('/')
$PlatformBaseUrl = $PlatformBaseUrl.TrimEnd('/')
if ($MinimumTrendPoints -gt $ActiveSteps) {
    throw 'MinimumTrendPoints cannot exceed ActiveSteps.'
}

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
$items = @($rows.items)
$activeSamples = @()
for ($index = 0; $index -lt $items.Count; $index++) {
    $copValue = [double]$items[$index].'measured_COP'
    if ($copValue -gt 0.0) {
        $activeSamples += [pscustomobject]@{
            sourceStep = $index
            measuredCop = $copValue
        }
    }
}
if ($activeSamples.Count -lt $ActiveSteps) {
    throw "Only $($activeSamples.Count) active Gaia 1.1 rows were found in the first 1,000 time steps; expected $ActiveSteps."
}
$selectedActiveSamples = @($activeSamples | Select-Object -First $ActiveSteps)
$fromStep = [int]$selectedActiveSamples[0].sourceStep
$toStep = [int]$selectedActiveSamples[-1].sourceStep
$replaySteps = $toStep - $fromStep + 1
$expectedLatestCop = [double]$selectedActiveSamples[-1].measuredCop

$latestBeforeDelivery = Invoke-JsonRequest GET "$PlatformBaseUrl/hvac/buildings/$BuildingId/indicators/latest" $null $platformHeaders
$previousCop = $latestBeforeDelivery.data.indicators | Where-Object {
    $_.indicatorCode -eq 'WCR_COP'
} | Select-Object -First 1

$delivery = Invoke-JsonRequest POST "$SimulatorBaseUrl/api/simulation-runs/$($run.runId)/mqtt-deliveries" @{
    fromStep = $fromStep
    toStep = $toStep
    timeMode = 'REBASE_TO_NOW'
    buildingId = $BuildingId
    deviceId = $DeviceId
}
$deliveryView = Wait-Until `
    { Invoke-JsonRequest GET "$SimulatorBaseUrl/api/simulation-runs/$($run.runId)/mqtt-deliveries/$($delivery.deliveryId)" $null } `
    { param($value) $value.status -in @('COMPLETED', 'PARTIAL_FAILED', 'FAILED') } `
    30 'MQTT delivery completion'
$expectedMessages = $replaySteps * 4
if ($deliveryView.status -ne 'COMPLETED' -or $deliveryView.successfulMessages -ne $expectedMessages) {
    throw "MQTT delivery was not complete: $($deliveryView | ConvertTo-Json -Compress)"
}

$createdAt = [DateTimeOffset]::Parse([string]$deliveryView.createdAt)
$latestMinuteCutoff = $createdAt.AddMinutes(-2).ToUnixTimeMilliseconds()
$latest = Wait-Until `
    { Invoke-JsonRequest GET "$PlatformBaseUrl/hvac/buildings/$BuildingId/indicators/latest" $null $platformHeaders } `
    {
        param($value)
        $indicator = $value.data.indicators | Where-Object {
            $_.indicatorCode -eq 'WCR_COP' -and $_.status -eq 'SUCCESS' -and $null -ne $_.minuteStart
        } | Select-Object -First 1
        if ($null -eq $indicator -or $indicator.minuteStart -lt $latestMinuteCutoff) {
            return $false
        }
        $tolerance = [Math]::Max(1.0E-8, [Math]::Abs($expectedLatestCop) * 1.0E-8)
        return [Math]::Abs([double]$indicator.value - $expectedLatestCop) -le $tolerance
    } `
    120 'central HVAC WCR_COP calculation'

$cop = $latest.data.indicators | Where-Object { $_.indicatorCode -eq 'WCR_COP' } | Select-Object -First 1
$trendFrom = [long]$cop.minuteStart - (($replaySteps - 1L) * 60000L)
$trendTo = [long]$cop.minuteStart + 60000L
$trendUri = "$PlatformBaseUrl/hvac/buildings/$BuildingId/indicators/trends?indicatorIds=$([Uri]::EscapeDataString($cop.indicatorId))&from=$trendFrom&to=$trendTo"
$expectedByMinute = @{}
foreach ($sample in $selectedActiveSamples) {
    $minute = [long]$cop.minuteStart - (($toStep - [int]$sample.sourceStep) * 60000L)
    $expectedByMinute[$minute] = [double]$sample.measuredCop
}
$trend = Wait-Until `
    { Invoke-JsonRequest GET $trendUri $null $platformHeaders } `
    {
        param($value)
        $matched = @($value.data.series[0].records | Where-Object {
            $expectedByMinute.ContainsKey([long]$_.time)
        })
        $matched.Count -ge $MinimumTrendPoints
    } `
    120 'central HVAC WCR_COP trend records'
$records = @($trend.data.series[0].records | Sort-Object time)

$recordsByTime = @{}
foreach ($record in $records) {
    $recordsByTime[[long]$record.time] = $record
}
$compared = 0
$maxDifference = 0.0
foreach ($entry in $expectedByMinute.GetEnumerator()) {
    $minute = [long]$entry.Key
    if (-not $recordsByTime.ContainsKey($minute)) {
        continue
    }
    $expected = [double]$entry.Value
    $actual = [double]$recordsByTime[$minute].average
    $difference = [Math]::Abs($actual - $expected)
    $tolerance = [Math]::Max(1.0E-8, [Math]::Abs($expected) * 1.0E-8)
    if ($difference -gt $tolerance) {
        throw "WCR_COP mismatch at minute $minute. expected=$expected actual=$actual difference=$difference tolerance=$tolerance"
    }
    $maxDifference = [Math]::Max($maxDifference, $difference)
    $compared++
}
if ($compared -lt $MinimumTrendPoints) {
    throw "Only $compared WCR_COP trend records matched the $ActiveSteps expected active Gaia time steps."
}

[pscustomobject]@{
    result = 'CENTRAL_HVAC_COP_VERIFIED'
    runId = $run.runId
    deliveryId = $delivery.deliveryId
    sourceFromStep = $fromStep
    sourceToStep = $toStep
    replayTimeSteps = $replaySteps
    expectedActivePoints = $ActiveSteps
    expectedLatestMeasuredCop = $expectedLatestCop
    platformWcrCop = [double]$cop.value
    maximumAbsoluteDifference = $maxDifference
    platformMinuteStart = [long]$cop.minuteStart
    trendRecordCount = $records.Count
    comparedTrendPoints = $compared
    previousPlatformMinuteStart = if ($null -eq $previousCop) { $null } else { [long]$previousCop.minuteStart }
    mqttMessages = $deliveryView.successfulMessages
}
