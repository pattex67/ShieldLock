# Installs ShieldLock on an NVIDIA Shield / Android TV device and turns the service on.
# On the device first: Developer options > Network debugging (or USB debugging) enabled.
#
#   powershell -ExecutionPolicy Bypass -File .\install.ps1 -Ip <tv-ip>
#
# The APK is picked up automatically when it sits next to this script; otherwise pass -Apk.
# If adb isn't installed, the script offers to download Google's platform-tools next to itself
# (nothing is installed system-wide; delete the platform-tools folder to remove it).
# -Yes skips that confirmation.
#
param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [int]$Port = 5555,
    [string]$Apk,
    [switch]$Yes
)
$ErrorActionPreference = 'Stop'
$pkg = 'app.shieldlock'
$svc = "$pkg/$pkg.LockService"
$dev = "${Ip}:$Port"
$platformToolsUrl = 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip'

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        "$PSScriptRoot\platform-tools\adb.exe",
        $(if ($env:ANDROID_HOME) { "$env:ANDROID_HOME\platform-tools\adb.exe" }),
        $(if ($env:ANDROID_SDK_ROOT) { "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe" }),
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    )
    foreach ($c in $candidates) { if ($c -and (Test-Path $c)) { return $c } }
    return $null
}

function Get-PlatformTools {
    Write-Host 'adb (Android platform-tools) was not found on this computer.' -ForegroundColor Yellow
    if (-not $Yes) {
        $answer = Read-Host "Download it from Google now (about 7 MB, into $PSScriptRoot\platform-tools)? [Y/n]"
        if ($answer -and $answer -notmatch '^(y|yes|o|oui)$') {
            throw 'adb is required. Install Android platform-tools, or run this script again and accept the download.'
        }
    }
    $zip = Join-Path ([IO.Path]::GetTempPath()) "platform-tools-$([guid]::NewGuid()).zip"
    try {
        Write-Host "Downloading $platformToolsUrl..."
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -UseBasicParsing -Uri $platformToolsUrl -OutFile $zip
        Expand-Archive -Path $zip -DestinationPath $PSScriptRoot -Force
    } finally {
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
    }
    $path = "$PSScriptRoot\platform-tools\adb.exe"
    if (-not (Test-Path $path)) { throw 'Download finished but adb.exe is missing.' }
    return $path
}

$adb = Find-Adb
if (-not $adb) { $adb = Get-PlatformTools }
Write-Host "Using adb: $adb"

if (-not $Apk) {
    # Next to the script (release download), or the repo's build output.
    $root = Split-Path $PSScriptRoot -Parent
    $Apk = Get-ChildItem "$PSScriptRoot\ShieldLock*.apk", "$root\build\ShieldLock*.apk" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime | Select-Object -Last 1 -ExpandProperty FullName
}
if (-not $Apk -or -not (Test-Path $Apk)) { throw 'APK not found: put ShieldLock.apk next to this script, or pass -Apk.' }

Write-Host "Connecting to $dev..."
& $adb connect $dev | Out-Host
if ((& $adb -s $dev get-state 2>$null) -ne 'device') {
    throw 'Device unauthorized or unreachable. Accept the debugging prompt on the TV, then run again.'
}

Write-Host "Installing $(Split-Path $Apk -Leaf)..."
& $adb -s $dev install -r $Apk | Out-Host
if ($LASTEXITCODE) { throw 'Install failed.' }

Write-Host 'Turning the accessibility service on...'
$current = "$(& $adb -s $dev shell settings get secure enabled_accessibility_services)".Trim()
if (-not $current -or $current -eq 'null') { $list = $svc }
elseif ($current.Split(':') -contains $svc) { $list = $current }
else { $list = "${current}:$svc" }
& $adb -s $dev shell settings put secure enabled_accessibility_services "$list"
& $adb -s $dev shell settings put secure accessibility_enabled 1
& $adb -s $dev shell appops set $pkg SYSTEM_ALERT_WINDOW allow

Start-Sleep -Seconds 2
if (& $adb -s $dev shell dumpsys accessibility | Select-String 'label=ShieldLock') {
    Write-Host 'Service active.' -ForegroundColor Green
} else {
    Write-Host 'Service not active yet: check Settings > Accessibility on the TV.' -ForegroundColor Yellow
}

Write-Host 'Opening the setup wizard on the TV...'
& $adb -s $dev shell am start -n "$pkg/.MainActivity" | Out-Null
Write-Host 'Done. Finish the wizard on the TV, then consider turning network debugging off.'
