# SMRTA.APK - SMRTASYS PORTAL Automated Build Script (Windows PowerShell)
$ErrorActionPreference = "Stop"

Write-Host "=== SMRTA.APK | SMRTASYS PORTAL (Windows Build) ===" -ForegroundColor Cyan

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $env:JAVA_HOME -and $javaCmd) {
    $env:JAVA_HOME = Split-Path (Split-Path $javaCmd.Source -Parent) -Parent
}
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    Write-Error "Java JDK not found. Please install JDK 17 and set JAVA_HOME."
}
Write-Host "[OK] Java JDK detected: $env:JAVA_HOME" -ForegroundColor Green
& "$env:JAVA_HOME\bin\java.exe" -version

if (-not $env:ANDROID_HOME -and $env:ANDROID_SDK_ROOT) {
    $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
}
if (-not $env:ANDROID_HOME) {
    $defaultSdk = "$env:LOCALAPPDATA\Android\Sdk"
    if (Test-Path $defaultSdk) {
        $env:ANDROID_HOME = $defaultSdk
    }
}
if (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) {
    Write-Error "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT."
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
Write-Host "[OK] Android SDK detected: $env:ANDROID_HOME" -ForegroundColor Green

$sdkEscaped = ($env:ANDROID_HOME -replace '\\', '/')
"sdk.dir=$sdkEscaped" | Out-File -FilePath "local.properties" -Encoding ASCII -Force
Write-Host "[OK] local.properties configured." -ForegroundColor Green

Write-Host "[..] Running assembleDebug..." -ForegroundColor Yellow
if (Test-Path ".\gradlew.bat") {
    & .\gradlew.bat assembleDebug --stacktrace
} elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
    & gradle assembleDebug --stacktrace
} else {
    Write-Error "Neither gradlew.bat nor system gradle was found."
}

$apkPath = Join-Path (Get-Location) "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    Write-Error "Build finished without producing APK at $apkPath"
}

Write-Host "=== BUILD SUCCEEDED ===" -ForegroundColor Green
Write-Host "Debug APK Location: $apkPath" -ForegroundColor Cyan
