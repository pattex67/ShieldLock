# Installs ShieldLock on an NVIDIA Shield / Android TV device and turns the service on.
# On the device first: Developer options > Network debugging (or USB debugging) enabled.
#
#   .\scripts\install.ps1 -Ip 192.168.1.42
#   .\scripts\install.ps1 -Ip 192.168.1.42 -Apk .\ShieldLock-1.0.0.apk
#
param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [int]$Port = 5555,
    [string]$Apk
)
$ErrorActionPreference = 'Stop'
$pkg = 'app.shieldlock'
$svc = "$pkg/$pkg.LockService"
$dev = "${Ip}:$Port"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw 'adb not found: install Android platform-tools.' }
if (-not $Apk) {
    $root = Split-Path $PSScriptRoot -Parent
    $Apk = Get-ChildItem "$root\build\ShieldLock-*.apk", "$root\ShieldLock-*.apk" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime | Select-Object -Last 1 -ExpandProperty FullName
}
if (-not $Apk -or -not (Test-Path $Apk)) { throw 'APK not found: pass -Apk or run scripts\build.ps1 first.' }

Write-Host "Connecting to $dev..."
adb connect $dev | Out-Host
if ((adb -s $dev get-state 2>$null) -ne 'device') {
    throw 'Device unauthorized or unreachable. Accept the debugging prompt on the TV, then run again.'
}

Write-Host "Installing $(Split-Path $Apk -Leaf)..."
adb -s $dev install -r $Apk | Out-Host
if ($LASTEXITCODE) { throw 'Install failed.' }

Write-Host 'Turning the accessibility service on...'
$current = "$(adb -s $dev shell settings get secure enabled_accessibility_services)".Trim()
if (-not $current -or $current -eq 'null') { $list = $svc }
elseif ($current.Split(':') -contains $svc) { $list = $current }
else { $list = "${current}:$svc" }
adb -s $dev shell settings put secure enabled_accessibility_services "$list"
adb -s $dev shell settings put secure accessibility_enabled 1
adb -s $dev shell appops set $pkg SYSTEM_ALERT_WINDOW allow

Start-Sleep -Seconds 2
if (adb -s $dev shell dumpsys accessibility | Select-String 'label=ShieldLock') {
    Write-Host 'Service active.' -ForegroundColor Green
} else {
    Write-Host 'Service not active yet: check Settings > Accessibility on the TV.' -ForegroundColor Yellow
}

Write-Host 'Opening the setup wizard on the TV...'
adb -s $dev shell am start -n "$pkg/.MainActivity" | Out-Null
Write-Host 'Done. Finish the wizard on the TV, then consider turning network debugging off.'
