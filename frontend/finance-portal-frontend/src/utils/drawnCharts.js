// Çizim (overlay) yapılan grafiklerin listesi — localStorage'daki "chart-overlays:..."
// anahtarlarından türetilir (ekstra veri saklanmaz; useChartDrawings'in yazdığını okur).
//
// Anahtar formatları (bkz. grafik bileşenlerindeki persistKey):
//   chart-overlays:crypto:{coinId}     → kripto   → /market/crypto/{coinId}
//   chart-overlays:fx:{symbol}         → döviz    → /market/fx/{symbol}
//   chart-overlays:fund:{code}         → fon      → /market/tefas/{code}
//   chart-overlays:commodity:{symbol}  → emtia    → /market/commodities/{symbol}
//   chart-overlays:{symbol}            → hisse    → /market/stocks/{symbol}  (öneksiz)

import { indexCodeOf } from '../features/portfolio/constants/watchlistMarketRoutes';

const PREFIX = 'chart-overlays:';

const TYPE_META = {
  crypto:    { label: 'Kripto', seg: 'crypto' },
  fx:        { label: 'Döviz',  seg: 'fx' },
  fund:      { label: 'Fon',    seg: 'tefas' },
  commodity: { label: 'Emtia',  seg: 'commodities' },
  stock:     { label: 'Hisse',  seg: 'stocks' },
  index:     { label: 'Endeks', seg: 'indices' },
  // Değerli metaller — kendi sayfaları (sembolsüz segment + sekme query). GoldChart persistId
  // formatı "metal:sekme" (ör. gold:gram) → route /market/gold?tab=gram.
  gold:      { label: 'Altın',   seg: 'gold',      metal: true },
  silver:    { label: 'Gümüş',   seg: 'silver',    metal: true },
  platinum:  { label: 'Platin',  seg: 'platinum',  metal: true },
  palladium: { label: 'Paladyum', seg: 'palladium', metal: true },
};

/** "chart-overlays:crypto:bitcoin" → { type:'crypto', symbol:'bitcoin', tab? } */
function parseKey(key) {
  const rest = key.slice(PREFIX.length);
  const firstColon = rest.indexOf(':');
  if (firstColon > 0) {
    const maybeType = rest.slice(0, firstColon);
    if (TYPE_META[maybeType]) {
      const after = rest.slice(firstColon + 1);
      // Metal: "gold:gram" → symbol = metal adı (rota segmenti zaten metal), tab = sekme.
      if (TYPE_META[maybeType].metal) {
        const tab = after.split(':')[0] || null;   // sekme (gram/ons/ceyrek…)
        return { type: maybeType, symbol: maybeType, tab };
      }
      // Tipli anahtar (crypto/fx/...). Sembole eski bir bug'dan range eki (":3A3mo") bulaşmışsa
      // ilk ":"'e kadarını al — çizimler sembol başına tek listede tutulur, range içermez.
      return { type: maybeType, symbol: after.split(':')[0] };
    }
  }
  // Önek yok → hisse (chart-overlays:THYAO.IS). Eski bug'dan range eki bulaşmış olabilir
  // (chart-overlays:XU100.IS:3A3mo → bozuk URL'e gidiyordu) → ilk ":"'den öncesini al.
  const sym = rest.split(':')[0];
  // BIST endeksleri (XUHIZ.IS, XU100.IS…) öneksiz kaydedilir ama hisse DEĞİL → endeks rotasına
  // (/market/indices/:code) gitmeli, yoksa hisse detayı veri bulamayıp boş gelir.
  if (indexCodeOf(sym)) return { type: 'index', symbol: indexCodeOf(sym) };
  return { type: 'stock', symbol: sym };
}

/** Hisse sembolü THYAO.IS → THYAO; kripto/diğer olduğu gibi gösterilir. */
function displaySymbol(type, symbol) {
  if (TYPE_META[type]?.metal) return TYPE_META[type].label;   // Altın/Gümüş/Platin/Paladyum
  if (type === 'stock' && symbol.includes('.')) return symbol.split('.')[0];
  if (type === 'crypto') return symbol.toUpperCase();
  return symbol;
}

/** Bir çizim kalemi için detay rotası (metal'de sembolsüz segment + ?tab=). */
function routeFor(type, symbol, tab) {
  const meta = TYPE_META[type] || TYPE_META.stock;
  if (meta.metal) {
    // /market/gold?tab=gram — ons sekmesi default olduğu için tab=ons'ta query eklemeyiz.
    return tab && tab !== 'ons' ? `/market/${meta.seg}?tab=${tab}` : `/market/${meta.seg}`;
  }
  return `/market/${meta.seg}/${encodeURIComponent(symbol)}`;
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

      const { type, symbol, tab } = parseKey(key);
      if (!symbol) continue;
      const meta = TYPE_META[type] || TYPE_META.stock;
      out.push({
        allKeys: [key],       // bu varlığa ait TÜM localStorage anahtarları (silme için)
        key,
        type,
        typeLabel: meta.label,
        symbol,
        // Metal sekmelerini (gram/ons…) etikette göster: "Altın · gram"
        display: meta.metal && tab ? `${meta.label} · ${tab}` : displaySymbol(type, symbol),
        route: routeFor(type, symbol, tab),
        count: arr.length,
      });
    }
  } catch {
    /* localStorage erişilemezse boş liste */
  }
  // Aynı varlığa işaret eden anahtarları (ör. doğru "XU100.IS" + eski-bug "XU100.IS:3A3mo")
  // TEK satırda birleştir → listede çift görünmesin. Çizim sayıları toplanır, tüm anahtarlar tutulur.
  const merged = new Map();
  for (const it of out) {
    // route'a göre birleştir — metal sekmeleri (gram/ons) farklı route → ayrı satır kalsın,
    // ama aynı route'a düşen mükerrer key'ler (eski range-ekli vb.) tek satırda toplansın.
    const id = `${it.type}|${it.route}`;
    const ex = merged.get(id);
    if (ex) { ex.count += it.count; ex.allKeys.push(it.key); }
    else merged.set(id, it);
  }
  const deduped = [...merged.values()];
  // Çizim sayısı çok olan üstte (daha "çalışılmış" grafikler öne)
  deduped.sort((a, b) => b.count - a.count || a.display.localeCompare(b.display, 'tr'));
  return deduped;
}

/** Bir grafiğin çizimlerini siler (localStorage + sunucu senkronu prefSet ile).
 *  keyOrKeys tek anahtar ya da anahtar dizisi olabilir (birleştirilmiş satırlar için tümü silinir). */
export function removeDrawnChart(keyOrKeys, prefSet) {
  const keys = Array.isArray(keyOrKeys) ? keyOrKeys : [keyOrKeys];
  for (const key of keys) {
    if (!key) continue;
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
}
