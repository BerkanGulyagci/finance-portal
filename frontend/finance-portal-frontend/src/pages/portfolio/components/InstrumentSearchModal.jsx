import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Search, X, ChevronRight } from 'lucide-react';
import client from '../../../api/client';

/**
 * Yeniden kullanılabilir enstrüman arama modal'ı.
 * - Tip seçilince hemen varsayılan liste gösterilir
 * - Arama case-insensitive, hem sembol hem isim üzerinden
 * - Sonsuz kaydırma: aşağı kaydırdıkça daha fazla sonuç yüklenir
 * - FON → Rasyonet TEFAS fonları
 * - VADELİ → VİOP sözleşmeleri
 * - EMTİA → önce 6 öne çıkan BİST kıymetli maden (gram ₺ / ons $), genişletince Kg·EUR·Yahoo emtialar
 */

const ASSET_TYPES = [
  { value: 'STOCK',     label: 'Hisse',    placeholder: 'THYAO.IS, AAPL, GARAN...' },
  { value: 'CRYPTO',    label: 'Kripto',   placeholder: 'BTC, ETH, BNB...' },
  { value: 'FX',        label: 'Döviz',    placeholder: 'USD, EUR, GBP...' },
  { value: 'FUND',      label: 'Fon',      placeholder: 'AAL, SPY, QQQ...' },
  { value: 'FUTURE',    label: 'Vadeli',   placeholder: 'XAUTRYM26, F_THYAO...' },
  { value: 'GOLD',      label: 'Altın',    placeholder: 'GOLD, XAU, gram altın...' },
  { value: 'COMMODITY', label: 'Emtia',    placeholder: 'Gram Gümüş, WTI Ham Petrol, Bakır...' },
  { value: 'BOND',      label: 'Tahvil',   placeholder: 'TRD070727K10...' },
];

const SEE_ALL_LINKS = {
  STOCK:     { label: 'Tüm hisseleri gör', path: '/market/stocks' },
  CRYPTO:    { label: 'Tüm kriptoları gör', path: '/market/crypto' },
  FX:        { label: 'Tüm dövizleri gör', path: '/market/fx' },
  FUND:      { label: 'Tüm fonları gör', path: '/market/tefas' },
  FUTURE:    { label: 'Tüm vadeli sözleşmeleri gör', path: '/market/futures' },
  GOLD:      { label: 'Altın fiyatlarını gör', path: '/market/gold' },
  COMMODITY: { label: 'Tüm emtiaları gör', path: '/market/commodities' },
  BOND:      { label: 'Tahvil/Bono listesini gör', path: '/market/bonds' },
};

// Statik listeler — API desteklemeyen tipler için
const STATIC_GOLD = [
  { symbol: 'GOLD',    name: 'Altın (Ons)' },
  { symbol: 'GRAM',    name: 'Gram Altın' },
  { symbol: 'CEYREK',  name: 'Çeyrek Altın' },
  { symbol: 'YARIM',   name: 'Yarım Altın' },
  { symbol: 'TAM',     name: 'Tam Altın' },
  { symbol: 'CUMHUR',  name: 'Cumhuriyet Altını' },
  { symbol: 'ATA',     name: 'Ata Altın' },
];

const STATIC_BOND = [
  { symbol: 'TRD070727K10', name: 'Devlet Tahvili 2027' },
  { symbol: 'TRB170626T13', name: 'Devlet Tahvili 2026' },
  { symbol: 'TRT180237T13', name: 'Devlet Tahvili 2037' },
  { symbol: 'TRT210235T10', name: 'Devlet Tahvili 2035' },
  { symbol: 'TRT270934T18', name: 'Devlet Tahvili 2034' },
];

// Kıymetli madenler — BIST kategorileri (SilverPage/PreciousMetals ile aynı yapı)
// symbol formatı: METAL:CATEGORY (handleSelect'te parse edilir)
const PRECIOUS_METALS_BIST = [
  // Gümüş
  { symbol: 'SILVER:GRAM_TRY',  name: 'Gram Gümüş (₺)',  metal: 'silver',    category: 'GRAM_TRY'  },
  { symbol: 'SILVER:KG_TRY',    name: 'Kg Gümüş (₺)',    metal: 'silver',    category: 'KG_TRY'    },
  { symbol: 'SILVER:USD_ONS',   name: 'Ons Gümüş ($)',   metal: 'silver',    category: 'USD_ONS'   },
  // Platin
  { symbol: 'PLATINUM:GRAM_TRY', name: 'Gram Platin (₺)', metal: 'platinum',  category: 'GRAM_TRY'  },
  { symbol: 'PLATINUM:KG_TRY',   name: 'Kg Platin (₺)',   metal: 'platinum',  category: 'KG_TRY'    },
  { symbol: 'PLATINUM:USD_ONS',  name: 'Ons Platin ($)',  metal: 'platinum',  category: 'USD_ONS'   },
  { symbol: 'PLATINUM:EUR_ONS',  name: 'Ons Platin (€)',  metal: 'platinum',  category: 'EUR_ONS'   },
  // Paladyum
  { symbol: 'PALLADIUM:GRAM_TRY', name: 'Gram Paladyum (₺)', metal: 'palladium', category: 'GRAM_TRY' },
  { symbol: 'PALLADIUM:KG_TRY',   name: 'Kg Paladyum (₺)',   metal: 'palladium', category: 'KG_TRY'   },
  { symbol: 'PALLADIUM:USD_ONS',  name: 'Ons Paladyum ($)',  metal: 'palladium', category: 'USD_ONS'  },
  { symbol: 'PALLADIUM:EUR_ONS',  name: 'Ons Paladyum (€)',  metal: 'palladium', category: 'EUR_ONS'  },
];

/** İlk görünüm + arama önceliği: gram ₺ ve ons $ (Kg / EUR ons burada değil) */
const FEATURED_PRECIOUS_SYMBOLS = new Set([
  'SILVER:GRAM_TRY',
  'SILVER:USD_ONS',
  'PLATINUM:GRAM_TRY',
  'PLATINUM:USD_ONS',
  'PALLADIUM:GRAM_TRY',
  'PALLADIUM:USD_ONS',
]);

// Yahoo Finance emtia sembolleri — sadece commodities sayfasındakiler, Türkçe isimli
const YAHOO_COMMODITIES = [
  // Enerji
  { symbol: 'CL=F',  name: 'WTI Ham Petrol'  },
  { symbol: 'BZ=F',  name: 'Brent Ham Petrol' },
  { symbol: 'NG=F',  name: 'Doğal Gaz'        },
  // Sanayi Metalleri
  { symbol: 'HG=F',  name: 'Bakır'            },
  // Tarım
  { symbol: 'ZW=F',  name: 'Buğday'           },
  { symbol: 'ZC=F',  name: 'Mısır'            },
  { symbol: 'KC=F',  name: 'Kahve'            },
  { symbol: 'CC=F',  name: 'Kakao'            },
  { symbol: 'CT=F',  name: 'Pamuk'            },
];

// TCMB döviz isimleri (API'den name gelmiyorsa fallback)
const FX_NAMES = {
  USD: 'ABD Doları', EUR: 'Euro', GBP: 'İngiliz Sterlini', JPY: 'Japon Yeni',
  CHF: 'İsviçre Frangı', CAD: 'Kanada Doları', AUD: 'Avustralya Doları',
  DKK: 'Danimarka Kronu', SEK: 'İsveç Kronu', NOK: 'Norveç Kronu',
  KWD: 'Kuveyt Dinarı', SAR: 'Suudi Arabistan Riyali', AED: 'BAE Dirhemi',
  CNY: 'Çin Yuanı', RUB: 'Rus Rublesi', INR: 'Hindistan Rupisi',
  BRL: 'Brezilya Reali', MXN: 'Meksika Pesosu', ZAR: 'Güney Afrika Randı',
  SGD: 'Singapur Doları', HKD: 'Hong Kong Doları', NZD: 'Yeni Zelanda Doları',
  TRY: 'Türk Lirası', BGN: 'Bulgar Levası', RON: 'Romen Leyi',
  HUF: 'Macar Forinti', CZK: 'Çek Korunası', PLN: 'Polonya Zlotisi',
  HRK: 'Hırvat Kunası', ISK: 'İzlanda Kronası', MKD: 'Makedon Dinarı',
  PKR: 'Pakistan Rupisi', QAR: 'Katar Riyali', OMR: 'Umman Riyali',
  BHD: 'Bahreyn Dinarı', JOD: 'Ürdün Dinarı', LYD: 'Libya Dinarı',
  MAD: 'Fas Dirhemi', DZD: 'Cezayir Dinarı', TND: 'Tunus Dinarı',
  EGP: 'Mısır Poundu', ILS: 'İsrail Şekeli', IRR: 'İran Riyali',
  IQD: 'Irak Dinarı', SYP: 'Suriye Poundu', LBP: 'Lübnan Poundu',
  YER: 'Yemen Riyali', AFN: 'Afgan Afganisi', AZN: 'Azerbaycan Manatı',
  GEL: 'Gürcistan Larisi', KZT: 'Kazakistan Tengesi', UZS: 'Özbek Somu',
  UAH: 'Ukrayna Grivnası', BYR: 'Belarus Rublesi', MDL: 'Moldova Leyi',
  ALL: 'Arnavut Leki', BAM: 'Bosna Marki', RSD: 'Sırp Dinarı',
};

const PAGE_SIZE = 15;

// ── Veri çekme fonksiyonları ──────────────────────────────────────────────────

async function fetchAll(type) {
  try {
    if (type === 'STOCK') {
      // Tüm hisseleri sayfalı çek (getAllStocks mantığı)
      try {
        const firstRes = await client.get('/api/market/stocks', { params: { page: 0, size: 20 } });
        const totalPages = firstRes.data?.data?.totalPages ?? 1;
        const results = [...(firstRes.data?.data?.content ?? [])];
        // Paralel çek (max 10 sayfa)
        const pages = Math.min(totalPages, 10);
        if (pages > 1) {
          const rest = await Promise.all(
            Array.from({ length: pages - 1 }, (_, i) =>
              client.get('/api/market/stocks', { params: { page: i + 1, size: 20 } })
            )
          );
          rest.forEach(r => results.push(...(r.data?.data?.content ?? [])));
        }
        return results.map(s => ({ symbol: s.symbol, name: s.name ?? s.symbol })).filter(s => s.symbol);
      } catch {
        return [];
      }
    }

    if (type === 'CRYPTO') {
      const { data } = await client.get('/api/market/crypto', { params: { page: 0, size: 250 } });
      return (data.data ?? []).map(c => ({
        symbol: c.symbol?.toUpperCase() ?? '',
        name: c.name ?? c.symbol ?? '',
      }));
    }

    if (type === 'FX') {
      const { data } = await client.get('/api/market/fx/tcmb/latest');
      return (data.data?.rates ?? []).map(r => ({
        symbol: r.symbol,
        name: r.name ?? FX_NAMES[r.symbol] ?? r.symbol,
      }));
    }

    if (type === 'FUND') {
      // 4 fon kaynağını paralel çek: TEFAS, BES, OKS, Osmanlı
      const [tefasRes, besRes, oksRes, osmanlıRes] = await Promise.allSettled([
        client.get('/api/market/funds/tefas/all'),
        client.get('/api/market/funds/bes/all'),
        client.get('/api/market/funds/oks/all'),
        client.get('/api/market/funds/osmanli/bulletin'),
      ]);

      const tefas = tefasRes.status === 'fulfilled'
        ? (tefasRes.value.data?.data ?? []).map(f => ({
            symbol: f.code ?? f.uniqueCode ?? '',
            name: f.name ?? f.code ?? '',
            category: 'TEFAS',
          }))
        : [];

      const bes = besRes.status === 'fulfilled'
        ? (besRes.value.data?.data ?? []).map(f => ({
            symbol: f.code ?? f.uniqueCode ?? '',
            name: f.name ?? f.code ?? '',
            category: 'BES',
          }))
        : [];

      const oks = oksRes.status === 'fulfilled'
        ? (oksRes.value.data?.data ?? []).map(f => ({
            symbol: f.code ?? f.uniqueCode ?? '',
            name: f.name ?? f.code ?? '',
            category: 'OKS',
          }))
        : [];

      const osmanli = osmanlıRes.status === 'fulfilled'
        ? (osmanlıRes.value.data?.data ?? []).map(f => ({
            symbol: f.code ?? f.uniqueCode ?? '',
            name: f.name ?? f.code ?? '',
            category: 'Osmanlı',
          }))
        : [];

      // Hepsini birleştir, boş sembol olanları filtrele, tekrarları kaldır
      const all = [...tefas, ...bes, ...oks, ...osmanli].filter(f => f.symbol);
      const seen = new Set();
      return all.filter(f => {
        if (seen.has(f.symbol)) return false;
        seen.add(f.symbol);
        return true;
      });
    }

    if (type === 'FUTURE') {
      // VİOP sözleşmeleri
      try {
        const { data } = await client.get('/api/market/futures/viop/contracts');
        const contracts = data.data ?? [];
        if (contracts.length > 0) {
          return contracts.map(c => ({
            symbol: c.contractName ?? c.name ?? '',
            name: c.underlyingAsset ?? c.contractName ?? '',
          })).filter(c => c.symbol);
        }
      } catch {
        // VİOP API başarısız olursa statik listeye düş
      }
      // Fallback statik liste
      return [
        { symbol: 'ES=F',  name: 'S&P 500 Vadeli' },
        { symbol: 'NQ=F',  name: 'Nasdaq 100 Vadeli' },
        { symbol: 'YM=F',  name: 'Dow Jones Vadeli' },
        { symbol: 'RTY=F', name: 'Russell 2000 Vadeli' },
        { symbol: 'GC=F',  name: 'Altın Vadeli' },
        { symbol: 'SI=F',  name: 'Gümüş Vadeli' },
        { symbol: 'CL=F',  name: 'Ham Petrol Vadeli' },
        { symbol: 'BZ=F',  name: 'Brent Petrol Vadeli' },
        { symbol: 'NG=F',  name: 'Doğal Gaz Vadeli' },
        { symbol: 'HG=F',  name: 'Bakır Vadeli' },
        { symbol: 'ZW=F',  name: 'Buğday Vadeli' },
        { symbol: 'ZC=F',  name: 'Mısır Vadeli' },
        { symbol: '6E=F',  name: 'EUR/USD Vadeli' },
        { symbol: '6J=F',  name: 'JPY/USD Vadeli' },
        { symbol: '6B=F',  name: 'GBP/USD Vadeli' },
      ];
    }

    if (type === 'GOLD') {
      return STATIC_GOLD;
    }

    if (type === 'COMMODITY') {
      // Kıymetli madenler (BIST) + Yahoo emtialar — API'ye gerek yok, statik liste yeterli
      // API'den gelen liste zaten YAHOO_COMMODITIES ile aynı semboller
      return [...PRECIOUS_METALS_BIST, ...YAHOO_COMMODITIES];
    }

    if (type === 'BOND') {
      // EVDS'den gerçek tahvil listesi çekmeyi dene
      try {
        const { data } = await client.get('/api/market/bonds/evds', { params: { page: 0, size: 50, sortBy: 'maturityDate', sortDir: 'asc' } });
        const items = data.data?.items ?? [];
        if (items.length > 0) {
          return items.map(b => ({
            symbol: b.instrumentCode ?? '',
            name: b.name ?? b.instrumentCode ?? '',
          })).filter(b => b.symbol);
        }
      } catch { /* fallback */ }
      return STATIC_BOND;
    }
  } catch {
    // hata
  }
  return [];
}

function normalize(str) {
  return str
    .toLowerCase()
    .replace(/İ/g, 'i')
    .replace(/I/g, 'ı')
    .replace(/Ğ/g, 'ğ')
    .replace(/Ü/g, 'ü')
    .replace(/Ş/g, 'ş')
    .replace(/Ö/g, 'ö')
    .replace(/Ç/g, 'ç');
}

function filterItems(items, query) {
  if (!query.trim()) return items;
  const q = normalize(query.trim());
  return items.filter(item =>
    normalize(item.symbol).includes(q) ||
    (item.name && normalize(item.name).includes(q))
  );
}

function preciousSortKey(symbol) {
  if (!symbol?.includes(':')) return 999;
  const [metal, cat] = symbol.split(':');
  const mo = { SILVER: 0, PLATINUM: 1, PALLADIUM: 2 }[metal] ?? 5;
  const co = { GRAM_TRY: 0, USD_ONS: 1, KG_TRY: 2, EUR_ONS: 3 }[cat] ?? 9;
  return mo * 10 + co;
}

function sortPreciousByTier(items) {
  return [...items].sort((a, b) => preciousSortKey(a.symbol) - preciousSortKey(b.symbol));
}

/**
 * Emtia: öne çıkan 6 kıymetli maden (+ ilk görünümde gizli Kg/EUR/Yahoo),
 * aramada önce gram/ons ($), sonra diğerleri.
 */
function buildCommoditySections(allItems, query, expanded) {
  const q = query.trim();

  if (!q) {
    const featuredList = allItems.filter(i => FEATURED_PRECIOUS_SYMBOLS.has(i.symbol));
    if (!expanded) {
      const restCount = allItems.length - featuredList.length;
      return {
        sections: [{ key: 'feat', title: 'Öne çıkanlar', items: featuredList }],
        showExpandButton: restCount > 0,
        totalCount: allItems.length,
        visibleCount: featuredList.length,
      };
    }
    const otherPrecious = allItems.filter(i => i.metal && !FEATURED_PRECIOUS_SYMBOLS.has(i.symbol));
    const yahoo = allItems.filter(i => !i.metal);
    const sections = [];
    if (featuredList.length) {
      sections.push({ key: 'feat', title: 'Öne çıkanlar', items: sortPreciousByTier(featuredList) });
    }
    if (otherPrecious.length) {
      sections.push({
        key: 'otherMetal',
        title: 'Diğer kıymetli madenler',
        items: sortPreciousByTier(otherPrecious),
      });
    }
    if (yahoo.length) {
      sections.push({ key: 'yahoo', title: 'Diğer emtialar', items: yahoo });
    }
    return {
      sections,
      showExpandButton: false,
      totalCount: allItems.length,
      visibleCount: allItems.length,
    };
  }

  const filteredAll = filterItems(allItems, query);
  const precious = filteredAll.filter(i => i.metal);
  const yahoo = filteredAll.filter(i => !i.metal);
  const primaryP = precious.filter(i => FEATURED_PRECIOUS_SYMBOLS.has(i.symbol));
  const secondaryP = precious.filter(i => !FEATURED_PRECIOUS_SYMBOLS.has(i.symbol));

  const primaryBlock = [...sortPreciousByTier(primaryP), ...yahoo];
  const sections = [];

  if (primaryBlock.length && secondaryP.length) {
    sections.push({
      key: 's1',
      title: 'Öne çıkan sonuçlar',
      items: primaryBlock,
    });
    sections.push({
      key: 's2',
      title: 'Diğer sonuçlar',
      items: sortPreciousByTier(secondaryP),
    });
  } else if (primaryBlock.length) {
    sections.push({ key: 's1', title: filteredAll.length > 1 ? 'Sonuçlar' : null, items: primaryBlock });
  } else if (secondaryP.length) {
    sections.push({
      key: 's2',
      title: null,
      items: sortPreciousByTier(secondaryP),
    });
  }

  return {
    sections,
    showExpandButton: false,
    totalCount: filteredAll.length,
    visibleCount: filteredAll.length,
  };
}

// ── Component ─────────────────────────────────────────────────────────────────

export default function InstrumentSearchModal({ portfolioName, onSelect, onClose }) {
  const [activeType, setActiveType] = useState('STOCK');
  const [query, setQuery] = useState('');
  const [allItems, setAllItems] = useState([]);   // tüm veri
  const [displayed, setDisplayed] = useState([]); // gösterilen (sayfalı)
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [priceLoading, setPriceLoading] = useState(false);
  const [commodityExpanded, setCommodityExpanded] = useState(false);
  const listRef = useRef(null);
  const searchRef = useRef(null);
  const searchTimer = useRef(null);

  // Tüm veriyi yükle
  const loadAll = useCallback(async (type) => {
    setLoading(true);
    setAllItems([]);
    setDisplayed([]);
    setPage(1);
    try {
      const items = await fetchAll(type);
      setAllItems(items);
      if (type === 'COMMODITY') {
        setDisplayed([]);
        setHasMore(false);
        setCommodityExpanded(false);
      } else {
        const filtered = filterItems(items, '');
        setDisplayed(filtered.slice(0, PAGE_SIZE));
        setHasMore(filtered.length > PAGE_SIZE);
      }
    } catch {
      setAllItems([]);
      setDisplayed([]);
      setHasMore(false);
    } finally {
      setLoading(false);
    }
  }, []);

  // İlk yükleme
  useEffect(() => { loadAll('STOCK'); }, [loadAll]);

  // Tip değişince
  function handleTypeChange(type) {
    setActiveType(type);
    setQuery('');
    setCommodityExpanded(false);
    loadAll(type);
    setTimeout(() => searchRef.current?.focus(), 50);
  }

  // Query değişince filtrele (emtia hariç — orada buildCommoditySections)
  useEffect(() => {
    clearTimeout(searchTimer.current);
    searchTimer.current = setTimeout(() => {
      if (activeType === 'COMMODITY') return;
      const filtered = filterItems(allItems, query);
      setDisplayed(filtered.slice(0, PAGE_SIZE));
      setPage(1);
      setHasMore(filtered.length > PAGE_SIZE);
    }, 150);
    return () => clearTimeout(searchTimer.current);
  }, [query, allItems, activeType]);

  // Daha fazla yükle
  function loadMore() {
    const filtered = filterItems(allItems, query);
    const nextPage = page + 1;
    const nextItems = filtered.slice(0, nextPage * PAGE_SIZE);
    setDisplayed(nextItems);
    setPage(nextPage);
    setHasMore(nextItems.length < filtered.length);
  }

  // Scroll ile otomatik yükleme
  function handleScroll(e) {
    if (activeType === 'COMMODITY') return;
    const el = e.currentTarget;
    if (!hasMore || loading) return;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 40) {
      loadMore();
    }
  }

  async function handleSelect(item) {
    setPriceLoading(true);
    try {
      let price = null;
      try {
        if (activeType === 'STOCK') {
          const { data } = await client.get(`/api/market/stocks/${encodeURIComponent(item.symbol)}`);
          price = data.data?.summary?.price ?? null;
        } else if (activeType === 'CRYPTO') {
          const { data } = await client.get('/api/market/crypto', { params: { page: 0, size: 250 } });
          const coin = (data.data ?? []).find(c => c.symbol?.toLowerCase() === item.symbol.toLowerCase());
          price = coin?.currentPrice ?? null;
        } else if (activeType === 'FX') {
          const { data } = await client.get('/api/market/fx/tcmb/latest');
          const rate = (data.data?.rates ?? []).find(r => r.symbol === item.symbol.toUpperCase());
          price = rate?.sell ?? null;
        } else if (activeType === 'COMMODITY' || activeType === 'FUTURE' || activeType === 'GOLD') {
          try {
            // BIST kıymetli maden mi? (SILVER:GRAM_TRY formatı)
            if (item.symbol.includes(':')) {
              const [metal, cat] = item.symbol.split(':');
              const metalKey = metal.toLowerCase();
              let spotData = null;
              if (metalKey === 'silver') {
                const { data } = await client.get('/api/silver/spot');
                spotData = data.data;
              } else {
                const { data } = await client.get(`/api/precious-metals/${metalKey}/spot`);
                spotData = data.data;
              }
              if (spotData) {
                if (cat === 'GRAM_TRY') price = spotData.tryGram ?? spotData.silverGramTry ?? spotData.gramTry ?? null;
                else if (cat === 'KG_TRY') price = spotData.tryKg ?? spotData.closeTryKg ?? spotData.weightedAverageTryKg ?? null;
                else if (cat === 'USD_ONS') price = spotData.usdOns ?? spotData.silverUsdOns ?? null;
                else if (cat === 'EUR_ONS') price = spotData.eurOns ?? null;
              }
            } else {
              // Yahoo Finance emtia
              const { data } = await client.get('/api/commodities/spot', { params: { symbol: item.symbol } });
              price = data.data?.price ?? null;
            }
          } catch { /* fiyat bulunamazsa null */ }
        } else if (activeType === 'FUND') {
          try {
            // Fon fiyatı için sourceCode belirle
            const sourceCode = item.category === 'BES' ? 'TPF' : item.category === 'OKS' ? 'TAF' : 'TMF';
            const { data } = await client.get(`/api/market/funds/tefas/${encodeURIComponent(item.symbol)}`, {
              params: { sourceCode },
            });
            price = data.data?.price ?? null;
          } catch { /* fiyat bulunamazsa null */ }
        }
      } catch { /* fiyat bulunamazsa null */ }

      onSelect({
        symbol: item.symbol,
        assetType: activeType,
        name: item.name ?? item.symbol,
        price,
        category: item.category,
      });
    } finally {
      setPriceLoading(false);
    }
  }

  const seeAll = SEE_ALL_LINKS[activeType];
  const currentType = ASSET_TYPES.find(t => t.value === activeType);
  const filtered = filterItems(allItems, query);

  const commodityView = useMemo(
    () =>
      activeType === 'COMMODITY'
        ? buildCommoditySections(allItems, query, commodityExpanded)
        : null,
    [activeType, allItems, query, commodityExpanded],
  );

  function renderInstrumentRow(item, i, keyPrefix = '') {
    return (
      <button
        key={`${keyPrefix}-${item.symbol}-${i}`}
        onClick={() => handleSelect(item)}
        disabled={priceLoading}
        className="w-full flex items-center justify-between px-4 py-3 text-left transition-colors border-b border-[#3a4155] last:border-b-0 hover:bg-[#252b3b] disabled:opacity-50"
      >
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            {(activeType === 'STOCK' || activeType === 'FUND') ? (
              <>
                <span className="font-bold text-sm text-white shrink-0">{item.symbol}</span>
                {item.name && item.name !== item.symbol && (
                  <span className="text-xs text-gray-400 truncate">{item.name}</span>
                )}
              </>
            ) : (
              <>
                <span className="font-bold text-sm text-white shrink-0">{item.name ?? item.symbol}</span>
                {!item.symbol.includes(':') && item.symbol !== item.name && (
                  <span className="text-xs text-gray-500 shrink-0">{item.symbol}</span>
                )}
              </>
            )}
          </div>
          <p className="text-xs text-gray-500 mt-0.5">
            {currentType?.label}
            {item.category && (
              <span className={`ml-1.5 px-1.5 py-0.5 rounded text-[10px] font-bold ${
                item.category === 'BES'     ? 'bg-emerald-500/20 text-emerald-400' :
                item.category === 'OKS'     ? 'bg-purple-500/20 text-purple-400' :
                item.category === 'Osmanlı' ? 'bg-orange-500/20 text-orange-400' :
                                              'bg-blue-500/20 text-blue-400'
              }`}>
                {item.category}
              </span>
            )}
            {item.metal && (
              <span className="ml-1.5 px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-500/20 text-amber-400">
                BİST
              </span>
            )}
          </p>
        </div>
        <div className="flex items-center gap-2 ml-2 shrink-0">
          <span className="text-xs text-gray-500 bg-[#2f3650] px-2 py-0.5 rounded">
            {currentType?.label?.toLowerCase()}
          </span>
          <ChevronRight className="w-3.5 h-3.5 text-gray-500" />
        </div>
      </button>
    );
  }

  const commodityEmptyQuery =
    activeType === 'COMMODITY' &&
    commodityView &&
    query.trim() &&
    commodityView.sections.every(s => !s.items.length);

  return (
    <div className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4">
      <div className="bg-[#1a1f2e] rounded-2xl shadow-2xl w-full max-w-lg text-white flex flex-col" style={{ maxHeight: '85vh' }}>

        {/* Header */}
        <div className="px-6 pt-6 pb-4 shrink-0">
          <div className="flex items-start justify-between mb-1">
            <h2 className="text-xl font-bold">Enstrüman Seç</h2>
            <button type="button" onClick={onClose} className="text-gray-400 hover:text-white transition-colors mt-0.5">
              <X className="w-5 h-5" />
            </button>
          </div>
          {portfolioName && (
            <p className="text-sm text-gray-400">{portfolioName} portföyüne ekle</p>
          )}
        </div>

        {/* Tip filtreleri */}
        <div className="px-6 pb-3 shrink-0 flex gap-2 flex-wrap">
          {ASSET_TYPES.map(t => (
            <button
              key={t.value}
              type="button"
              onClick={() => handleTypeChange(t.value)}
              className={`px-3 py-1 rounded-lg text-xs font-bold transition-all ${
                activeType === t.value
                  ? 'bg-[#4a6cf7] text-white'
                  : 'bg-[#252b3b] text-gray-400 hover:bg-[#2f3650]'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {/* Arama */}
        <div className="px-6 pb-3 shrink-0">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              ref={searchRef}
              type="text"
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder={currentType?.placeholder ?? 'Sembol veya enstrüman ara...'}
              autoFocus
              className="w-full bg-[#252b3b] border border-[#3a4155] rounded-xl pl-9 pr-4 py-2.5 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-[#4a6cf7]"
            />
          </div>
        </div>

        {/* Sonuç sayısı */}
        {!loading && allItems.length > 0 && (
          <div className="px-6 pb-1 shrink-0">
            <span className="text-xs text-gray-500">
              {activeType === 'COMMODITY' && commodityView ? (
                query.trim()
                  ? `${commodityView.totalCount} sonuç · "${query}" için`
                  : commodityExpanded
                    ? `${commodityView.totalCount} enstrüman`
                    : `${commodityView.visibleCount} öne çıkan · ${commodityView.totalCount} toplam`
              ) : query.trim()
                ? `${filtered.length} sonuç · "${query}" için`
                : `${allItems.length} enstrüman`}
            </span>
          </div>
        )}

        {/* Sonuçlar — kaydırılabilir */}
        <div
          ref={listRef}
          onScroll={handleScroll}
          className="mx-6 mb-2 rounded-xl border border-[#3a4155] overflow-y-auto flex-1"
          style={{ minHeight: 120 }}
        >
          {loading && (
            <div className="flex items-center justify-center py-8 gap-1.5">
              <div className="w-1.5 h-1.5 bg-[#4a6cf7] rounded-full animate-bounce" />
              <div className="w-1.5 h-1.5 bg-[#4a6cf7]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-1.5 h-1.5 bg-[#4a6cf7]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          )}

          {!loading && activeType === 'COMMODITY' && commodityView && commodityEmptyQuery && (
            <div className="px-4 py-8 text-center">
              <p className="text-gray-400 text-sm">
                <span className="font-semibold">&quot;{query}&quot;</span> için sonuç bulunamadı.
              </p>
              <p className="text-xs text-gray-600 mt-1">
                Farklı bir sembol veya tür deneyin.
              </p>
            </div>
          )}

          {!loading && activeType !== 'COMMODITY' && displayed.length === 0 && allItems.length > 0 && (
            <div className="px-4 py-8 text-center">
              <p className="text-gray-400 text-sm">
                <span className="font-semibold">&quot;{query}&quot;</span> için sonuç bulunamadı.
              </p>
              <p className="text-xs text-gray-600 mt-1">
                Farklı bir sembol veya tür deneyin.
              </p>
            </div>
          )}

          {!loading && allItems.length === 0 && (
            <div className="px-4 py-8 text-center text-gray-500 text-sm">
              Veri yüklenemedi. Lütfen tekrar deneyin.
            </div>
          )}

          {!loading && activeType === 'COMMODITY' && commodityView && !commodityEmptyQuery && (
            <>
              {commodityView.sections.map(section => (
                <div key={section.key}>
                  {section.title && (
                    <div className="px-4 py-2 text-[11px] font-bold text-gray-500 uppercase tracking-wide bg-[#151925] border-b border-[#3a4155] sticky top-0 z-[1]">
                      {section.title}
                    </div>
                  )}
                  {section.items.map((item, i) => renderInstrumentRow(item, i, section.key))}
                </div>
              ))}
              {commodityView.showExpandButton && (
                <button
                  type="button"
                  onClick={() => setCommodityExpanded(true)}
                  className="w-full py-3 text-xs text-[#4a6cf7] hover:text-[#6b8cf7] transition-colors text-center border-t border-[#3a4155]"
                >
                  Diğer emtiaları göster ({commodityView.totalCount - commodityView.visibleCount} gizli)
                </button>
              )}
              {!commodityView.showExpandButton && commodityExpanded && !query.trim() && (
                <button
                  type="button"
                  onClick={() => setCommodityExpanded(false)}
                  className="w-full py-2.5 text-xs text-gray-500 hover:text-gray-300 transition-colors text-center border-t border-[#3a4155]"
                >
                  Daha az göster
                </button>
              )}
            </>
          )}

          {!loading && activeType !== 'COMMODITY' && displayed.map((item, i) => renderInstrumentRow(item, i, 'd'))}

          {/* Daha fazla yükle göstergesi */}
          {hasMore && !loading && activeType !== 'COMMODITY' && (
            <button
              type="button"
              onClick={loadMore}
              className="w-full py-3 text-xs text-[#4a6cf7] hover:text-[#6b8cf7] transition-colors text-center border-t border-[#3a4155]"
            >
              Daha fazla göster ({filtered.length - displayed.length} kaldı)
            </button>
          )}
        </div>

        {/* Alt link */}
        {seeAll && (
          <div className="px-6 pb-4 pt-1 shrink-0">
            <a
              href={seeAll.path}
              target="_blank"
              rel="noreferrer"
              className="text-xs text-[#4a6cf7] hover:underline flex items-center gap-1"
            >
              {seeAll.label} →
            </a>
          </div>
        )}
      </div>
    </div>
  );
}
