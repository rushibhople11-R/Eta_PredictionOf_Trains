$desktop = [Environment]::GetFolderPath('Desktop')
$wsh = New-Object -ComObject WScript.Shell
$sc = $wsh.CreateShortcut("$desktop\RailAndaz.lnk")
$sc.TargetPath = 'C:\Users\rushikesh\Downloads\RailAndaz\RailAndaz.exe'
$sc.WorkingDirectory = 'C:\Users\rushikesh\Downloads\RailAndaz'
$sc.IconLocation = 'C:\Projects\Eta_PredictionOf_Trains\app\desktopApp\src\main\resources\icon.ico,0'
$sc.Description = 'RailAndaz AI Train Dispatcher and Live Radar'
$sc.Save()

if (Test-Path "$desktop\RailAndaz.lnk") {
    Write-Host "Shortcut successfully created on Desktop: $desktop\RailAndaz.lnk"
}
