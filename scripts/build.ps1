# Builds ShieldLock without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner
#
#   .\scripts\build.ps1
#
# Requirements: Android SDK (build-tools + one platform) and a JDK 11+.
# Signing: uses signing.properties at the repo root if present (see README), otherwise
# the Android debug keystore.
$ErrorActionPreference = 'Stop'
$VersionName = '1.0.1'
$VersionCode = 2

$root = Split-Path $PSScriptRoot -Parent

# --- Locate the SDK, build-tools, platform and JDK ---------------------------------------
$sdk = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk") |
    Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $sdk) { throw 'Android SDK not found. Set ANDROID_HOME.' }

$bt = Get-ChildItem "$sdk\build-tools" -Directory | Sort-Object { [version]($_.Name -replace '-.*$', '') } |
    Select-Object -Last 1 -ExpandProperty FullName
$platform = Get-ChildItem "$sdk\platforms" -Directory | Where-Object { Test-Path "$($_.FullName)\android.jar" } |
    Sort-Object { [double](($_.Name -replace '^android-', '') -replace '[^\d.]', '') } |
    Select-Object -Last 1 -ExpandProperty FullName
if (-not $bt -or -not $platform) { throw 'Missing build-tools or platform in the Android SDK.' }
$jar = "$platform\android.jar"

if (-not $env:JAVA_HOME) {
    foreach ($jbr in "$env:ProgramFiles\Android\Android Studio\jbr", "$env:ProgramFiles\Android\Android Studio\jre") {
        if (Test-Path "$jbr\bin\java.exe") { $env:JAVA_HOME = $jbr; break }
    }
}
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'javac not found. Install a JDK or set JAVA_HOME.' }

Write-Host "SDK: $sdk"
Write-Host "Build-tools: $(Split-Path $bt -Leaf), platform: $(Split-Path $platform -Leaf)"

# --- Build --------------------------------------------------------------------------------
$out = Join-Path $root 'build'
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force "$out\gen", "$out\classes", "$out\dex" | Out-Null

& "$bt\aapt2.exe" compile --dir "$root\res" -o "$out\res.zip"
if ($LASTEXITCODE) { throw 'aapt2 compile failed' }
& "$bt\aapt2.exe" link -o "$out\unsigned.apk" -I $jar --manifest "$root\AndroidManifest.xml" `
    --java "$out\gen" --min-sdk-version 26 --target-sdk-version 30 `
    --version-code $VersionCode --version-name $VersionName "$out\res.zip"
if ($LASTEXITCODE) { throw 'aapt2 link failed' }

$sources = Get-ChildItem -Recurse "$root\src", "$out\gen" -Filter *.java | ForEach-Object FullName
& javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath $jar `
    -classpath "$bt\core-lambda-stubs.jar" -d "$out\classes" @sources
if ($LASTEXITCODE) { throw 'javac failed' }

$classes = Get-ChildItem -Recurse "$out\classes" -Filter *.class | ForEach-Object FullName
& "$bt\d8.bat" --release --min-api 26 --lib $jar --output "$out\dex" @classes
if ($LASTEXITCODE) { throw 'd8 failed' }

Push-Location "$out\dex"
& jar uf "$out\unsigned.apk" classes.dex
$jarExit = $LASTEXITCODE
Pop-Location
if ($jarExit) { throw 'jar failed' }

& "$bt\zipalign.exe" -f -p 4 "$out\unsigned.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw 'zipalign failed' }

# --- Sign -----------------------------------------------------------------------------------
$props = Join-Path $root 'signing.properties'
if (Test-Path $props) {
    $p = @{}
    Get-Content $props | Where-Object { $_ -match '^\s*([^#=]+?)\s*=\s*(.*)$' } | ForEach-Object { $p[$Matches[1]] = $Matches[2] }
    $ks = $p['storeFile']; $storePass = $p['storePassword']; $alias = $p['keyAlias']; $keyPass = $p['keyPassword']
    Write-Host "Signing with release key: $ks"
} else {
    $ks = "$env:USERPROFILE\.android\debug.keystore"; $storePass = 'android'; $alias = 'androiddebugkey'; $keyPass = 'android'
    if (-not (Test-Path $ks)) {
        New-Item -ItemType Directory -Force (Split-Path $ks) | Out-Null
        & keytool -genkeypair -keystore $ks -storepass android -keypass android -alias androiddebugkey `
            -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
    }
    Write-Host 'Signing with the debug key (no signing.properties found).'
}

$apk = Join-Path $out "ShieldLock-$VersionName.apk"
& "$bt\apksigner.bat" sign --ks $ks --ks-key-alias $alias --ks-pass "pass:$storePass" --key-pass "pass:$keyPass" `
    --out $apk "$out\aligned.apk"
if ($LASTEXITCODE) { throw 'apksigner failed' }

# Unversioned copy: the release asset behind the stable .../releases/latest/download/ShieldLock.apk link.
Copy-Item $apk (Join-Path $out 'ShieldLock.apk') -Force

Write-Host "OK -> $apk (+ build\ShieldLock.apk)"
