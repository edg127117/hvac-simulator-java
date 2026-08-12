param(
    [string]$SimulatorBaseUrl = 'http://127.0.0.1:8080',
    [string]$PlatformBaseUrl = 'http://127.0.0.1:8081/api',
    [string]$PlatformToken = $env:IOT_PLATFORM_TOKEN,
    [string]$BuildingId = 'BLD001',
    [string]$CoolingTowerDeviceId = 'TOWER1',
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
    seed = 20240810
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
    $row = $items[$index]
    if ([double]$row.'ct_fan_power_kW' -le 0.0 -or [double]$row.'cw_flow_sensor' -le 0.0) {
        continue
    }
    $inlet = [double]$row.'T_cw_return_sensor'
    $outlet = [double]$row.'T_cw_supply_sensor'
    $wetBulb = [double]$row.'T_wb'
    if ($inlet -le $outlet -or $inlet -le $wetBulb) {
        throw "Gaia tower inputs are invalid at source step $index. inlet=$inlet outlet=$outlet wetBulb=$wetBulb"
    }
    $efficiency = ($inlet - $outlet) / ($inlet - $wetBulb) * 100.0
    if ($efficiency -lt 0.0 -or $efficiency -gt 100.0) {
        throw "Gaia tower efficiency is outside 0..100 at source step $index. value=$efficiency"
    }
    $activeSamples += [pscustomobject]@{
        sourceStep = $index
        expectedEfficiency = $efficiency
    }
}
if ($activeSamples.Count -lt $ActiveSteps) {
    throw "Only $($activeSamples.Count) active tower rows were found in the first 1,000 time steps; expected $ActiveSteps."
}

$indicatorId = 'INDICATOR_TOWER_EFF_B1'
$selectionDeadline = [DateTime]::UtcNow.AddSeconds(90)
$selectedActiveSamples = @()
$selectedDeliveryMinute = 0L
do {
    $candidateEpoch = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $candidateDeliveryMinute = $candidateEpoch - ($candidateEpoch % 60000L)
    $occupiedFrom = $candidateDeliveryMinute - (24L * 60L * 60000L)
    $occupiedTo = $candidateDeliveryMinute + 60000L
    $occupiedUri = "$PlatformBaseUrl/hvac/buildings/$BuildingId/indicators/trends?indicatorIds=$([Uri]::EscapeDataString($indicatorId))&from=$occupiedFrom&to=$occupiedTo"
    $occupiedTrend = Invoke-JsonRequest GET $occupiedUri $null $platformHeaders
    $occupiedMinutes = @{}
    foreach ($record in @($occupiedTrend.data.series[0].records)) {
        $occupiedMinutes[[long]$record.time] = $true
    }

    for ($start = 0; $start -le $activeSamples.Count - $ActiveSteps; $start++) {
        $candidateSamples = @($activeSamples[$start..($start + $ActiveSteps - 1)])
        $candidateFromStep = [int]$candidateSamples[0].sourceStep
        $candidateToStep = [int]$candidateSamples[-1].sourceStep
        if ($candidateToStep - $candidateFromStep + 1 -gt 1440) {
            continue
        }
        $hasConflict = $false
        foreach ($sample in $candidateSamples) {
            $targetMinute = $candidateDeliveryMinute - 60000L `
                - (($candidateToStep - [int]$sample.sourceStep) * 60000L)
            if ($occupiedMinutes.ContainsKey($targetMinute)) {
                $hasConflict = $true
                break
            }
        }
        if (-not $hasConflict) {
            $selectedActiveSamples = $candidateSamples
            $selectedDeliveryMinute = $candidateDeliveryMinute
            break
        }
    }
    $currentEpoch = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $currentMinute = $currentEpoch - ($currentEpoch % 60000L)
    if ($selectedActiveSamples.Count -eq $ActiveSteps `
            -and $currentMinute -eq $selectedDeliveryMinute) {
        break
    }
    $selectedActiveSamples = @()
    Start-Sleep -Milliseconds 500
} while ([DateTime]::UtcNow -lt $selectionDeadline)
if ($selectedActiveSamples.Count -ne $ActiveSteps) {
    throw 'No collision-free replay window became available within 90 seconds.'
}
$fromStep = [int]$selectedActiveSamples[0].sourceStep
$toStep = [int]$selectedActiveSamples[-1].sourceStep
$replaySteps = $toStep - $fromStep + 1
$delivery = Invoke-JsonRequest POST "$SimulatorBaseUrl/api/simulation-runs/$($run.runId)/mqtt-deliveries" @{
    fromStep = $fromStep
    toStep = $toStep
    timeMode = 'REBASE_TO_NOW'
    buildingId = $BuildingId
    coolingTowerDeviceId = $CoolingTowerDeviceId
    targets = @('TOWER_EFF')
}
$deliveryView = Wait-Until `
    { Invoke-JsonRequest GET "$SimulatorBaseUrl/api/simulation-runs/$($run.runId)/mqtt-deliveries/$($delivery.deliveryId)" $null } `
    { param($value) $value.status -in @('COMPLETED', 'PARTIAL_FAILED', 'FAILED') } `
    30 'MQTT delivery completion'
$expectedMessages = $ActiveSteps * 3
if ($deliveryView.status -ne 'COMPLETED' -or $deliveryView.successfulMessages -ne $expectedMessages) {
    throw "MQTT delivery was not complete: $($deliveryView | ConvertTo-Json -Compress)"
}

$createdAt = [DateTimeOffset]::Parse([string]$deliveryView.createdAt)
$deliveryEpoch = $createdAt.ToUnixTimeMilliseconds()
$deliveryMinute = $deliveryEpoch - ($deliveryEpoch % 60000L)
$expectedToMinute = $deliveryMinute - 60000L
$expectedByMinute = @{}
foreach ($sample in $selectedActiveSamples) {
    $minute = $expectedToMinute - (($toStep - [int]$sample.sourceStep) * 60000L)
    $expectedByMinute[$minute] = [double]$sample.expectedEfficiency
}
$expectedMinutes = @($expectedByMinute.Keys | ForEach-Object { [long]$_ } | Sort-Object)
$trendFrom = $expectedMinutes[0] - 60000L
$trendTo = $expectedMinutes[-1] + 60000L
$trendUri = "$PlatformBaseUrl/hvac/buildings/$BuildingId/indicators/trends?indicatorIds=$([Uri]::EscapeDataString($indicatorId))&from=$trendFrom&to=$trendTo"
$matchesExpected = {
    param($record)
    $minute = [long]$record.time
    if (-not $expectedByMinute.ContainsKey($minute)) {
        return $false
    }
    $expected = [double]$expectedByMinute[$minute]
    $tolerance = [Math]::Max(1.0E-8, [Math]::Abs($expected) * 1.0E-8)
    return [Math]::Abs([double]$record.average - $expected) -le $tolerance
}
$trend = Wait-Until `
    { Invoke-JsonRequest GET $trendUri $null $platformHeaders } `
    {
        param($value)
        $matched = @($value.data.series[0].records | Where-Object {
            & $matchesExpected $_
        })
        $matched.Count -ge $MinimumTrendPoints
    } `
    120 'central HVAC TOWER_EFF trend calculation'
$records = @($trend.data.series[0].records | Sort-Object time)

$recordsByTime = @{}
foreach ($record in $records) {
    $recordsByTime[[long]$record.time] = $record
}
$compared = 0
$conflicting = 0
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
        $conflicting++
        continue
    }
    $maxDifference = [Math]::Max($maxDifference, $difference)
    $compared++
}
if ($compared -lt $MinimumTrendPoints) {
    throw "Only $compared TOWER_EFF trend records matched the $ActiveSteps expected active Gaia time steps."
}
$latestVerifiedRecord = $records | Where-Object {
    & $matchesExpected $_
} | Sort-Object time | Select-Object -Last 1
$latestVerifiedMinute = [long]$latestVerifiedRecord.time
$latestVerifiedExpected = [double]$expectedByMinute[$latestVerifiedMinute]
$latestSnapshot = Invoke-JsonRequest GET "$PlatformBaseUrl/hvac/buildings/$BuildingId/indicators/latest" $null $platformHeaders
$currentTower = $latestSnapshot.data.indicators | Where-Object {
    $_.indicatorCode -eq 'TOWER_EFF'
} | Select-Object -First 1

[pscustomobject]@{
    result = 'CENTRAL_HVAC_TOWER_EFFICIENCY_VERIFIED'
    runId = $run.runId
    deliveryId = $delivery.deliveryId
    sourceFromStep = $fromStep
    sourceToStep = $toStep
    replayTimeSteps = $replaySteps
    expectedActivePoints = $ActiveSteps
    latestVerifiedMinute = $latestVerifiedMinute
    latestVerifiedExpectedEfficiency = $latestVerifiedExpected
    latestVerifiedPlatformEfficiency = [double]$latestVerifiedRecord.average
    maximumAbsoluteDifference = $maxDifference
    trendRecordCount = $records.Count
    comparedTrendPoints = $compared
    conflictingTrendPoints = $conflicting
    currentSnapshotStatus = if ($null -eq $currentTower) { 'NO_DATA' } else { [string]$currentTower.status }
    currentSnapshotMinuteStart = if ($null -eq $currentTower -or $null -eq $currentTower.minuteStart) { $null } else { [long]$currentTower.minuteStart }
    mqttMessages = $deliveryView.successfulMessages
}
