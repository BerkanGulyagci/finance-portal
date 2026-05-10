// ── Altın türleri tanımları ───────────────────────────────────────────────────

export const GOLD_TABS = [
  {
    key: 'ons',
    label: 'Ons Altın',
    currency: 'USD',
    spotField: 'onsUsd',
    canCandle: true,
    isTheoretical: false,
    grossWeight: null,
    fineness: null,
  },
  {
    key: 'gram',
    label: 'Gram Altın',
    currency: 'TRY',
    spotField: 'gramGoldTry',
    canCandle: true,
    isTheoretical: false,
    grossWeight: 1,
    fineness: 1.0,
  },
  {
    key: 'ceyrek',
    label: 'Çeyrek Altın',
    currency: 'TRY',
    spotField: 'quarterGoldTry',
    canCandle: false,
    isTheoretical: true,
    grossWeight: 1.754,
    fineness: 0.9166,
  },
  {
    key: 'yarim',
    label: 'Yarım Altın',
    currency: 'TRY',
    spotField: 'halfGoldTry',
    canCandle: false,
    isTheoretical: true,
    grossWeight: 3.508,
    fineness: 0.9166,
  },
  {
    key: 'cumhuriyet',
    label: 'Cumhuriyet Altını',
    currency: 'TRY',
    spotField: 'republicGoldTry',
    canCandle: false,
    isTheoretical: true,
    grossWeight: 7.216,
    fineness: 0.9166,
  },
  {
    key: 'ziynet',
    label: 'Ziynet Altını',
    currency: 'TRY',
    spotField: 'ziynetGoldTry',
    canCandle: false,
    isTheoretical: true,
    grossWeight: 7.016,
    fineness: 0.9166,
  },
  {
    key: '14ayar',
    label: '14 Ayar Bilezik',
    currency: 'TRY',
    spotField: 'fourteenKBraceletTry',
    canCandle: false,
    isTheoretical: true,
    grossWeight: 1,
    fineness: 0.5850,
  },
  {
    key: '22ayar',
    label: '22 Ayar Bilezik',
    currency: 'TRY',
    spotField: 'twentyTwoKBraceletTry',
    canCandle: false,
    isTheoretical: true,
    grossWeight: 1,
    fineness: 0.9166,
  },
];

export const RANGES = ['1D', '1W', '1M', '3M', '1Y', '5Y', 'ALL'];
export const RANGE_LABELS = {
  '1D':  '1G',
  '1W':  '1H',
  '1M':  '1A',
  '3M':  '3A',
  '1Y':  '1Y',
  '5Y':  '5Y',
  'ALL': 'Tümü',
};

export const CANDLE_DISCLAIMER =
  'Mum grafikte açılış değeri BIST tarafından verilmediği için önceki gün kapanışından sentetik olarak üretilmiştir.';

export const THEORETICAL_DISCLAIMER =
  'Bu fiyatlar Borsa İstanbul resmi metal fiyatından hesaplanan teorik referans değerlerdir. ' +
  'Serbest piyasa alış/satış, işçilik, basım primi ve makas dahil değildir.';

// ── Teorik fiyat hesabı ───────────────────────────────────────────────────────

/**
 * BIST gram TRY history noktasından seçili tab için teorik fiyat hesaplar.
 * Çizgi grafik için weightedAverage, mum için close/high/low kullanılır.
 */
export function calcTheoreticalPrice(baseGram, grossWeight, fineness) {
  if (baseGram == null || isNaN(baseGram)) return null;
  return baseGram * grossWeight * fineness;
}

/** Spot response'dan seçili tab için güncel fiyatı döndürür */
export function getSpotPrice(spot, tab) {
  if (!spot || !tab) return null;
  const v = spot[tab.spotField];
  return v != null ? parseFloat(v) : null;
}

/** Sayı formatlayıcı */
export function fmt(v, dec = 2) {
  if (v == null || v === '' || isNaN(parseFloat(v))) return '-';
  return parseFloat(v).toLocaleString('tr-TR', {
    minimumFractionDigits: dec,
    maximumFractionDigits: dec,
  });
}
