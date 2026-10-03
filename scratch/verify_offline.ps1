$c = (Invoke-WebRequest -Uri 'http://localhost:8080/api/map/nashik' -UseBasicParsing).Content
Write-Host "unpkg present: $($c.Contains('unpkg'))"
Write-Host "fonts.googleapis present: $($c.Contains('fonts.googleapis'))"
Write-Host "openstreetmap present: $($c.Contains('openstreetmap'))"
Write-Host "DEMO_MAP_ID present: $($c.Contains('DEMO_MAP_ID'))"
Write-Host "OFFLINE RADAR present: $($c.Contains('OFFLINE RADAR'))"
