# Emulator / ADB recovery (run when Device Manager fails or device is offline)
$ErrorActionPreference = "Continue"
$Sdk = "$env:LOCALAPPDATA\Android\Sdk"
$Adb = Join-Path $Sdk "platform-tools\adb.exe"
$Emulator = Join-Path $Sdk "emulator\emulator.exe"
$AvdName = "Pixel_8"
$AvdDir = "$env:USERPROFILE\.android\avd\${AvdName}.avd"

Write-Host "Stopping emulator processes..."
Get-Process -Name "qemu-system*","emulator" -ErrorAction SilentlyContinue |
    Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

if (Test-Path $Adb) {
    Write-Host "Restarting ADB..."
    & $Adb kill-server
    Start-Sleep -Seconds 1
    & $Adb start-server
}

if (Test-Path $AvdDir) {
    Write-Host "Removing stale lock files under $AvdDir"
    Get-ChildItem $AvdDir -Filter "*.lock" -Recurse -ErrorAction SilentlyContinue |
        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
}

if (Test-Path $Emulator) {
    Write-Host "Starting AVD: $AvdName"
    Start-Process -FilePath $Emulator -ArgumentList @("-avd", $AvdName) -WindowStyle Normal
    Start-Sleep -Seconds 8
    & $Adb devices
} else {
    Write-Host "emulator.exe not found: $Emulator"
}

Write-Host "Done. Wait until adb shows 'device' (not offline), then Run in Android Studio."
