Write-Host "=== BIST ONS ENDPOINT DIREKT TEST ===" -ForegroundColor Cyan
$bistOns = Invoke-WebRequest -Uri "https://www.borsaistanbul.com/veri-sorgulama.php?op=fetchVeriSorgulama&bastar=2026-05-05&bittar=2026-05-06&pazart=SA&f_tipit=`$/O" -UseBasicParsing -TimeoutSec 15 -Headers @{"User-Agent"="Mozilla/5.0";"Referer"="https://www.borsaistanbul.com/"}
$bistOnsJson = $bistOns.Content | ConvertFrom-Json
Write-Host "BIST ONS HTTP: $($bistOns.StatusCode) | kayit: $($bistOnsJson.data.Count)"
$bistOnsJson.data | Select-Object -First 2 | ForEach-Object {
    Write-Host "  $($_.guntar) | close=$($_.kpo) | high=$($_.max_f) | low=$($_.min_f) | wa=$($_.sum_o)"
}

Write-Host ""
Write-Host "=== /api/gold/spot ===" -ForegroundColor Cyan
$spot = (Invoke-WebRequest -Uri "http://localhost:8080/api/gold/spot" -UseBasicParsing -TimeoutSec 30).Content | ConvertFrom-Json
$d = $spot.data
Write-Host "source=$($d.source) | official=$($d.official) | fallback=$($d.fallback)"
Write-Host "bistDate=$($d.bistDate)"
Write-Host "officialPureGoldGramTry=$($d.officialPureGoldGramTry)"
Write-Host "onsUsd=$($d.onsUsd) | onsHigh=$($d.onsHigh) | onsLow=$($d.onsLow)"
Write-Host "gramGoldTry=$($d.gramGoldTry) | quarterGoldTry=$($d.quarterGoldTry)"

Write-Host ""
Write-Host "=== /api/gold/history?range=1M&currency=USD (BIST ONS) ===" -ForegroundColor Cyan
$histUsd = (Invoke-WebRequest -Uri "http://localhost:8080/api/gold/history?range=1M&currency=USD" -UseBasicParsing -TimeoutSec 30).Content | ConvertFrom-Json
$hu = $histUsd.data
Write-Host "source=$($hu.source) | official=$($hu.official) | fallback=$($hu.fallback) | points=$($hu.points.Count)"
Write-Host "disclaimer=$($hu.disclaimer)"
$hu.points | Select-Object -First 3 | ForEach-Object {
    Write-Host "  $($_.date) | close=$($_.close) | open=$($_.open) | high=$($_.high) | low=$($_.low)"
}

Write-Host ""
Write-Host "=== /api/gold/history?range=1M&currency=TRY (BIST GRAM) ===" -ForegroundColor Cyan
$histTry = (Invoke-WebRequest -Uri "http://localhost:8080/api/gold/history?range=1M&currency=TRY" -UseBasicParsing -TimeoutSec 30).Content | ConvertFrom-Json
$ht = $histTry.data
Write-Host "source=$($ht.source) | official=$($ht.official) | fallback=$($ht.fallback) | points=$($ht.points.Count)"
$ht.points | Select-Object -Last 2 | ForEach-Object {
    Write-Host "  $($_.date) | close=$($_.close) | wa=$($_.weightedAverage)"
}

Write-Host ""
Write-Host "=== SENTETIK OPEN KONTROLU ===" -ForegroundColor Yellow
$pts = $histUsd.data.points
for ($i = 1; $i -lt [Math]::Min(4, $pts.Count); $i++) {
    $prevClose = $pts[$i-1].close
    $thisOpen  = $pts[$i].open
    $ok = if ([math]::Abs([double]$prevClose - [double]$thisOpen) -lt 0.01) {"OK"} else {"FAIL"}
    Write-Host "  [$i] prevClose=$prevClose thisOpen=$thisOpen [$ok]"
}
