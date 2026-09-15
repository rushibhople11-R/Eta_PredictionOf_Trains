$res = (Invoke-WebRequest -Uri 'http://localhost:8080/api/map/nashik' -UseBasicParsing).Content
Write-Host "Contains 'Google Maps API Configuration':" ($res.Contains("Google Maps API Configuration"))
Write-Host "Contains 'api-key-input':" ($res.Contains("api-key-input"))
Write-Host "Contains 'btn-demo-link':" ($res.Contains("btn-demo-link"))
Write-Host "Has Google Maps JS:" ($res.Contains("maps.googleapis.com"))
Write-Host "Has Injected Backend Key:" ($res.Contains("AIzaSyDkdaDsZ8FaW8s4JULT6K6iTz9hr_2cPQY"))
