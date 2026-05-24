/**
 * trendUtils.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Tüm varlık tipleri (hisse, kripto, emtia, fon, döviz, tahvil) için ortak
 * trend hesaplama mantığı.
 *
 * Öncelik sırası:
 *   1. Backend'den explicit `trend` alanı
 *   2. FUND: 3A ±%2 → 1A ±%2 → NAV MA20/MA50
 *   3. MA20 + MA50 çift hareketli ortalama kesişimi
 *   4. Sadece MA20 varsa tek-MA ±%0.5 tolerans
 *   5. 52 haftalık fiyat aralığındaki konum (STOCK / FUTURE / genel emtia)
 *   6. Kripto 7 günlük değişim (24h'den çok daha güvenilir)
 *   7. Günlük değişim % fallback — tüm tipler için ±2% eşiği
 *   8. Yeterli veri yok → null
 *
 * ALTIN, BIST gümüş (SILVER:*) ve DİBS (BOND) için yalnızca MA20/MA50 kullanılır;
 * 52 hafta konumu veya günlük % fallback uygulanmaz.
 */

export const TREND_SIGNAL = {
  UP:       'UP',
  DOWN:     'DOWN',
  SIDEWAYS: 'SIDEWAYS',
};

/** Hesaplama yöntemine göre tooltip açıklamaları */
export const TREND_METHOD_LABEL = {
  'explicit':      "Backend'den gelen teknik trend verisi",
  'ma-crossover':  'MA20/MA50 çift hareketli ortalama kesişimi',
  'ma20':          'MA20 hareketli ortalama (±%0.5 tolerans)',
  '52w-position':  '52 haftalık fiyat aralığındaki konum (>%65 Yükseliş, <%35 Düşüş)',
  'fund-return-3m': 'Fon 3 ay getirisi (±%2 eşiği)',
  'fund-return-1m': 'Fon 1 ay getirisi (±%2 eşiği)',
  'fund-nav-ma':   'Fon NAV üzerinden MA20 / MA50',
  'crypto-7d':     '7 günlük kripto fiyat değişimi (±%2 eşiği)',
  'daily-change':  'Günlük fiyat değişimi (±%2 eşiği — zayıf sinyal)',
  'multi-signal':  'Çoklu sinyal: MA (ağırlık 2) + 52 hafta konumu + momentum birlikte oylanır',
  'no-data':       'Trend hesaplamak için yeterli veri yok',
};

// ─── Yardımcılar ─────────────────────────────────────────────────────────────

function f(v) {
  if (v == null) return null;
  const n = parseFloat(v);
  return isNaN(n) ? null : n;
}

function normalizeExplicit(raw) {
  const s = String(raw).trim().toUpperCase()
    .normalize('NFD').replace(/[\u0300-\u036f]/g, ''); // diakritik temizle
  if (s === 'UP'   || s === 'YUKSELIS' || s === 'YUKSELIŞ') return TREND_SIGNAL.UP;
  if (s === 'DOWN' || s === 'DUSUS'    || s === 'DUSUS'   ) return TREND_SIGNAL.DOWN;
  if (s === 'SIDEWAYS' || s === 'YATAY')                     return TREND_SIGNAL.SIDEWAYS;
  return null;
}

/** Altın, BIST gümüş ve DİBS: trend yalnızca MA ile (52w / günlük % yedek yok). */
function isMaOnlyTrendAsset(item) {
  const t = String(item?.assetType ?? '').toUpperCase();
  if (t === 'GOLD') return true;
  if (t === 'BOND') return true;
  if (t === 'COMMODITY') {
    const sym = String(item?.symbol ?? '').toUpperCase();
    return sym.startsWith('SILVER:');
  }
  return false;
}

function trendFromMovingAverages(item) {
  const price = f(
    item.currentPrice ?? item.price ?? item.displayPrice ??
    item.lastPrice    ?? item.indicatorValue
  );
  const ma20 = f(item.ma20 ?? item.movingAverage20);
  const ma50 = f(item.ma50 ?? item.movingAverage50);

  if (price != null && ma20 != null && ma50 != null) {
    if (price > ma20 && ma20 > ma50) return { signal: TREND_SIGNAL.UP,       method: 'ma-crossover' };
    if (price < ma20 && ma20 < ma50) return { signal: TREND_SIGNAL.DOWN,     method: 'ma-crossover' };
    return                                  { signal: TREND_SIGNAL.SIDEWAYS, method: 'ma-crossover' };
  }
  if (price != null && ma20 != null) {
    if (price > ma20 * 1.005) return { signal: TREND_SIGNAL.UP,       method: 'ma20' };
    if (price < ma20 * 0.995) return { signal: TREND_SIGNAL.DOWN,     method: 'ma20' };
    return                           { signal: TREND_SIGNAL.SIDEWAYS, method: 'ma20' };
  }
  return { signal: null, method: 'no-data' };
}

// ─── Ana fonksiyon ───────────────────────────────────────────────────────────

/**
 * Herhangi bir varlık nesnesini alır, mevcut alanlardan trend sinyali üretir.
 *
 * @param {object|null} item - Herhangi bir piyasa/varlık nesnesi
 * @returns {{ signal: 'UP'|'DOWN'|'SIDEWAYS'|null, method: string }}
 */
/**
 * Bir kapanış serisinden ({@code closes[]}) trend rozeti için item üretir:
 * son fiyat + serinin kendi MA20/MA50'si (MA kesişimi = güvenilir trend). 52h "konumu"
 * GİRİLMEZ — yüklenen aralık değişken olduğu için (1A/5Y...) sahte sinyal üretirdi; trend
 * sadece MA + (varsa) momentum ile hesaplanır. {@link computeTrend} ile kullanılır.
 *
 * @param {number[]} closes - kapanış (veya gösterge/NAV) serisi
 * @param {string} assetType - STOCK | CRYPTO | FX | GOLD | FUND | COMMODITY | BOND | FUTURE
 * @param {object} [extra] - computeTrend'e geçecek ek alanlar (ör. returnThreeMonths)
 */
export function buildTrendItem(closes, assetType, extra = {}) {
  const arr = (closes ?? []).map(Number).filter(v => Number.isFinite(v) && v > 0);
  if (arr.length < 2) return null;
  const last = arr[arr.length - 1];
  const sma = (p) => (arr.length >= p ? arr.slice(-p).reduce((a, b) => a + b, 0) / p : null);
  return {
    assetType,
    currentPrice: last,
    ma20: sma(20),
    ma50: sma(50),
    ...extra,
  };
}

export function computeTrend(item) {
  if (!item) return { signal: null, method: 'no-data' };

  // ── 1. Explicit backend trend ────────────────────────────────────────────
  if (item.trend != null) {
    const sig = normalizeExplicit(item.trend);
    if (sig) return { signal: sig, method: 'explicit' };
  }

  // ── FUND: önce 3A ±%2, yoksa 1A, yoksa NAV MA20/MA50 (holding / watchlist) ─
  const assetType = String(item.assetType ?? '').toUpperCase();
  if (assetType === 'FUND') {
    const r3m = f(item.returnThreeMonths ?? item.fundReturnThreeMonths);
    if (r3m != null) {
      if (r3m > 2) return { signal: TREND_SIGNAL.UP, method: 'fund-return-3m' };
      if (r3m < -2) return { signal: TREND_SIGNAL.DOWN, method: 'fund-return-3m' };
      return { signal: TREND_SIGNAL.SIDEWAYS, method: 'fund-return-3m' };
    }
    const r1m = f(item.returnOneMonth ?? item.fundReturnOneMonth);
    if (r1m != null) {
      if (r1m > 2) return { signal: TREND_SIGNAL.UP, method: 'fund-return-1m' };
      if (r1m < -2) return { signal: TREND_SIGNAL.DOWN, method: 'fund-return-1m' };
      return { signal: TREND_SIGNAL.SIDEWAYS, method: 'fund-return-1m' };
    }
    const fundPrice = f(
      item.currentPrice ?? item.price ?? item.displayPrice ??
      item.lastPrice ?? item.indicatorValue
    );
    const fMa20 = f(item.ma20 ?? item.movingAverage20);
    const fMa50 = f(item.ma50 ?? item.movingAverage50);
    if (fundPrice != null && fMa20 != null && fMa50 != null) {
      if (fundPrice > fMa20 && fMa20 > fMa50) return { signal: TREND_SIGNAL.UP, method: 'fund-nav-ma' };
      if (fundPrice < fMa20 && fMa20 < fMa50) return { signal: TREND_SIGNAL.DOWN, method: 'fund-nav-ma' };
      return { signal: TREND_SIGNAL.SIDEWAYS, method: 'fund-nav-ma' };
    }
    return { signal: null, method: 'no-data' };
  }

  // ── ALTIN / BIST gümüş / DİBS: yalnızca MA ───────────────────────────────
  if (isMaOnlyTrendAsset(item)) {
    return trendFromMovingAverages(item);
  }

  // ── Genel (hisse, vadeli, kripto, döviz, Yahoo emtia): çok-sinyalli oy ─────
  // Tek bir eşiğe takılıp ilk eşleşeni döndürmek yerine, mevcut TÜM sinyalleri
  // ağırlıklı toplar → daha kararlı ve tutarlı. MA en güçlü sinyal (ağırlık 2);
  // 52 hafta konumu ve momentum (kripto 7g veya günlük %) destekleyici (ağırlık 1).
  const price = f(
    item.currentPrice ?? item.price ?? item.displayPrice ??
    item.lastPrice    ?? item.indicatorValue
  );
  const ma20 = f(item.ma20 ?? item.movingAverage20);
  const ma50 = f(item.ma50 ?? item.movingAverage50);
  const hi52 = f(item.fiftyTwoWeekHigh ?? item.weekHigh52 ?? item.week52High);
  const lo52 = f(item.fiftyTwoWeekLow  ?? item.weekLow52  ?? item.week52Low);
  const pct7d = f(
    item.priceChangePercentage7d ?? item.changePercent7d ??
    item.sevenDayChangePercent   ?? item.periodReturn7d  ?? item.return7d
  );
  const changePct = f(
    item.changePercent ?? item.dailyChangePercent ?? item.priceChangePercentage24h
  );

  let score = 0;
  let signals = 0;

  // 1) Hareketli ortalama — en güçlü sinyal (ağırlık 2)
  if (price != null && ma20 != null && ma50 != null) {
    if (price > ma20 && ma20 > ma50) score += 2;
    else if (price < ma20 && ma20 < ma50) score -= 2;
    signals += 1;
  } else if (price != null && ma20 != null) {
    if (price > ma20 * 1.005) score += 1;
    else if (price < ma20 * 0.995) score -= 1;
    signals += 1;
  }

  // 2) 52 hafta fiyat aralığındaki konum (ağırlık 1)
  if (price != null && hi52 != null && lo52 != null && hi52 > lo52) {
    const pos = ((price - lo52) / (hi52 - lo52)) * 100;
    if (pos >= 65) score += 1;
    else if (pos <= 35) score -= 1;
    signals += 1;
  }

  // 3) Momentum — kripto 7 günlük (±%2) ya da günlük % (zayıf, ±%3 daha yüksek eşik)
  if (pct7d != null) {
    if (pct7d > 2) score += 1;
    else if (pct7d < -2) score -= 1;
    signals += 1;
  } else if (changePct != null) {
    if (changePct > 3) score += 1;
    else if (changePct < -3) score -= 1;
    signals += 1;
  }

  if (signals === 0) return { signal: null, method: 'no-data' };
  if (score > 0) return { signal: TREND_SIGNAL.UP,       method: 'multi-signal' };
  if (score < 0) return { signal: TREND_SIGNAL.DOWN,     method: 'multi-signal' };
  return                { signal: TREND_SIGNAL.SIDEWAYS, method: 'multi-signal' };
}
