$base = "http://localhost:8080"

function Test-Endpoint($label, $url) {
    Write-Host "`n=== $label ===" -ForegroundColor Cyan
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 30
        $d = ($r.Content | ConvertFrom-Json).data
        Write-Host "HTTP=$($r.StatusCode) | source=$($d.source) | official=$($d.official) | fallback=$($d.fallback)"
        if ($d.points) {
            $pts = $d.points
            Write-Host "points=$($pts.Count) | ilk=$($pts[0].date) | son=$($pts[-1].date)"
            # priceRef=MTL kontrolü — tryGram ve usdOns dolu mu?
            $sample = $pts | Select-Object -First 3
            $sample | ForEach-Object {
                Write-Host "  $($_.date) | tryGram=$($_.tryGram) | usdOns=$($_.usdOns) | eurOns=$($_.eurOns) | value=$($_.value)"
            }
            # Null/0 fiyat kontrolü
            $nullCount = ($pts | Where-Object { $_.value -eq $null -or $_.value -eq 0 }).Count
            Write-Host "  Null/0 value sayisi: $nullCount / $($pts.Count)"
            # tryGram = tryKg / 1000 kontrolü
            $check = $pts | Where-Object { $_.tryKg -ne $null -and $_.tryGram -ne $null } | Select-Object -First 1
            if ($check) {
                $expected = [math]::Round([double]$check.tryKg / 1000, 4)
                $actual   = [math]::Round([double]$check.tryGram, 4)
                $ok = if ([math]::Abs($expected - $actual) -lt 0.01) {"OK"} else {"FAIL"}
                Write-Host "  tryGram=tryKg/1000 kontrolü: beklenen=$expected gelen=$actual [$ok]"
            }
        } elseif ($d.usdOns -ne $null) {
            Write-Host "  usdOns=$($d.usdOns) | tryKg=$($d.tryKg) | tryGram=$($d.tryGram) | eurOns=$($d.eurOns)"
            Write-Host "  lastValidDate=$($d.lastValidDate)"
        }
    } catch {
        Write-Host "HATA: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Test-Endpoint "Platin SPOT"         "$base/api/precious-metals/platinum/spot"
Test-Endpoint "Platin 1M TRY"       "$base/api/precious-metals/platinum/history?range=1M&currency=TRY"
Test-Endpoint "Platin 1M USD"       "$base/api/precious-metals/platinum/history?range=1M&currency=USD"
Test-Endpoint "Platin 3M TRY"       "$base/api/precious-metals/platinum/history?range=3M&currency=TRY"
Test-Endpoint "Paladyum SPOT"       "$base/api/precious-metals/palladium/spot"
Test-Endpoint "Paladyum 1M TRY"     "$base/api/precious-metals/palladium/history?range=1M&currency=TRY"
Test-Endpoint "Paladyum 1M USD"     "$base/api/precious-metals/palladium/history?range=1M&currency=USD"
Test-Endpoint "Paladyum 3M TRY"     "$base/api/precious-metals/palladium/history?range=3M&currency=TRY"
