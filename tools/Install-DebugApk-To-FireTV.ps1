param(
    [Parameter(Mandatory=$true)]
    [string]$IpAddress,

    [int]$Port = 5555,

    [string]$AdbPath = "adb",

    [string]$ApkPath = ".\app\build\outputs\apk\debug\app-debug.apk"
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $ApkPath)) {
    throw "APK not found at $ApkPath. Build the debug APK in Android Studio first."
}

Write-Host "Connecting to Fire TV at $IpAddress`:$Port ..."
& $AdbPath connect "$IpAddress`:$Port"

Write-Host "Installing $ApkPath ..."
& $AdbPath install -r $ApkPath

Write-Host ""
Write-Host "You can now open CX Fire Loader from the Fire TV Apps screen."
