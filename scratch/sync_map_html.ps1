$html = (Invoke-WebRequest -Uri 'http://localhost:8080/api/map/nashik' -UseBasicParsing).Content
[System.IO.File]::WriteAllText('dist/nashik_rail_map.html', $html, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText('app/desktopApp/build/compose/binaries/main/app/RailAndaz/nashik_rail_map.html', $html, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText('C:\Users\rushikesh\Downloads\RailAndaz\nashik_rail_map.html', $html, [System.Text.Encoding]::UTF8)
Write-Host "Successfully synced Google Maps HTML to all files!"
