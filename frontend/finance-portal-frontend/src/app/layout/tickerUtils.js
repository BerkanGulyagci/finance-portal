// MarketTicker — saf yardımcılar (JSX yok): route çözümü, % hesapları, seri dönüşümleri.
// MarketTicker.jsx'ten ayrıştırıldı; mantık birebir aynıdır. Bağımsız test edilebilir.

export const COLOR_UP = '#10b981';
export const COLOR_DOWN = '#ef4444';
export const COLOR_NEUTRAL = '#64748b';

// Custom ticker varlık tipi → detay route segmenti
const CUSTOM_ROUTE_SEG = {
  STOCK: 'stocks', CRYPTO: 'crypto', FX: 'fx', FUTURE: 'futures',
  COMMODITY: 'commodities', FUND: 'tefas', BOND: 'bonds',
};

// Altın türü sembolü (GoldPage compareSymbol) → /market/gold sekme key'i.
// Custom eklenmiş altın türü doğru sekmeye gitsin (gram→gram, ons→ons…) diye.
const GOLD_SYMBOL_TO_TAB = {
  GOLD: 'ons', ONS: 'ons',
  GRAM: 'gram', CEYREK: 'ceyrek', YARIM: 'yarim',
  CUMHUR: 'cumhuriyet', ZIYNET: 'ziynet', '14AYAR': '14ayar', '22AYAR': '22ayar',
};

/**
 * Ticker öğesinin tıklanınca gideceği detay route'unu çözer.
 * Karşılığı olmayan (ekonomi göstergesi gibi) öğelerde null döner → tıklanamaz.
 */
export function tickerHref(item) {
  const key = item.key || '';
  const [kind, rest] = key.split(':');

  switch (kind) {
    case 'fx':
    case 'bank':
      return rest ? `/market/fx/${encodeURIComponent(rest.toUpperCase())}` : null;
    case 'crypto':
      // Detay route coinId ister (ör. "bitcoin"); yoksa link verme.
      return item.coinId ? `/market/crypto/${encodeURIComponent(item.coinId)}` : null;
    case 'bist':
      return rest ? `/market/indices/${encodeURIComponent(rest.toUpperCase())}` : null;
    case 'gold':
      return '/market/gold';
    case 'eco':
      // Ekonomi göstergeleri (TÜFE/faiz/ÜFE/mevduat) → Türkiye ekonomisi sayfası
      return '/market/economy';
    case 'custom': {
      // key = custom:ASSETTYPE:SYMBOL
      const parts = key.split(':');
      const assetType = parts[1];
      const symbol = parts.slice(2).join(':');
      if (assetType === 'GOLD') {
        // Altın türü (GRAM/ÇEYREK/ONS…) → doğru sekme. ONS dışındakiler ?tab= ile açılır,
        // yoksa hepsi ons sekmesine düşüp "gram tıkladım ons geldi" oluyordu.
        const tab = GOLD_SYMBOL_TO_TAB[(symbol || '').toUpperCase()];
        return tab && tab !== 'ons' ? `/market/gold?tab=${tab}` : '/market/gold';
      }
      if (assetType === 'CRYPTO') {
        return item.coinId ? `/market/crypto/${encodeURIComponent(item.coinId)}` : null;
      }
      if (assetType === 'BOND' && item.subType === 'EUROBOND') {
        return symbol ? `/market/bonds/global/${encodeURIComponent(symbol)}` : null;
      }
      const seg = CUSTOM_ROUTE_SEG[assetType];
      return seg && symbol ? `/market/${seg}/${encodeURIComponent(symbol)}` : null;
    }
    default:
      return null;
  }
}

/** Son değer ve yüzde değişime göre monoton eğri (rastgele değil). */
export function trendSparkline(endValue, changePercent, points = 14) {
  if (endValue == null || Number.isNaN(endValue) || endValue <= 0) return [];
  if (changePercent == null || Number.isNaN(changePercent) || Math.abs(changePercent) < 1e-6) {
    return Array.from({ length: Math.max(2, points) }, () => endValue);
  }
  const startValue = endValue / (1 + changePercent / 100);
  const n = Math.max(2, points);
  return Array.from({ length: n }, (_, i) => startValue + (endValue - startValue) * (i / (n - 1)));
}

export function dirFromChangePct(pct) {
  if (pct == null || Number.isNaN(pct)) return null;
  if (pct > 0.0001) return 'up';
  if (pct < -0.0001) return 'down';
  return null;
}

/**
 * Seriden GÜNLÜK % değişim — son nokta ile FARKLI bir takvim gününe ait önceki nokta arası.
 * Seri saatlik olsa bile (hisse/endeks 1 günde çok nokta) "son 2 nokta = son 2 saat" tuzağına
 * düşmez; gerçek dün→bugün değişimini verir. timestamps saniye cinsinden beklenir.
 */
export function dailyChangeFromSeries(closes, timestamps) {
  if (!Array.isArray(closes) || closes.length < 2) return null;
  const last = closes[closes.length - 1];
  if (!(last > 0)) return null;
  // timestamp yoksa: son 2 noktaya düş (günlük seride doğru, saatlikte yaklaşık).
  if (!Array.isArray(timestamps) || timestamps.length !== closes.length) {
    const prev = closes[closes.length - 2];
    return prev > 0 ? ((last - prev) / prev) * 100 : null;
  }
  const dayOf = (ts) => Math.floor(Number(ts) / 86400);   // gün indeksi (UTC)
  const lastDay = dayOf(timestamps[timestamps.length - 1]);
  // Sondan geriye git, GÜNÜ farklı olan ilk kapanışı bul = "önceki günün kapanışı".
  for (let i = closes.length - 2; i >= 0; i--) {
    if (dayOf(timestamps[i]) < lastDay) {
      const prev = closes[i];
      return prev > 0 ? ((last - prev) / prev) * 100 : null;
    }
  }
  // Hepsi aynı gün (ör. günün ilk saatleri) → son 2 nokta farkı.
  const prev = closes[closes.length - 2];
  return prev > 0 ? ((last - prev) / prev) * 100 : null;
}

/** TCMB günlük kapanışlardan mini-seri + GÜNLÜK % değişim (tarih varsa gün-bazlı). */
export function sparkFromFxHistory(hist, maxPoints = 16) {
  const raw = hist?.points;
  if (!Array.isArray(raw) || raw.length < 2) return { spark: [], changePct: null, dir: null };
  const closesAll = raw.map(p => parseFloat(p.close));
  const tsAll = raw.map(p => (p.date ? Math.floor(new Date(p.date).getTime() / 1000) : null));
  const hasTs = tsAll.every(v => v != null);
  const changePct = dailyChangeFromSeries(closesAll, hasTs ? tsAll : null);
  const closes = closesAll.filter(v => !Number.isNaN(v) && v > 0);
  if (closes.length < 2) return { spark: [], changePct: null, dir: null };
  const spark = closes.slice(-maxPoints);
  return { spark, changePct, dir: dirFromChangePct(changePct) };
}

export function downsample(series, maxPoints) {
  if (!series?.length) return [];
  if (series.length <= maxPoints) return [...series];
  const out = [];
  const last = series.length - 1;
  for (let i = 0; i < maxPoints; i++) {
    const idx = Math.round((i / (maxPoints - 1)) * last);
    out.push(series[idx]);
  }
  return out;
}

/** Seriyi ölçekle: son nokta endValue olsun (şekil korunur, % değişim aynı kalır). */
export function scaleSparkToEnd(spark, endValue) {
  if (!spark?.length || endValue == null || endValue <= 0) return spark ?? [];
  const last = spark[spark.length - 1];
  if (!last || last <= 0) return spark;
  const k = endValue / last;
  return spark.map(v => v * k);
}

export function changeDirFromSeries(spark) {
  if (!spark?.length || spark.length < 2) return { changePct: null, dir: null };
  const first = spark[0];
  const last = spark[spark.length - 1];
  if (first == null || first <= 0) return { changePct: null, dir: null };
  const changePct = ((last - first) / first) * 100;
  return { changePct, dir: dirFromChangePct(changePct) };
}

/** CoinGecko market_chart.prices → mini seri (grafik sayfalarıyla aynı kaynak). */
export function sparkFromGeckoChart(chart, maxPoints = 36) {
  const raw = chart?.prices;
  if (!Array.isArray(raw) || raw.length < 2) return { spark: [], changePct: null, dir: null };
  const vals = raw.map(p => parseFloat(p[1])).filter(v => !Number.isNaN(v) && v > 0);
  if (vals.length < 2) return { spark: [], changePct: null, dir: null };
  const spark = downsample(vals, maxPoints);
  const { changePct, dir } = changeDirFromSeries(spark);
  return { spark, changePct, dir };
}

/** Altın tarihsel (ONS USD) — detay grafikleriyle aynı endpoint. */
export function sparkFromGoldHist(hist, maxPoints = 36) {
  const raw = hist?.points;
  if (!Array.isArray(raw) || raw.length < 2) return { spark: [], changePct: null, dir: null };
  const vals = raw.map(p => parseFloat(p.close)).filter(v => !Number.isNaN(v) && v > 0);
  if (vals.length < 2) return { spark: [], changePct: null, dir: null };
  const spark = downsample(vals, maxPoints);
  const { changePct, dir } = changeDirFromSeries(spark);
  return { spark, changePct, dir };
}
