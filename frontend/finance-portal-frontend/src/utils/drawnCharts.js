// Çizim (overlay) yapılan grafiklerin listesi — localStorage'daki "chart-overlays:..."
// anahtarlarından türetilir (ekstra veri saklanmaz; useChartDrawings'in yazdığını okur).
//
// Anahtar formatları (bkz. grafik bileşenlerindeki persistKey):
//   chart-overlays:crypto:{coinId}     → kripto   → /market/crypto/{coinId}
//   chart-overlays:fx:{symbol}         → döviz    → /market/fx/{symbol}
//   chart-overlays:fund:{code}         → fon      → /market/tefas/{code}
//   chart-overlays:commodity:{symbol}  → emtia    → /market/commodities/{symbol}
//   chart-overlays:{symbol}            → hisse    → /market/stocks/{symbol}  (öneksiz)

const PREFIX = 'chart-overlays:';

const TYPE_META = {
  crypto:    { label: 'Kripto', seg: 'crypto' },
  fx:        { label: 'Döviz',  seg: 'fx' },
  fund:      { label: 'Fon',    seg: 'tefas' },
  commodity: { label: 'Emtia',  seg: 'commodities' },
  stock:     { label: 'Hisse',  seg: 'stocks' },
};

/** "chart-overlays:crypto:bitcoin" → { type:'crypto', symbol:'bitcoin' } */
function parseKey(key) {
  const rest = key.slice(PREFIX.length);
  const firstColon = rest.indexOf(':');
  if (firstColon > 0) {
    const maybeType = rest.slice(0, firstColon);
    if (TYPE_META[maybeType]) {
      return { type: maybeType, symbol: rest.slice(firstColon + 1) };
    }
  }
  // Önek yok → hisse (chart-overlays:THYAO.IS)
  return { type: 'stock', symbol: rest };
}

/** Hisse sembolü THYAO.IS → THYAO; kripto/diğer olduğu gibi gösterilir. */
function displaySymbol(type, symbol) {
  if (type === 'stock' && symbol.includes('.')) return symbol.split('.')[0];
  if (type === 'crypto') return symbol.toUpperCase();
  return symbol;
}

/**
 * localStorage'daki tüm çizili grafikleri döndürür.
 * @returns {{key,type,typeLabel,symbol,display,route,count}[]} — çizim sayısına göre değil,
 *          en son eklenen üstte olacak şekilde ekleme sırası korunur (localStorage sırası).
 */
export function listDrawnCharts() {
  const out = [];
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (!key || !key.startsWith(PREFIX)) continue;

      let arr;
      try {
        arr = JSON.parse(localStorage.getItem(key) || '[]');
      } catch {
        arr = [];
      }
      // Boş liste (çizim silinmiş ama anahtar kalmış) → atla
      if (!Array.isArray(arr) || arr.length === 0) continue;

      const { type, symbol } = parseKey(key);
      if (!symbol) continue;
      const meta = TYPE_META[type] || TYPE_META.stock;
      out.push({
        key,
        type,
        typeLabel: meta.label,
        symbol,
        display: displaySymbol(type, symbol),
        route: `/market/${meta.seg}/${encodeURIComponent(symbol)}`,
        count: arr.length,
      });
    }
  } catch {
    /* localStorage erişilemezse boş liste */
  }
  // Çizim sayısı çok olan üstte (daha "çalışılmış" grafikler öne)
  out.sort((a, b) => b.count - a.count || a.display.localeCompare(b.display, 'tr'));
  return out;
}

/** Bir grafiğin çizimlerini siler (localStorage + sunucu senkronu prefSet ile). */
export function removeDrawnChart(key, prefSet) {
  try {
    if (typeof prefSet === 'function') {
      prefSet(key, []); // boş liste = çizim yok; sunucuyla da senkronlanır
    } else {
      localStorage.removeItem(key);
    }
  } catch {
    /* yoksay */
  }
}
