param([Parameter(Mandatory = $true)][string]$PlanPath)
$ErrorActionPreference = 'Stop'
$plan = Get-Content -LiteralPath $PlanPath -Raw -Encoding UTF8 | ConvertFrom-Json
$restartPath = [string]$plan.executable
$success = $false
$mayRestart = $false
$detail = ''

try {
    [IO.File]::WriteAllText([string]$plan.readyPath, 'ready')
    $parent = Get-Process -Id ([int]$plan.parentPid) -ErrorAction SilentlyContinue
    if ($null -ne $parent) { Wait-Process -Id $parent.Id -Timeout 120 -ErrorAction Stop }
    $mayRestart = $true
    if (-not (Test-Path -LiteralPath $plan.installer -PathType Leaf)) { throw 'Kurulum dosyası bulunamadı.' }
    $hashAlgorithm = [Security.Cryptography.SHA256]::Create()
    $packageStream = [IO.File]::OpenRead([string]$plan.installer)
    try { $actualHash = [BitConverter]::ToString($hashAlgorithm.ComputeHash($packageStream)).Replace('-', '') }
    finally { $packageStream.Dispose(); $hashAlgorithm.Dispose() }
    if ($actualHash -ne $plan.sha256) { throw 'Kurulum dosyası doğrulaması başarısız.' }

    # jpackage's EXE wrapper forwards these arguments to Windows Installer.
    # Elevate only the installer; the helper and restarted app stay in the user's session.
    $arguments = '/qn /norestart /L*v "' + [string]$plan.logPath + '"'
    $setup = Start-Process -FilePath ([string]$plan.installer) -ArgumentList $arguments -Verb RunAs -Wait -PassThru
    if ($setup.ExitCode -notin @(0, 3010)) { throw ('Windows kurulum kodu: ' + $setup.ExitCode) }
    $success = $true
    $detail = if ($setup.ExitCode -eq 3010) { 'Güncellendi; Windows yeniden başlatma isteyebilir.' } else { 'Güncelleme tamamlandı.' }

    # A portable build may have installed to Program Files; discover the new installation.
    $keys = @('HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
              'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*',
              'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*')
    $installed = Get-ItemProperty -Path $keys -ErrorAction SilentlyContinue |
        Where-Object { $_.DisplayName -eq 'Metrolist' -and $_.DisplayVersion -eq $plan.version -and $_.InstallLocation } |
        Select-Object -First 1
    if ($null -ne $installed) {
        $candidate = Join-Path $installed.InstallLocation 'Metrolist.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { $restartPath = $candidate }
    } elseif ($restartPath -notlike ([Environment]::GetFolderPath('ProgramFiles') + '\*')) {
        $candidate = Join-Path ([Environment]::GetFolderPath('ProgramFiles')) 'Metrolist\Metrolist.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { $restartPath = $candidate }
    }
} catch {
    $detail = $_.Exception.Message
} finally {
    $result = @{ version = [string]$plan.version; success = $success; message = $detail } | ConvertTo-Json -Compress
    [IO.File]::WriteAllText([string]$plan.resultPath, $result, [Text.UTF8Encoding]::new($false))
    Remove-Item -LiteralPath $plan.readyPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $PlanPath -Force -ErrorAction SilentlyContinue
}

if ($mayRestart -and (Test-Path -LiteralPath $restartPath -PathType Leaf)) {
    Start-Process -FilePath $restartPath -WorkingDirectory (Split-Path -Parent $restartPath)
}
