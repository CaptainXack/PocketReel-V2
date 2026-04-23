param(
    [Parameter(Mandatory=$true)]
    [string]$IpAddress,

    [int]$Port = 5555,

    [string]$AdbPath = "adb"
)

$ErrorActionPreference = 'Stop'

Write-Host "Connecting to Fire TV at $IpAddress`:$Port ..."
& $AdbPath connect "$IpAddress`:$Port"
Write-Host ""
Write-Host "Connected devices:"
& $AdbPath devices
