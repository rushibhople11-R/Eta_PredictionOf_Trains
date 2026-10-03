$sourceZip = "C:\Projects\Eta_PredictionOf_Trains\dist\RailAndaz-Desktop.zip"
$destFolder = "C:\Users\rushikesh\Downloads\RailAndaz"
$exePath = "C:\Projects\Eta_PredictionOf_Trains\app\desktopApp\build\compose\binaries\main\app\RailAndaz\RailAndaz.exe"
$workDir = "C:\Projects\Eta_PredictionOf_Trains\app\desktopApp\build\compose\binaries\main\app\RailAndaz"
$iconPath = "C:\Projects\Eta_PredictionOf_Trains\app\desktopApp\src\main\resources\icon.ico"

# Extract to Downloads/RailAndaz
if (-not (Test-Path $destFolder)) {
    New-Item -ItemType Directory -Path $destFolder -Force | Out-Null
}
Expand-Archive -Path $sourceZip -DestinationPath $destFolder -Force

# Create Desktop Shortcut
$wsh = New-Object -ComObject WScript.Shell
$sc = $wsh.CreateShortcut("C:\Users\rushikesh\Desktop\RAILअंदाज.lnk")
$sc.TargetPath = $exePath
$sc.WorkingDirectory = $workDir
$sc.IconLocation = "$iconPath,0"
$sc.Description = "RAILअंदाज • AI Train Dispatcher & Live Radar"
$sc.Save()

Write-Host "SUCCESS: Extracted to $destFolder and created Desktop shortcut!"
