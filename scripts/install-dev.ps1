[CmdletBinding()]
param(
    [string]$Serial,
    [string]$Apk,
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
$expectedPackage = 'com.iamskorpz.watchioiptv.debug'
$expectedCert = [Environment]::GetEnvironmentVariable('WATCHIO_DEV_CERT_SHA256', 'User')
if ([string]::IsNullOrWhiteSpace($expectedCert)) {
    $expectedCert = $env:WATCHIO_DEV_CERT_SHA256
}
if ([string]::IsNullOrWhiteSpace($expectedCert)) {
    throw 'Missing WATCHIO_DEV_CERT_SHA256'
}
$expectedCert = $expectedCert.Replace(':', '').ToLowerInvariant()

if ([string]::IsNullOrWhiteSpace($Apk)) {
    $Apk = Get-ChildItem "$PSScriptRoot\..\native-android\app\build\outputs\apk\debug\*.apk" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if ([string]::IsNullOrWhiteSpace($Apk) -or !(Test-Path -LiteralPath $Apk)) {
    throw 'DEV APK not found. Pass -Apk with an existing APK path.'
}
$Apk = (Resolve-Path -LiteralPath $Apk).Path

$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    $localProperties = Join-Path $PSScriptRoot '..\native-android\local.properties'
    if (Test-Path $localProperties) {
        $sdkLine = Get-Content $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) {
            $sdkRoot = ($sdkLine -replace '^sdk\.dir=', '').Replace('\:', ':').Replace('\\', '\')
        }
    }
}
if ([string]::IsNullOrWhiteSpace($sdkRoot)) { throw 'Android SDK path not found' }
$buildTools = Get-ChildItem (Join-Path $sdkRoot 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$aapt = Join-Path $buildTools.FullName 'aapt.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'

$badging = & $aapt dump badging $Apk
if ($LASTEXITCODE -ne 0) { throw 'aapt could not inspect APK' }
$packageMatch = [regex]::Match(($badging -join "`n"), "package: name='([^']+)'" )
if (!$packageMatch.Success -or $packageMatch.Groups[1].Value -ne $expectedPackage) {
    throw "APK package mismatch. Expected '$expectedPackage'."
}
$certOutput = & $apksigner verify --print-certs $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
$certMatch = [regex]::Match(($certOutput -join "`n"), 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)')
if (!$certMatch.Success -or $certMatch.Groups[1].Value.ToLowerInvariant() -ne $expectedCert) {
    throw 'APK signing certificate does not match WATCHIO_DEV_CERT_SHA256'
}

Write-Output "Validated package $expectedPackage and DEV certificate."
if ($ValidateOnly) { return }
if ([string]::IsNullOrWhiteSpace($Serial)) { throw 'Pass -Serial for installation.' }

$installOutput = & $adb -s $Serial install -r $Apk 2>&1
if ($LASTEXITCODE -eq 0) { $installOutput; return }
if (($installOutput -join "`n") -notmatch 'INSTALL_FAILED_VERSION_DOWNGRADE') {
    throw "adb install -r failed: $($installOutput -join ' ')"
}
$downgradeOutput = & $adb -s $Serial install -r -d $Apk 2>&1
if ($LASTEXITCODE -ne 0) { throw "adb install -r -d failed: $($downgradeOutput -join ' ')" }
$downgradeOutput
