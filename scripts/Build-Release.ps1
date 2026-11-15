param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$appSource = Get-Content -LiteralPath (Join-Path $projectRoot 'desktop/src/main/kotlin/com/metrolist/desktop/AppVersion.kt') -Raw
$buildSource = Get-Content -LiteralPath (Join-Path $projectRoot 'desktop/build.gradle.kts') -Raw
$appVersion = [regex]::Match($appSource, 'const val NAME = "([0-9]+\.[0-9]+\.[0-9]+)"').Groups[1].Value
$packageVersion = [regex]::Match($buildSource, 'packageVersion = "([0-9]+\.[0-9]+\.[0-9]+)"').Groups[1].Value
if (-not $appVersion -or $appVersion -ne $packageVersion) { throw 'AppVersion.NAME ve packageVersion aynı sürüm olmalı.' }
$gradleArguments = @('-p', $projectRoot, ':desktop:test', ':desktop:packageExe', '--console=plain')
if ($Offline) { $gradleArguments += '--offline' }
& (Join-Path $projectRoot 'gradlew.bat') @gradleArguments
if ($LASTEXITCODE -ne 0) { throw 'Test veya paketleme başarısız.' }
$package = Join-Path $projectRoot "desktop/build/compose/binaries/main/exe/Metrolist-$appVersion.exe"
if (-not (Test-Path -LiteralPath $package -PathType Leaf)) { throw 'Kurulum paketi oluşturulamadı.' }
$algorithm = [Security.Cryptography.SHA256]::Create()
$stream = [IO.File]::OpenRead($package)
try { $digest = [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
finally { $stream.Dispose(); $algorithm.Dispose() }
[IO.File]::WriteAllText(($package + '.sha256'), ($digest + '  ' + [IO.Path]::GetFileName($package) + "`n"), [Text.Encoding]::ASCII)
Write-Output "Release v$appVersion için hazır: $package"
Write-Output 'GitHub Release yayınına EXE dosyasını yükle. Bu betik yayın yapmaz.'
