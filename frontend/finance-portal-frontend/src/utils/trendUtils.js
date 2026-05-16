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

  // Anlık fiyat — birden fazla olası alan adı
  const price = f(
    item.currentPrice ?? item.price ?? item.displayPrice ??
    item.lastPrice    ?? item.indicatorValue
  );

  // ── 2. MA20 + MA50 çift crossover ────────────────────────────────────────
  const ma20 = f(item.ma20 ?? item.movingAverage20);
  const ma50 = f(item.ma50 ?? item.movingAverage50);

  if (price != null && ma20 != null && ma50 != null) {
    if (price > ma20 && ma20 > ma50) return { signal: TREND_SIGNAL.UP,       method: 'ma-crossover' };
    if (price < ma20 && ma20 < ma50) return { signal: TREND_SIGNAL.DOWN,     method: 'ma-crossover' };
    return                                  { signal: TREND_SIGNAL.SIDEWAYS, method: 'ma-crossover' };
  }

  // ── 3. Sadece MA20 ──────────────────────────────────────────────────────
  if (price != null && ma20 != null) {
    if (price > ma20 * 1.005) return { signal: TREND_SIGNAL.UP,       method: 'ma20' };
    if (price < ma20 * 0.995) return { signal: TREND_SIGNAL.DOWN,     method: 'ma20' };
    return                           { signal: TREND_SIGNAL.SIDEWAYS, method: 'ma20' };
  }

  // ── 4. 52 hafta pozisyon yüzdesi (STOCK / FUTURE / Yahoo emtia) ─────────
  const hi52 = f(item.fiftyTwoWeekHigh ?? item.weekHigh52 ?? item.week52High);
  const lo52 = f(item.fiftyTwoWeekLow  ?? item.weekLow52  ?? item.week52Low);

  if (price != null && hi52 != null && lo52 != null && hi52 > lo52) {
    const pos = (price - lo52) / (hi52 - lo52) * 100;
    if (pos >= 65) return { signal: TREND_SIGNAL.UP,       method: '52w-position' };
    if (pos <= 35) return { signal: TREND_SIGNAL.DOWN,     method: '52w-position' };
    return                { signal: TREND_SIGNAL.SIDEWAYS, method: '52w-position' };
  }

  // ── 5. Kripto 7 günlük değişim ───────────────────────────────────────────
  const pct7d = f(
    item.priceChangePercentage7d ??
    item.changePercent7d         ??
    item.sevenDayChangePercent   ??
    item.periodReturn7d          ??
    item.return7d
  );
  if (pct7d != null) {
    if (pct7d >  2) return { signal: TREND_SIGNAL.UP,       method: 'crypto-7d' };
    if (pct7d < -2) return { signal: TREND_SIGNAL.DOWN,     method: 'crypto-7d' };
    return                 { signal: TREND_SIGNAL.SIDEWAYS, method: 'crypto-7d' };
  }

  // ── 6. Günlük değişim % fallback ────────────────────────────────────────
  const changePct = f(
    item.changePercent         ??
    item.dailyChangePercent    ??
    item.priceChangePercentage24h
  );
  if (changePct != null) {
    if (changePct >  2) return { signal: TREND_SIGNAL.UP,       method: 'daily-change' };
    if (changePct < -2) return { signal: TREND_SIGNAL.DOWN,     method: 'daily-change' };
    return                     { signal: TREND_SIGNAL.SIDEWAYS, method: 'daily-change' };
  }

  // ── 7. Yeterli veri yok ─────────────────────────────────────────────────
  return { signal: null, method: 'no-data' };
}
