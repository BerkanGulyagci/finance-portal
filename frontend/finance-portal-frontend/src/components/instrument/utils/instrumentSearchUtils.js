// InstrumentSearchModal'dan çıkarılan SAF veri sabitleri + yardımcılar (veri-çekme, filtre, sıralama,
// emtia bölümleme). React/JSX yok. Davranış orijinaliyle birebir aynıdır (taşıma; mantık değişmedi).

import client from '../../../lib/http';
import { parseTrNumber } from '../../../utils/numberFormat';

export const ASSET_TYPES = [
  { value: 'STOCK',     label: 'Hisse',    placeholder: 'THYAO.IS, AAPL, GARAN...' },
  { value: 'CRYPTO',    label: 'Kripto',   placeholder: 'BTC, ETH, BNB...' },
  { value: 'FX',        label: 'Döviz',    placeholder: 'USD, EUR, GBP...' },
  { value: 'FUND',      label: 'Fon',      placeholder: 'AAL, SPY, QQQ...' },
  { value: 'FUTURE',    label: 'Vadeli',   placeholder: 'XAUTRYM26, F_THYAO...' },
  { value: 'GOLD',      label: 'Altın',    placeholder: 'GOLD, XAU, gram altın...' },
  { value: 'COMMODITY', label: 'Emtia',    placeholder: 'Gram Gümüş, WTI Ham Petrol, Bakır...' },
  { value: 'BOND',      label: 'Tahvil',  placeholder: 'TRD0707... / XS3123...' },
  { value: 'INDICATOR', label: 'Endeksler', placeholder: 'XU100, XBANK, XU030...' },
];

export const SEE_ALL_LINKS = {
  STOCK:     { label: 'Tüm hisseleri gör', path: '/market/stocks' },
  CRYPTO:    { label: 'Tüm kriptoları gör', path: '/market/crypto' },
  FX:        { label: 'Tüm dövizleri gör', path: '/market/fx' },
  FUND:      { label: 'Tüm fonları gör', path: '/market/tefas' },
  FUTURE:    { label: 'Tüm vadeli sözleşmeleri gör', path: '/market/futures' },
  GOLD:      { label: 'Altın fiyatlarını gör', path: '/market/gold' },
  COMMODITY: { label: 'Tüm emtiaları gör', path: '/market/commodities' },
  BOND:      { label: 'DİBS listesini gör', path: '/market/bonds' },
  INDICATOR: { label: 'Tüm endeksleri gör', path: '/market/stocks?view=indices' },
};

// Statik listeler — API desteklemeyen tipler için
export const STATIC_GOLD = [
  { symbol: 'GRAM',    name: 'Gram Altın' },
  { symbol: 'CEYREK',  name: 'Çeyrek Altın' },
  { symbol: 'YARIM',   name: 'Yarım Altın' },
  { symbol: 'ATA',     name: 'Ata Lira (Cumhuriyet)' },
  { symbol: 'TAM',     name: 'Tam Altın (Ziynet)' },
  { symbol: 'GOLD',    name: 'Ons Altın' },
  { symbol: '14AYAR',  name: '14 Ayar Bilezik' },
  { symbol: '22AYAR',  name: '22 Ayar Bilezik' },
];

// BondCategory enum → Türkçe kullanıcı etiketi (search modal'da satır altında gözükür)
export const BOND_CATEGORY_LABELS = {
  ZERO_COUPON_BILL:                    'Hazine Bonosu (Kuponsuz)',
  ZERO_COUPON_BOND:                    'Devlet Tahvili (Kuponsuz)',
  FIXED_COUPON_BOND:                   'Kuponlu Devlet Tahvili',
  PRINCIPAL_STRIP:                     'Ana Para Stripi',
  COUPON_STRIP:                        'Kupon Stripi',
  TLREF_INDEXED_BOND:                  'TLREF-Endeksli',
  INFLATION_INDEXED_BOND:              'TÜFE-Endeksli (Tam Bond)',
  INFLATION_PRINCIPAL_STRIP:           'TÜFE-Endeksli Ana Para Stripi',
  INFLATION_COUPON_STRIP:              'TÜFE-Endeksli Kupon Stripi',
  GOLD_INDEXED_BOND:                   'Altına Dayalı Senet',
  FX_DENOMINATED_BOND:                 'Yabancı Para Cinsli (EUR/USD)',
  LEASE_CERTIFICATE:                   'Kira Sertifikası',
  INFLATION_INDEXED_LEASE_CERTIFICATE: 'TÜFE-Endeksli Kira Sertifikası',
  GOLD_INDEXED_LEASE_CERTIFICATE:      'Altına Dayalı Kira Sertifikası',
  FX_LEASE_CERTIFICATE:                'Yabancı Para Cinsli Kira Sertifikası',
};

export const STATIC_BOND = [
  { symbol: 'TRD070727K10', name: 'Devlet Tahvili 2027', type: 'Devlet Tahvili' },
  { symbol: 'TRB170626T13', name: 'Devlet Tahvili 2026', type: 'Devlet Tahvili' },
  { symbol: 'TRT180237T13', name: 'Devlet Tahvili 2037', type: 'Devlet Tahvili' },
  { symbol: 'TRT210235T10', name: 'Devlet Tahvili 2035', type: 'Devlet Tahvili' },
  { symbol: 'TRT270934T18', name: 'Devlet Tahvili 2034', type: 'Devlet Tahvili' },
];

// Kıymetli madenler — BIST kategorileri (SilverPage/PreciousMetals ile aynı yapı)
// symbol formatı: METAL:CATEGORY (handleSelect'te parse edilir)
export const PRECIOUS_METALS_BIST = [
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

/** Portföy modalında yalnızca TL cinsinden BIST kıymetli madenler */
export const PRECIOUS_METALS_TRY = PRECIOUS_METALS_BIST.filter(
  (p) => p.category === 'GRAM_TRY' || p.category === 'KG_TRY',
);

/** İlk görünüm: gram ₺ (gümüş, platin, paladyum) */
export const FEATURED_PRECIOUS_SYMBOLS = new Set(
  PRECIOUS_METALS_TRY.filter((p) => p.category === 'GRAM_TRY').map((p) => p.symbol),
);

// Yahoo Finance emtia sembolleri — sadece commodities sayfasındakiler, Türkçe isimli
export const YAHOO_COMMODITIES = [
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
export const FX_NAMES = {
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

export const PAGE_SIZE = 15;

// Tür bazlı oturum önbelleği — modal her açıldığında tüm listeyi yeniden çekmesin (hız).
export const TYPE_CACHE = new Map();

/** BIST altın spot — sembole göre TL referans fiyatı. */
export function pickGoldSpotPrice(symbol, g) {
  if (!g || typeof g !== 'object') return null;
  const s = (symbol || '').toUpperCase();
  const pick = (v) => {
    if (v == null || v === '') return null;
    if (typeof v === 'number' && Number.isFinite(v) && v > 0) return v;
    const n = parseTrNumber(v);
    return n != null && n > 0 ? n : null;
  };
  switch (s) {
    case 'GOLD':
      return pick(g.onsTry) ?? pick(g.priceTl) ?? pick(g.gramGoldTry);
    case 'GRAM':
      return pick(g.gramGoldTry) ?? pick(g.gramTl) ?? pick(g.officialPureGoldGramTry);
    case 'CEYREK':
      return pick(g.quarterGoldTry) ?? pick(g.ceyrekTl);
    case 'YARIM':
      return pick(g.halfGoldTry) ?? pick(g.yarimTl);
    case 'TAM':
      return pick(g.ziynetGoldTry) ?? pick(g.tamTl);
    case '14AYAR':
    case 'AYAR14':
      return pick(g.fourteenKBraceletTry) ?? pick(g.ayar14Tl);
    case '22AYAR':
    case 'AYAR22':
      return pick(g.twentyTwoKBraceletTry) ?? pick(g.ayar22Tl);
    case 'ZIYNET':
      return pick(g.ziynetGoldTry) ?? pick(g.tamTl);
    case 'CUMHUR':
      return pick(g.republicGoldTry) ?? pick(g.cumhuriyetTl);
    case 'ATA':
      return pick(g.republicGoldTry) ?? pick(g.cumhuriyetTl);
    default:
      return pick(g.gramGoldTry) ?? pick(g.officialPureGoldGramTry);
  }
}

// ── Veri çekme fonksiyonları ──────────────────────────────────────────────────

export async function fetchAll(type, onPartial) {
  try {
    if (type === 'STOCK') {
      // Tüm hisseleri çek — büyük sayfa boyutu (backend max'ı kabul eder) + TÜM sayfalar (kapama yok)
      try {
        const mapStock = s => ({ symbol: s.symbol, name: s.name ?? s.symbol });
        const firstRes = await client.get('/api/v1/market/stocks', { params: { page: 0, size: 200 } });
        const data0 = firstRes.data?.data;
        const size = data0?.size || 200;
        const totalPages = data0?.totalPages ?? 1;
        const firstMapped = (data0?.content ?? []).map(mapStock).filter(s => s.symbol);
        // İlk sayfayı hemen göster — kullanıcı tüm sayfalar bitene kadar beklemesin.
        onPartial?.(firstMapped);
        const results = [...firstMapped];
        if (totalPages > 1) {
          const rest = await Promise.all(
            Array.from({ length: totalPages - 1 }, (_, i) =>
              client.get('/api/v1/market/stocks', { params: { page: i + 1, size } })
            )
          );
          rest.forEach(r => results.push(...(r.data?.data?.content ?? []).map(mapStock).filter(s => s.symbol)));
        }
        return results;
      } catch {
        return [];
      }
    }

    if (type === 'CRYPTO') {
      // Top ~1000 coin (cache'li) — top-250 dışındaki coinler de aranabilsin
      const { data } = await client.get('/api/v1/market/crypto/all');
      return (data.data ?? []).map(c => ({
        symbol: c.symbol?.toUpperCase() ?? '',
        name: c.name ?? c.symbol ?? '',
        id: c.id,
        image: c.image ?? null,
      }));
    }

    if (type === 'FX') {
      const { data } = await client.get('/api/v1/market/fx/tcmb/latest');
      return (data.data?.rates ?? []).map(r => ({
        symbol: r.symbol,
        name: r.name ?? FX_NAMES[r.symbol] ?? r.symbol,
      }));
    }

    if (type === 'FUND') {
      // 4 fon kaynağını paralel çek: TEFAS, BES, OKS, Osmanlı
      const [tefasRes, besRes, oksRes, osmanlıRes] = await Promise.allSettled([
        client.get('/api/v1/market/funds/tefas/all'),
        client.get('/api/v1/market/funds/bes/all'),
        client.get('/api/v1/market/funds/oks/all'),
        client.get('/api/v1/market/funds/osmanli/bulletin'),
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
        const { data } = await client.get('/api/v1/market/futures/viop/contracts');
        const contracts = data.data ?? [];
        if (contracts.length > 0) {
          return contracts.map((c) => {
            const fullName = (c.name ?? c.contractName ?? '').trim();
            const shortLabel =
              fullName.includes(' (') ? fullName.split(' (')[0].trim() : fullName;
            return {
              symbol: fullName,
              name: shortLabel || fullName,
              lastPrice: c.lastPrice,
              settlementPrice: c.settlementPrice,
            };
          }).filter((c) => c.symbol);
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
      return [...PRECIOUS_METALS_TRY, ...YAHOO_COMMODITIES];
    }

    if (type === 'BOND') {
      // DİBS (EVDS) + Eurobond (Hazine dış borç). Aynı sekmede ikisi de aranır/eklenir; assetType=BOND.
      const mapBond = b => ({
        symbol: b.instrumentCode ?? '',
        name: b.instrumentCode ?? '',
        type: b.type ?? null,
        category: b.category ?? null,           // BondCategory (Tier 2/3 modal uyarıları için)
        currency: b.currency ?? null,
        cbrtCode: b.cbrtCode ?? null,
        issueDate: b.issueDate ?? null,        // ihraç tarihi — modalda ihraç-öncesi alım engeli için
        maturityDate: b.maturityDate ?? null,
        couponRate: b.couponRate != null ? Number(b.couponRate) : null,
        indicatorValue: b.indicatorValue != null ? Number(b.indicatorValue) : null,
      });
      let evds = [];
      try {
        const bondParams = (p, s) => ({ page: p, size: s, sortBy: 'maturityDate', sortDir: 'asc' });
        const firstRes = await client.get('/api/v1/market/bonds/evds', { params: bondParams(0, 100) });
        const d0 = firstRes.data?.data;
        const size = d0?.size || 100;
        const totalPages = d0?.totalPages ?? 1;
        const firstMapped = (d0?.items ?? []).map(mapBond).filter(b => b.symbol);
        // İlk DİBS sayfasını hemen göster — 11 sayfa + eurobondlar bitene kadar beklenmesin.
        onPartial?.(firstMapped);
        evds = [...firstMapped];
        if (totalPages > 1) {
          const rest = await Promise.all(
            Array.from({ length: totalPages - 1 }, (_, i) =>
              client.get('/api/v1/market/bonds/evds', { params: bondParams(i + 1, size) }),
            ),
          );
          rest.forEach(r => evds.push(...(r.data?.data?.items ?? []).map(mapBond).filter(b => b.symbol)));
        }
      } catch { /* fallback */ }

      let euro = [];
      try {
        // tradeable=true → BI verisi olmayan eurobondlar listeden hariç tutulur
        // (fiyat yok → alış/satış K/Z hesaplanamaz, kullanıcıyı yanıltmasın).
        const r = await client.get('/api/v1/market/bonds/global?tradeable=true');
        euro = (r.data?.data ?? []).map(b => ({
          symbol: b.isin ?? '',
          name: b.name || b.issuer || b.isin || '',
          type: 'Eurobond',
          subType: 'EUROBOND',
          indicatorValue: b.lastPrice != null ? Number(b.lastPrice) : null,
        })).filter(b => b.symbol);
      } catch { /* eurobond yoksa yalnız EVDS */ }

      const merged = [...evds, ...euro];
      return merged.length > 0 ? merged : STATIC_BOND;
    }

    if (type === 'INDICATOR') {
      // BIST endeksleri — sembol = kod (XBANK). Karşılaştırmada assetType='INDICATOR' ile çizilir
      // (backend price-history endeks kodlarını hisse grafik yoluna yönlendirir). Alım-satıma EKLENMEZ.
      const { data } = await client.get('/api/v1/market/indices');
      return (data.data ?? []).map(idx => ({ symbol: idx.code, name: idx.name, category: idx.category }));
    }
  } catch {
    // hata
  }
  return [];
}

export function normalize(str) {
  // Turkce locale ile kucult: "YIGIT.IS" -> "yigit.is" noktasiz. Once toLowerCase()
  // cagirmak buyuk I'yi noktali i yapip aramayi bozuyordu.
  return String(str ?? '').toLocaleLowerCase('tr');
}

export function filterItems(items, query) {
  if (!query.trim()) return items;
  const q = normalize(query.trim());
  return items.filter(item =>
    normalize(item.symbol).includes(q) ||
    (item.name && normalize(item.name).includes(q))
  );
}

export function preciousSortKey(symbol) {
  if (!symbol?.includes(':')) return 999;
  const [metal, cat] = symbol.split(':');
  const mo = { SILVER: 0, PLATINUM: 1, PALLADIUM: 2 }[metal] ?? 5;
  const co = { GRAM_TRY: 0, KG_TRY: 1 }[cat] ?? 9;
  return mo * 10 + co;
}

export function sortPreciousByTier(items) {
  return [...items].sort((a, b) => preciousSortKey(a.symbol) - preciousSortKey(b.symbol));
}

/**
 * Emtia: öne çıkan gram ₺ madenler; genişletince Kg ₺ + Yahoo (TRY).
 */
export function buildCommoditySections(allItems, query, expanded) {
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
