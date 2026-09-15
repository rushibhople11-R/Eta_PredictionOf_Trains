$res = (Invoke-WebRequest -Uri 'http://localhost:8080/api/map/nashik' -UseBasicParsing).Content
Write-Host "Response Length: $($res.Length)"
Write-Host "Has Google Maps JS: $($res.Contains('maps.googleapis.com'))"
Write-Host "Has Injected API Key: $($res.Contains('AIzaSyDkdaDsZ8FaW8s4JULT6K6iTz9hr_2cPQY'))"
