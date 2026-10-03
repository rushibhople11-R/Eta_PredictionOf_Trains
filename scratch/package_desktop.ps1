$src = 'app\desktopApp\build\compose\binaries\main\app\RailAndaz'
Copy-Item -Path 'dist\nashik_rail_map.html' -Destination "$src\nashik_rail_map.html" -Force

# Update dist/RailNexus-Desktop-Portable
Copy-Item -Path "$src\*" -Destination 'dist\RailNexus-Desktop-Portable' -Recurse -Force

# Update user Downloads folder
$userDl = 'C:\Users\rushikesh\Downloads\RailAndaz'
if (-not (Test-Path $userDl)) { 
    New-Item -ItemType Directory -Path $userDl -Force | Out-Null 
}
Copy-Item -Path "$src\*" -Destination $userDl -Recurse -Force

# Create clean fresh zip in dist and Downloads
$zipDist = 'dist\RailAndaz-Desktop.zip'
$zipDl = 'C:\Users\rushikesh\Downloads\RailAndaz-Desktop.zip'
if (Test-Path $zipDist) { Remove-Item $zipDist -Force }
Compress-Archive -Path "$src\*" -DestinationPath $zipDist -Force
Copy-Item -Path $zipDist -Destination $zipDl -Force

Write-Host "SUCCESS: Desktop version packaged and placed in Downloads successfully!"
