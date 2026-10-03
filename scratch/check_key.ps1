$res = (Invoke-WebRequest -Uri 'http://localhost:8080/api/map/nashik' -UseBasicParsing).Content
Write-Host "Response length: $($res.Length)"
Write-Host "Contains Google Maps Script: $($res.Contains('maps.googleapis.com'))"
Write-Host "Contains User API Key: $($res.Contains('AIzaSyDkdaDsZ8FaW8s4JULT6K6iTz9hr_2cPQY'))"
Write-Host "Contains useGoogleMapsEngine in window.onload: $($res.Contains('useGoogleMapsEngine()'))"
Write-Host "Google Maps element default display: $($res.Contains('id=""gmap""'))"
