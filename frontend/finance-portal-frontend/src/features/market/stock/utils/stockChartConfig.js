/**
 * Hisse grafikleri — paylaşılan sabitler ve yardımcılar
 * (CandlestickChart, LineChart, DrawingToolbar tarafından kullanılır).
 */

/** Çizim aracı grupları (DrawingToolbar + Chart paneli "aktif araç" göstergesinde). */
export const DRAWING_TOOLS = [
  { group: 'Çizgiler', tools: [
    { id: 'segment',                label: 'Çizgi Segmenti',   icon: '╱' },
    { id: 'straightLine',           label: 'Düz Çizgi (∞)',    icon: '⟵⟶' },
    { id: 'rayLine',                label: 'Işın',             icon: '⟶' },
    { id: 'horizontalStraightLine', label: 'Yatay Çizgi',      icon: '─' },
    { id: 'verticalStraightLine',   label: 'Dikey Çizgi',      icon: '│' },
    { id: 'horizontalSegment',      label: 'Yatay Segment',    icon: '━' },
    { id: 'verticalSegment',        label: 'Dikey Segment',    icon: '┃' },
    { id: 'priceLine',              label: 'Fiyat Çizgisi',    icon: '₺─' },
  ]},
  { group: 'Kanallar', tools: [
    { id: 'parallelStraightLine',   label: 'Paralel Kanal',    icon: '⫽' },
    { id: 'priceChannelLine',       label: 'Fiyat Kanalı',     icon: '▱' },
  ]},
  { group: 'Fibonacci', tools: [
    { id: 'fibonacciLine',          label: 'Fibonacci Retracement', icon: 'Fib' },
  ]},
  { group: 'Şekiller', tools: [
    { id: 'customRect',   label: 'Dikdörtgen', icon: '□' },
    { id: 'customCircle', label: 'Çember',     icon: '○' },
  ]},
];

/** Hareketli ortalama periyotları + buton renkleri (MA çizgi rengiyle eşit). */
export const MA_PERIODS = [
  { period: 20,  color: '#f59e0b', label: 'MA20'  },
  { period: 50,  color: '#8b5cf6', label: 'MA50'  },
  { period: 200, color: '#ef4444', label: 'MA200' },
];

/** MA çizgi stilleri (klinecharts default paleti yerine MA_PERIODS renklerini kullan). */
export function maLineStyles(periods) {
  return { lines: periods.map(p => ({ color: MA_PERIODS.find(m => m.period === p)?.color ?? '#888', size: 1.5 })) };
}

// ── MA ısınması (finans-sitesi davranışı) ──────────────────────────────────────
// Seçili aralık için, MA'nın pencerenin BAŞINDAN dolu çizilebilmesi adına pencereden
// ÖNCE ekstra veri çekeriz (aynı interval, daha uzun range). MA tüm seri üzerinden
// hesaplanır; ekranda yalnız seçili pencere gösterilir (ısınma sola kaydırılır).
export const STOCK_WARMUP_RANGE = {
  '1d': '5d', '5d': '1mo',
  '1mo': '1y', '3mo': '2y', '6mo': '2y', '1y': '2y', '5y': '10y', '10y': '10y',
};
export const RANGE_WINDOW_MS = {
  '1d': 1 * 864e5, '5d': 5 * 864e5, '1mo': 31 * 864e5, '3mo': 93 * 864e5,
  '6mo': 186 * 864e5, '1y': 366 * 864e5, '5y': 1827 * 864e5, '10y': Infinity,
};

/** Tüm veri (ısınma + pencere) uygulandıktan sonra görünür alanı yalnız pencereye sığdırır. */
export function fitVisibleToWindow(chart, chartId, allData, windowStartTs) {
  if (!chart || !allData?.length) return;
  requestAnimationFrame(() => {
    try {
      const el = document.getElementById(chartId);
      const width = el?.clientWidth ?? 0;
      if (width <= 0) return;
      const windowCount = windowStartTs > 0
        ? allData.filter(d => d.timestamp >= windowStartTs).length
        : allData.length;
      const count = Math.max(1, windowCount);
      chart.setOffsetRightDistance(8);
      chart.setBarSpace(Math.max(2, (width - 16) / count));
    } catch (_) { /* yoksay */ }
  });
}

/** Alt indikatör menüsünde gösterilen seçenekler. */
export const SUB_INDICATORS = [
  { name: 'VOL',  label: 'Hacim', color: '#6b7280' },
  { name: 'RSI',  label: 'RSI',   color: '#f59e0b' },
  { name: 'MACD', label: 'MACD',  color: '#3b82f6' },
  { name: 'KDJ',  label: 'KDJ',   color: '#8b5cf6' },
  { name: 'BOLL', label: 'BOLL',  color: '#10b981' },
];
