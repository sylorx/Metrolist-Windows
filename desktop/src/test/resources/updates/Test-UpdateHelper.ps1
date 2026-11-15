param([string]$HelperPath, [string]$WorkDirectory)
$ErrorActionPreference = 'Stop'
# All process-launch and registry operations are replaced in this test scope.
# No installer or application is executed by these scenarios.
function Run-Scenario([string]$name, [int]$setupExitCode, [bool]$badHash = $false) {
    $folder = Join-Path $WorkDirectory $name
    New-Item -ItemType Directory -Path $folder | Out-Null
    $setupFile = Join-Path $folder 'setup.exe'
    $appFile = Join-Path $folder 'Metrolist.exe'
    [IO.File]::WriteAllText($setupFile, 'MZ-test-installer')
    [IO.File]::WriteAllText($appFile, 'MZ-test-app')
    $planFile = Join-Path $folder 'plan.json'
    $resultFile = Join-Path $folder 'result.json'
    $hashAlgorithm = [Security.Cryptography.SHA256]::Create()
    $digest = [BitConverter]::ToString($hashAlgorithm.ComputeHash([IO.File]::ReadAllBytes($setupFile))).Replace('-', '')
    $hashAlgorithm.Dispose()
    if ($badHash) { $digest = '0' * 64 }
    $plan = @{ parentPid = 999999; installer = $setupFile; executable = $appFile; version = '1.2.0'; sha256 = $digest;
        resultPath = $resultFile; readyPath = (Join-Path $folder 'ready'); logPath = (Join-Path $folder 'install.log') }
    $plan | ConvertTo-Json | Set-Content -LiteralPath $planFile -Encoding UTF8
    $script:launches = @()
    function Get-Process { param($Id, $ErrorAction) return $null }
    function Get-ItemProperty { param($Path, $ErrorAction) return $null }
    function Start-Process {
        param($FilePath, $ArgumentList, $Verb, [switch]$Wait, [switch]$PassThru, $WorkingDirectory)
        $script:launches += @{ Path = $FilePath; Verb = $Verb; Arguments = $ArgumentList }
        if ($FilePath -eq $setupFile) { return [pscustomobject]@{ ExitCode = $setupExitCode } }
    }
    . $HelperPath -PlanPath $planFile
    $result = Get-Content -LiteralPath $resultFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($result.success -ne ($setupExitCode -eq 0 -and -not $badHash)) { throw "$name success result is wrong" }
    $expected = if ($badHash) { 1 } else { 2 }
    if ($script:launches.Count -ne $expected) { throw "$name process count is wrong" }
    if (-not $badHash) {
        if ($script:launches[0].Verb -ne 'RunAs') { throw 'Only installer must be elevated' }
        if ($script:launches[0].Arguments -notlike '*/qn /norestart*') { throw 'Quiet update arguments missing' }
    }
    if ($script:launches[-1].Verb) { throw 'Restarted app must not be elevated' }
    if (Test-Path -LiteralPath $planFile) { throw 'Plan was not cleaned up' }
    Write-Output "$name passed"
}
Run-Scenario 'success' 0
Run-Scenario 'installer-failure' 1603
Run-Scenario 'checksum-failure' 0 $true
