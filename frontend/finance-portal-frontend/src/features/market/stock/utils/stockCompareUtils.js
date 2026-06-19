// StockComparePage'den çıkarılan SAF yardımcılar + sabitler.
// React/state/JSX yok — yalnız veri dönüşümü ve metrik hesabı. Davranış StockComparePage'deki
// orijinaliyle birebir aynıdır (taşıma; mantık değişmedi).

export const ASSET_LABELS = {
  STOCK: 'Hisse', CRYPTO: 'Kripto', FX: 'Döviz', FUND: 'Fon',
  FUTURE: 'Vadeli', GOLD: 'Altın', COMMODITY: 'Emtia', BOND: 'Tahvil',
  INDICATOR: 'Gösterge',
};

// Faiz/enflasyon benchmark'ları — fiyat değil, kümülatif % endeks (INDICATOR türü).
export const BENCHMARK_SHORTCUTS = [
  { key: 'INDICATOR|TUFE', assetType: 'INDICATOR', symbol: 'TUFE', label: 'Enflasyon (TÜFE)' },
  { key: 'INDICATOR|USCPI_TRY', assetType: 'INDICATOR', symbol: 'USCPI_TRY', label: 'ABD Enflasyonu' },
  { key: 'INDICATOR|DEPOSIT', assetType: 'INDICATOR', symbol: 'DEPOSIT', label: 'Mevduat Faizi' },
];

// Endeks kısayolları
export const INDEX_SHORTCUTS = [
  { symbol: 'XU100.IS', label: 'BIST 100' },
  { symbol: 'XU030.IS', label: 'BIST 30' },
];

export const COLORS = ['#093eaa', '#f97316', '#8b5cf6', '#10b981'];
export const MAX_STOCKS = 4;

/**
 * Adil kıyas için tüm serileri ORTAK başlangıç tarihinden %0'a sabitler.
 * Farklı geçmişe sahip varlıklarda (ör. yeni halka arz hisse vs 10y enflasyon) her seri kendi
 * ilk tarihinden başlarsa kıyas yanıltıcı olur. Ortak başlangıç = serilerin ilk-veri tarihlerinin
 * EN GEÇ olanı; herkes o tarihteki değerine göre yeniden normalize edilir, öncesi kırpılır.
 */
export function rebaseToCommonStart(rows, keys) {
  if (!rows.length || !keys.length) return rows;
  const firstIdx = {};
  keys.forEach(k => {
    for (let i = 0; i < rows.length; i++) {
      if (rows[i][`__price_${k}`] != null) { firstIdx[k] = i; break; }
    }
  });
  const present = keys.filter(k => firstIdx[k] != null);
  if (!present.length) return rows;
  const startIdx = Math.max(...present.map(k => firstIdx[k]));
  if (startIdx <= 0) return rows; // hepsi zaten aynı tarihten başlıyor
  const baseline = {};
  present.forEach(k => {
    // Baseline = serinin ortak başlangıç tarihindeki değeri. Önce o tarihte/ÖNCESİNDE son bilinen
    // değer (floor) — aylık seri (TÜFE/mevduat) gün-bazlı seriyle hizalanıp ortak başlangıç ay ortasına
    // denk gelince baseline'ı bir SONRAKİ aya kaydırıp enflasyonu eksik göstermesin. Yoksa sonraki ilk değer.
    let b = null;
    for (let i = startIdx; i >= 0; i--) {
      const p = rows[i][`__price_${k}`];
      if (p != null) { b = p; break; }
    }
    if (b == null) {
      for (let i = startIdx + 1; i < rows.length; i++) {
        const p = rows[i][`__price_${k}`];
        if (p != null) { b = p; break; }
      }
    }
    baseline[k] = b;
  });
  return rows.slice(startIdx).map((row, idx) => {
    const r = { ...row };
    present.forEach(k => {
      const p = r[`__price_${k}`];
      if (p != null && baseline[k]) {
        r[k] = parseFloat(((p - baseline[k]) / baseline[k] * 100).toFixed(3));
      } else if (idx === 0 && baseline[k] != null) {
        // İlk satır = ortak başlangıç. Aylık seri (TÜFE) burada veri taşımıyorsa %0 baseline noktası
        // koy ki çizgi %0'dan başlasın — ilk ayı bir gün önceye denk gelip kırpılmasın/+%X'ten başlamasın.
        r[k] = 0;
        r[`__price_${k}`] = baseline[k];
      } else {
        r[k] = null;
      }
    });
    return r;
  });
}

/**
 * Seyrek (aylık) serileri — enflasyon/mevduat — bilinen noktaları arasında DOĞRUSAL bağlar
 * (basamak/ileri-doldur yerine). Böylece ilk günden itibaren sürekli yükselirler; "ilk ay %0'da
 * takılıp günlük hareket eden BIST'e bir aylık avantaj verme" adaletsizliği biter. Son bilinen
 * noktadan SONRASI ileri-doldurulur (henüz yayımlanmamış ayları/geleceği bilemeyiz — TÜFE gecikmesi).
 * İlk bilinen noktadan ÖNCESİ null kalır (ortak başlangıçta zaten %0 seed'lendi).
 */
export function interpolateSeries(rows, keys) {
  keys.forEach(k => {
    const known = [];
    for (let i = 0; i < rows.length; i++) if (rows[i][k] != null) known.push(i);
    if (known.length === 0) return;
    for (let s = 0; s < known.length - 1; s++) {
      const a = known[s], b = known[s + 1];
      const pa = rows[a][k], pb = rows[b][k];
      const prA = rows[a][`__price_${k}`], prB = rows[b][`__price_${k}`];
      for (let i = a + 1; i < b; i++) {
        const t = (i - a) / (b - a);
        rows[i][k] = parseFloat((pa + (pb - pa) * t).toFixed(3));
        if (prA != null && prB != null) rows[i][`__price_${k}`] = prA + (prB - prA) * t;
      }
    }
    const last = known[known.length - 1];
    for (let i = last + 1; i < rows.length; i++) {
      rows[i][k] = rows[last][k];
      rows[i][`__price_${k}`] = rows[last][`__price_${k}`];
    }
  });
  return rows;
}

/**
 * Seyrek (aylık) serileri — enflasyon/mevduat — BASAMAK (step) olarak doldurur: bilinen iki nokta
 * arasını DOĞRUSAL bağlamak yerine, ÖNCEKİ ayın değerinde SABİT tutar; değer ancak BİR SONRAKİ
 * gerçek noktada (ay sonunda) zıplar. Mantık: bir ayın enflasyonu ancak o ay BİTTİĞİNDE bellidir
 * (TÜİK ay sonu açıklar) → ay içinde geleceği "bilmiş gibi" tırmandırmak yerine düz tutulur.
 * Son bilinen noktadan SONRASI ileri-doldurulur (yayımlanmamış ay/gelecek bilinemez).
 * İlk bilinen noktadan ÖNCESİ null kalır (ortak başlangıçta zaten %0 seed'lendi).
 *
 * <p>{@link #interpolateSeries} (doğrusal) yerine kullanılır; grafik bu seriyi {@code step:'end'}
 * ile çizer → veri de görsel de "ay sonunda zıplayan" gerçek basamak olur (tooltip ara günde
 * önceki ayın değerini gösterir, tutarlı).</p>
 */
export function stepFillSeries(rows, keys) {
  keys.forEach(k => {
    const known = [];
    for (let i = 0; i < rows.length; i++) if (rows[i][k] != null) known.push(i);
    if (known.length === 0) return;
    // Her bilinen nokta, BİR SONRAKİ bilinen noktaya kadar değerini KORUR (basamak — düz tut).
    for (let s = 0; s < known.length - 1; s++) {
      const a = known[s], b = known[s + 1];
      for (let i = a + 1; i < b; i++) {
        rows[i][k] = rows[a][k];
        rows[i][`__price_${k}`] = rows[a][`__price_${k}`];
      }
    }
    // Son bilinen noktadan sonrası → ileri-doldur (gelecek/yayımlanmamış ay bilinemez).
    const last = known[known.length - 1];
    for (let i = last + 1; i < rows.length; i++) {
      rows[i][k] = rows[last][k];
      rows[i][`__price_${k}`] = rows[last][`__price_${k}`];
    }
  });
  return rows;
}

/** Yahoo aralığını genel price-history endpoint aralığına çevir. */
export function toGenericRange(yahooRange) {
  switch (yahooRange) {
    case '1d': case '5d': case '1mo': return '1M';
    case '3mo': return '3M';
    case '6mo': return '6M';
    case '1y': return '1Y';
    case '5y': return '5Y';
    case 'max': return 'ALL';
    default: return '1Y';
  }
}

// ── Performans Metrikleri Hesaplama ──────────────────────────────────────────
export function calcMetrics(prices) {
  if (!prices || prices.length < 2) return null;
  const nums = prices.map(p => parseFloat(p)).filter(p => !isNaN(p) && p > 0);
  if (nums.length < 2) return null;

  const startPrice = nums[0];
  const endPrice   = nums[nums.length - 1];
  const maxPrice   = Math.max(...nums);
  const minPrice   = Math.min(...nums);
  const periodReturn = ((endPrice - startPrice) / startPrice) * 100;

  // Max Drawdown
  let peak = nums[0], maxDrawdown = 0;
  for (const p of nums) {
    if (p > peak) peak = p;
    const dd = (p - peak) / peak;
    if (dd < maxDrawdown) maxDrawdown = dd;
  }

  // Volatilite (günlük getiri std sapması)
  const returns = [];
  for (let i = 1; i < nums.length; i++) {
    if (nums[i - 1] > 0) returns.push((nums[i] - nums[i - 1]) / nums[i - 1] * 100);
  }
  const mean = returns.reduce((a, b) => a + b, 0) / (returns.length || 1);
  const variance = returns.reduce((a, b) => a + (b - mean) ** 2, 0) / (returns.length || 1);
  const volatility = Math.sqrt(variance);

  // RSI (14)
  let gains = 0, losses = 0;
  const period = Math.min(14, returns.length);
  for (let i = 0; i < period; i++) {
    if (returns[i] >= 0) gains += returns[i]; else losses -= returns[i];
  }
  let avgGain = gains / period, avgLoss = losses / period;
  for (let i = period; i < returns.length; i++) {
    avgGain = (avgGain * (period - 1) + Math.max(returns[i], 0)) / period;
    avgLoss = (avgLoss * (period - 1) + Math.max(-returns[i], 0)) / period;
  }
  const rsi = avgLoss === 0 ? 100 : 100 - (100 / (1 + avgGain / avgLoss));

  // MA hesaplama
  function ma(n) {
    if (nums.length < n) return null;
    return nums.slice(-n).reduce((a, b) => a + b, 0) / n;
  }
  const ma7  = ma(7);
  const ma25 = ma(25);
  const ma99 = ma(99);

  // Trend: çok sinyalli analiz
  const currentPrice = endPrice;
  const maGapPercent = (ma7 && ma25) ? ((ma7 - ma25) / ma25) * 100 : null;

  let trend;
  if (
    periodReturn > 5 &&
    ma7 && ma25 && ma7 > ma25 &&
    currentPrice > (ma7 ?? 0)
  ) {
    trend = 'Güçlü Yükselen';
  } else if (
    periodReturn < -5 &&
    ma7 && ma25 && ma7 < ma25 &&
    currentPrice < (ma7 ?? Infinity)
  ) {
    trend = 'Güçlü Düşen';
  } else if (
    periodReturn > 2 ||
    (ma7 && ma25 && ma7 > ma25 && currentPrice > (ma7 ?? 0))
  ) {
    trend = 'Yükselen';
  } else if (
    periodReturn < -2 ||
    (ma7 && ma25 && ma7 < ma25 && currentPrice < (ma7 ?? Infinity))
  ) {
    trend = 'Düşen';
  } else if (
    periodReturn >= -2 && periodReturn <= 2 &&
    maGapPercent != null && Math.abs(maGapPercent) < 1
  ) {
    trend = 'Yatay';
  } else {
    trend = 'Yatay';
  }

  // RSI uyarısı (trend kararına dahil değil, ayrı gösterilir)
  const rsiWarning = rsi >= 70 ? 'Aşırı Alım ⚠️' : rsi <= 30 ? 'Aşırı Satım ⚠️' : null;

  // Risk başına getiri (Sharpe benzeri)
  const riskAdjusted = volatility > 0 ? periodReturn / volatility : null;

  return { startPrice, endPrice, maxPrice, minPrice, periodReturn, drawdown: maxDrawdown * 100, volatility, rsi, rsiWarning, riskAdjusted, ma7, ma25, ma99, trend, lastPrice: endPrice };
}
