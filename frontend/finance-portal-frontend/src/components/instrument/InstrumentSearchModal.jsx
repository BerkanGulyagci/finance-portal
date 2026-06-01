import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { Search, X, Plus } from 'lucide-react';
import client from '../../lib/http';
import { parseTrNumber } from '../../utils/numberFormat';
import {
  pickCommoditySpotPriceTry,
  pickCommoditySpotPriceUsd,
  isYahooCommoditySymbol,
} from '../../utils/commodityPriceUtils';
import { pickSilverGramCloseTry } from '../../utils/silverPriceUtils';
import { useTranslation } from '../../context/LanguageContext';

/**
 * Yeniden kullanılabilir enstrüman arama modal'ı.
 * - Tip seçilince hemen varsayılan liste gösterilir
 * - Arama case-insensitive, hem sembol hem isim üzerinden
 * - Sonsuz kaydırma: aşağı kaydırdıkça daha fazla sonuç yüklenir
 * - FON → Rasyonet TEFAS fonları
 * - VADELİ → VİOP sözleşmeleri
 * - EMTİA → BİST kıymetli maden (yalnızca ₺) + Yahoo emtialar (TCMB kuru ile ₺)
 */

const ASSET_TYPES = [
  { value: 'STOCK',     label: 'Hisse',    placeholder: 'THYAO.IS, AAPL, GARAN...' },
  { value: 'CRYPTO',    label: 'Kripto',   placeholder: 'BTC, ETH, BNB...' },
  { value: 'FX',        label: 'Döviz',    placeholder: 'USD, EUR, GBP...' },
  { value: 'FUND',      label: 'Fon',      placeholder: 'AAL, SPY, QQQ...' },
  { value: 'FUTURE',    label: 'Vadeli',   placeholder: 'XAUTRYM26, F_THYAO...' },
  { value: 'GOLD',      label: 'Altın',    placeholder: 'GOLD, XAU, gram altın...' },
  { value: 'COMMODITY', label: 'Emtia',    placeholder: 'Gram Gümüş, WTI Ham Petrol, Bakır...' },
  { value: 'BOND',      label: 'Tahvil',  placeholder: 'TRD0707... / XS3123...' },
];

const SEE_ALL_LINKS = {
  STOCK:     { label: 'Tüm hisseleri gör', path: '/market/stocks' },
  CRYPTO:    { label: 'Tüm kriptoları gör', path: '/market/crypto' },
  FX:        { label: 'Tüm dövizleri gör', path: '/market/fx' },
  FUND:      { label: 'Tüm fonları gör', path: '/market/tefas' },
  FUTURE:    { label: 'Tüm vadeli sözleşmeleri gör', path: '/market/futures' },
  GOLD:      { label: 'Altın fiyatlarını gör', path: '/market/gold' },
  COMMODITY: { label: 'Tüm emtiaları gör', path: '/market/commodities' },
  BOND:      { label: 'DİBS listesini gör', path: '/market/bonds' },
};

// Statik listeler — API desteklemeyen tipler için
const STATIC_GOLD = [
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
const BOND_CATEGORY_LABELS = {
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

const STATIC_BOND = [
  { symbol: 'TRD070727K10', name: 'Devlet Tahvili 2027', type: 'Devlet Tahvili' },
  { symbol: 'TRB170626T13', name: 'Devlet Tahvili 2026', type: 'Devlet Tahvili' },
  { symbol: 'TRT180237T13', name: 'Devlet Tahvili 2037', type: 'Devlet Tahvili' },
  { symbol: 'TRT210235T10', name: 'Devlet Tahvili 2035', type: 'Devlet Tahvili' },
  { symbol: 'TRT270934T18', name: 'Devlet Tahvili 2034', type: 'Devlet Tahvili' },
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

/** Portföy modalında yalnızca TL cinsinden BIST kıymetli madenler */
const PRECIOUS_METALS_TRY = PRECIOUS_METALS_BIST.filter(
  (p) => p.category === 'GRAM_TRY' || p.category === 'KG_TRY',
);

/** İlk görünüm: gram ₺ (gümüş, platin, paladyum) */
const FEATURED_PRECIOUS_SYMBOLS = new Set(
  PRECIOUS_METALS_TRY.filter((p) => p.category === 'GRAM_TRY').map((p) => p.symbol),
);

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
  EGP: 'Mısır Poundu', ILS: 'İsrail Şekeli', IRR: 'İran Riyali',
  IQD: 'Irak Dinarı', SYP: 'Suriye Poundu', LBP: 'Lübnan Poundu',
  YER: 'Yemen Riyali', AFN: 'Afgan Afganisi', AZN: 'Azerbaycan Manatı',
  GEL: 'Gürcistan Larisi', KZT: 'Kazakistan Tengesi', UZS: 'Özbek Somu',
  UAH: 'Ukrayna Grivnası', BYR: 'Belarus Rublesi', MDL: 'Moldova Leyi',
  ALL: 'Arnavut Leki', BAM: 'Bosna Marki', RSD: 'Sırp Dinarı',
};

const PAGE_SIZE = 15;

// Tür bazlı oturum önbelleği — modal her açıldığında tüm listeyi yeniden çekmesin (hız).
const TYPE_CACHE = new Map();

/** BIST altın spot — sembole göre TL referans fiyatı. */
function pickGoldSpotPrice(symbol, g) {
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

async function fetchAll(type) {
  try {
    if (type === 'STOCK') {
      // Tüm hisseleri çek — büyük sayfa boyutu (backend max'ı kabul eder) + TÜM sayfalar (kapama yok)
      try {
        const firstRes = await client.get('/api/market/stocks', { params: { page: 0, size: 200 } });
        const data0 = firstRes.data?.data;
        const size = data0?.size || 200;
        const totalPages = data0?.totalPages ?? 1;
        const results = [...(data0?.content ?? [])];
        if (totalPages > 1) {
          const rest = await Promise.all(
            Array.from({ length: totalPages - 1 }, (_, i) =>
              client.get('/api/market/stocks', { params: { page: i + 1, size } })
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
      // Top ~1000 coin (cache'li) — top-250 dışındaki coinler de aranabilsin
      const { data } = await client.get('/api/market/crypto/all');
      return (data.data ?? []).map(c => ({
        symbol: c.symbol?.toUpperCase() ?? '',
        name: c.name ?? c.symbol ?? '',
        id: c.id,
        image: c.image ?? null,
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
      let evds = [];
      try {
        const bondParams = (p, s) => ({ page: p, size: s, sortBy: 'maturityDate', sortDir: 'asc' });
        const firstRes = await client.get('/api/market/bonds/evds', { params: bondParams(0, 100) });
        const d0 = firstRes.data?.data;
        const size = d0?.size || 100;
        const totalPages = d0?.totalPages ?? 1;
        const items = [...(d0?.items ?? [])];
        if (totalPages > 1) {
          const rest = await Promise.all(
            Array.from({ length: totalPages - 1 }, (_, i) =>
              client.get('/api/market/bonds/evds', { params: bondParams(i + 1, size) }),
            ),
          );
          rest.forEach(r => items.push(...(r.data?.data?.items ?? [])));
        }
        evds = items.map(b => ({
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
        })).filter(b => b.symbol);
      } catch { /* fallback */ }

      let euro = [];
      try {
        // tradeable=true → BI verisi olmayan eurobondlar listeden hariç tutulur
        // (fiyat yok → alış/satış K/Z hesaplanamaz, kullanıcıyı yanıltmasın).
        const r = await client.get('/api/market/bonds/global?tradeable=true');
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
  } catch {
    // hata
  }
  return [];
}

function normalize(str) {
  // Turkce locale ile kucult: "YIGIT.IS" -> "yigit.is" noktasiz. Once toLowerCase()
  // cagirmak buyuk I'yi noktali i yapip aramayi bozuyordu.
  return String(str ?? '').toLocaleLowerCase('tr');
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
  const co = { GRAM_TRY: 0, KG_TRY: 1 }[cat] ?? 9;
  return mo * 10 + co;
}

function sortPreciousByTier(items) {
  return [...items].sort((a, b) => preciousSortKey(a.symbol) - preciousSortKey(b.symbol));
}

/**
 * Emtia: öne çıkan gram ₺ madenler; genişletince Kg ₺ + Yahoo (TRY).
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

export default function InstrumentSearchModal({ portfolioName, onSelect, onClose, initialType = 'STOCK' }) {
  const { t } = useTranslation();
  const [activeType, setActiveType] = useState(initialType);
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
  const selectSeqRef = useRef(0);

  // Tüm veriyi yükle
  const loadAll = useCallback(async (type) => {
    setLoading(true);
    setDisplayed([]);
    setPage(1);
    try {
      let items = TYPE_CACHE.get(type);
      if (!items) { items = await fetchAll(type); TYPE_CACHE.set(type, items); }
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

  // İlk yükleme — modal hangi tür için açıldıysa o türle başla
  useEffect(() => { loadAll(initialType); }, [loadAll, initialType]);

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
    const seq = ++selectSeqRef.current;
    setPriceLoading(true);
    try {
      let price = null;
      let commoditySpot = null;
      let fxBuy = null;   // TCMB alış kuru  (kullanıcı satarken alacağı birim fiyat)
      let fxSell = null;  // TCMB satış kuru (kullanıcı alırken ödeyeceği birim fiyat)
      try {
        if (activeType === 'STOCK') {
          const { data } = await client.get(`/api/market/stocks/${encodeURIComponent(item.symbol)}`);
          price = data.data?.summary?.price ?? null;
        } else if (activeType === 'CRYPTO') {
          const { data } = await client.get('/api/market/crypto/all');
          const list = data.data ?? [];
          const coin = (item.id && list.find(c => c.id === item.id))
            || list.find(c => c.symbol?.toLowerCase() === item.symbol.toLowerCase());
          price = coin?.currentPrice ?? null;
        } else if (activeType === 'FX') {
          const { data } = await client.get('/api/market/fx/tcmb/latest');
          const rate = (data.data?.rates ?? []).find(r => r.symbol === item.symbol.toUpperCase());
          if (rate) {
            const unit = rate.unit && rate.unit > 1 ? rate.unit : 1;
            // Olası alan isimleri için geniş fallback — TCMB için buy/sell yeterli,
            // ileride başka FX kaynağı eklenirse de uyumlu kalır
            const rawSell = rate.sell ?? rate.selling ?? rate.forexSelling ?? rate.sellRate
              ?? rate.ask ?? rate.currencySelling ?? rate.currentSellPrice ?? null;
            const rawBuy  = rate.buy  ?? rate.buying  ?? rate.forexBuying  ?? rate.buyRate
              ?? rate.bid ?? rate.currencyBuying  ?? rate.currentBuyPrice  ?? null;
            fxSell = rawSell != null ? rawSell / unit : null;
            fxBuy  = rawBuy  != null ? rawBuy  / unit : null;
            // BUY varsayılan → kurum dövizi kullanıcıya satar → forexSelling kullanılır
            price  = fxSell;
          }
        } else if (activeType === 'FUTURE') {
          const sym = (item.symbol ?? '').trim();
          const yahooStyle = /^[A-Z0-9.=]{1,15}$/i.test(sym);
          try {
            if (yahooStyle) {
              const { data } = await client.get('/api/commodities/spot', { params: { symbol: sym } });
              commoditySpot = data.data ?? null;
              price = pickCommoditySpotPriceUsd(commoditySpot);
            } else if (sym) {
              const fromList =
                parseTrNumber(item.lastPrice) ??
                parseTrNumber(item.settlementPrice);
              if (fromList != null) {
                price = fromList;
              } else {
                const { data } = await client.get('/api/market/futures/viop', {
                  params: { name: sym },
                });
                const d = data.data;
                price =
                  parseTrNumber(d?.lastPrice) ??
                  parseTrNumber(d?.settlementPrice) ??
                  parseTrNumber(d?.prevSettlementPrice) ??
                  null;
              }
            }
          } catch { /* fiyat bulunamazsa null */ }
        } else if (activeType === 'GOLD') {
          try {
            const { data } = await client.get('/api/gold/spot');
            price = pickGoldSpotPrice(item.symbol, data.data);
          } catch { /* fiyat bulunamazsa null */ }
        } else if (activeType === 'COMMODITY') {
          try {
            if (item.symbol.includes(':')) {
              const [metal, cat] = item.symbol.split(':');
              const metalKey = metal.toLowerCase();
              let spotData = null;
              if (metalKey === 'silver') {
                const [spotRes, histRes] = await Promise.all([
                  client.get('/api/silver/spot'),
                  cat === 'GRAM_TRY'
                    ? client.get('/api/silver/history', { params: { range: '1W', currency: 'TRY' } })
                    : Promise.resolve({ data: { data: null } }),
                ]);
                spotData = spotRes.data?.data;
                if (cat === 'GRAM_TRY') {
                  price = pickSilverGramCloseTry(spotData, histRes.data?.data?.points);
                }
              } else {
                const { data } = await client.get(`/api/precious-metals/${metalKey}/spot`);
                spotData = data.data;
              }
              if (spotData && price == null) {
                if (cat === 'GRAM_TRY') {
                  price = pickSilverGramCloseTry(spotData, null);
                } else if (cat === 'KG_TRY') {
                  price = spotData.closeTryKg ?? spotData.weightedAverageTryKg ?? spotData.tryKg ?? null;
                }
              }
            } else {
              const { data } = await client.get('/api/commodities/spot', { params: { symbol: item.symbol } });
              commoditySpot = data.data ?? null;
              price = pickCommoditySpotPriceTry(commoditySpot);
            }
          } catch { /* fiyat bulunamazsa null */ }
        } else if (activeType === 'FUND') {
          try {
            const sourceCode = item.category === 'BES' ? 'TPF' : item.category === 'OKS' ? 'TAF' : 'TMF';
            const { data } = await client.get(`/api/market/funds/tefas/${encodeURIComponent(item.symbol)}`, {
              params: { sourceCode },
            });
            price = data.data?.price ?? null;
          } catch { /* fiyat bulunamazsa null */ }
        } else if (activeType === 'BOND') {
          try {
            const raw =
              item.indicatorValue ??
              item.evdsValue ??
              item.price;
            if (raw != null && raw !== '') {
              const n = typeof raw === 'number' ? raw : parseFloat(String(raw).replace(/\s/g, '').replace(',', '.'));
              if (Number.isFinite(n) && n > 0) price = n;
            }
            if ((price == null || !Number.isFinite(price)) && item.symbol) {
              const { data } = await client.get(`/api/market/bonds/evds/${encodeURIComponent(item.symbol)}`);
              const d = data?.data;
              if (d?.indicatorValue != null) {
                const n = Number(d.indicatorValue);
                if (Number.isFinite(n) && n > 0) price = n;
              }
            }
          } catch { /* fiyat bulunamazsa null */ }
        }
      } catch { /* fiyat bulunamazsa null */ }

      if (seq !== selectSeqRef.current) return;

      const currency =
        activeType === 'GOLD' || activeType === 'COMMODITY' || activeType === 'BOND'
          ? 'TRY'
          : activeType === 'FX'
            ? item.symbol
            : activeType === 'FUTURE' && isYahooCommoditySymbol(item.symbol)
              ? 'USD'
              : undefined;

      onSelect({
        symbol: item.symbol,
        assetType: activeType,
        name: item.name ?? item.symbol,
        price,
        currency,
        fxBuy,
        fxSell,
        category: item.category,
        ...(item.subType ? { subType: item.subType } : {}),
        // BOND için kategori-bazlı modal uyarıları + manuel kupon ekleme referansı
        ...(activeType === 'BOND' ? {
          cbrtCode: item.cbrtCode,
          maturityDate: item.maturityDate,
          couponRate: item.couponRate,
        } : {}),
        commoditySpot: activeType === 'COMMODITY' && isYahooCommoditySymbol(item.symbol)
          ? commoditySpot
          : null,
      });
    } finally {
      if (seq === selectSeqRef.current) setPriceLoading(false);
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
    const isStockOrFund = activeType === 'STOCK' || activeType === 'FUND';
    const primary = isStockOrFund ? item.symbol : (item.name ?? item.symbol);
    const secondary = isStockOrFund
      ? (item.name && item.name !== item.symbol ? item.name : null)
      : (!String(item.symbol ?? '').includes(':') && item.symbol !== item.name ? item.symbol : null);
    const typeLabel = activeType === 'BOND' ? t('dibs') : (currentType?.label ? t(currentType.label).toLowerCase() : '');
    return (
      <button
        key={`${keyPrefix}-${item.symbol}-${i}`}
        onClick={() => handleSelect(item)}
        disabled={priceLoading}
        className="group w-full flex items-center justify-between p-4 text-left border-b border-[#e2e1eb] last:border-b-0 hover:bg-[#f3f3fc] cursor-pointer transition-colors disabled:opacity-50"
      >
        <div className="flex flex-col min-w-0 flex-1">
          <span className="font-bold text-[#1a1b22] truncate">{primary}</span>
          {secondary && <span className="text-[#434653] text-sm mt-0.5 truncate">{secondary}</span>}
          {(item.category || item.metal) && (
            <span className="flex items-center gap-1.5 mt-1">
              {item.category && (
                <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                  item.category === 'BES'     ? 'bg-emerald-50 text-emerald-700' :
                  item.category === 'OKS'     ? 'bg-purple-50 text-purple-700' :
                  item.category === 'Osmanlı' ? 'bg-orange-50 text-orange-700' :
                  activeType === 'BOND'       ? 'bg-indigo-50 text-indigo-700' :
                                                'bg-blue-50 text-blue-700'
                }`}>
                  {activeType === 'BOND'
                    ? (BOND_CATEGORY_LABELS[item.category] ? t(BOND_CATEGORY_LABELS[item.category]) : item.category)
                    : item.category}
                </span>
              )}
              {item.metal && (
                <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-50 text-amber-700">
                  {t('BİST')}
                </span>
              )}
            </span>
          )}
        </div>
        <div className="flex items-center gap-3 ml-3 shrink-0">
          <span className="text-[10px] font-bold tracking-wider uppercase bg-[#e8e7f1] text-[#434653] px-2 py-1 rounded">
            {typeLabel}
          </span>
          <span className="text-[#c4c5d5] group-hover:text-[#093eaa] transition-colors p-1 rounded-full group-hover:bg-[#d0e1fb]">
            <Plus className="w-4 h-4" />
          </span>
        </div>
      </button>
    );
  }

  const commodityEmptyQuery =
    activeType === 'COMMODITY' &&
    commodityView &&
    query.trim() &&
    commodityView.sections.every(s => !s.items.length);

  return createPortal((
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1a1b22]/30">
      <div
        className="bg-white rounded-2xl shadow-2xl border border-[#e2e1eb] w-full max-w-2xl overflow-hidden flex flex-col"
        style={{ maxHeight: '85vh' }}
        role="dialog"
        aria-modal="true"
      >

        {/* Header */}
        <div className="px-4 pt-4 pb-3 sm:px-6 sm:pt-6 sm:pb-4 border-b border-[#e2e1eb] flex justify-between items-start shrink-0">
          <div>
            <h2 className="text-xl sm:text-2xl font-bold text-[#1a1b22] mb-1">{t('Enstrüman Seç')}</h2>
            <p className="text-sm text-[#434653]">
              {portfolioName
                ? t('{name} portföyüne ekle', { name: portfolioName })
                : t('Portföyünüze yeni bir varlık ekleyin')}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label={t('Kapat')}
            className="text-[#434653] hover:text-[#1a1b22] transition-colors rounded-full p-2 sm:p-1 hover:bg-[#f3f3fc]"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tip sekmeleri — alt çizgili (M3) */}
        <div className="px-3 pt-2 sm:px-6 bg-[#f3f3fc]/60 border-b border-[#e2e1eb] flex gap-1 overflow-x-auto shrink-0 [&::-webkit-scrollbar]:hidden">
          {ASSET_TYPES.map(at => (
            <button
              key={at.value}
              type="button"
              onClick={() => handleTypeChange(at.value)}
              className={`px-4 py-2 border-b-2 text-sm whitespace-nowrap transition-colors ${
                activeType === at.value
                  ? 'border-[#093eaa] text-[#093eaa] font-bold'
                  : 'border-transparent text-[#434653] hover:text-[#1a1b22]'
              }`}
            >
              {t(at.label)}
            </button>
          ))}
        </div>

        {/* Arama */}
        <div className="p-4 pb-3 sm:p-6 sm:pb-4 border-b border-[#e2e1eb] shrink-0">
          <div className="relative flex items-center w-full h-12 rounded-lg bg-[#f3f3fc] border border-[#c4c5d5] overflow-hidden focus-within:border-[#093eaa] focus-within:ring-1 focus-within:ring-[#093eaa] transition-all">
            <Search className="w-5 h-5 text-[#747684] ml-4 mr-2 shrink-0" />
            <input
              ref={searchRef}
              type="text"
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder={currentType?.placeholder ? t(currentType.placeholder) : t('Sembol veya enstrüman ara...')}
              autoFocus
              className="w-full h-full bg-transparent border-none focus:ring-0 text-[#1a1b22] text-sm placeholder-[#747684] outline-none"
            />
          </div>
          {/* Sonuç sayısı */}
          {!loading && allItems.length > 0 && (
            <span className="block mt-2 text-xs text-[#747684]">
              {activeType === 'COMMODITY' && commodityView ? (
                query.trim()
                  ? t('{count} sonuç · "{query}" için', { count: commodityView.totalCount, query })
                  : commodityExpanded
                    ? t('{count} enstrüman', { count: commodityView.totalCount })
                    : t('{visible} öne çıkan · {total} toplam', { visible: commodityView.visibleCount, total: commodityView.totalCount })
              ) : query.trim()
                ? t('{count} sonuç · "{query}" için', { count: filtered.length, query })
                : t('{count} enstrüman', { count: allItems.length })}
            </span>
          )}
        </div>

        {/* Sonuçlar — kaydırılabilir */}
        <div
          ref={listRef}
          onScroll={handleScroll}
          className="overflow-y-auto flex-1 bg-white"
          style={{ minHeight: 120 }}
        >
          {loading && (
            <div className="flex items-center justify-center py-10 gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          )}

          {!loading && ((activeType === 'COMMODITY' && commodityView && commodityEmptyQuery)
            || (activeType !== 'COMMODITY' && displayed.length === 0 && allItems.length > 0)) && (
            <div className="px-4 py-10 text-center">
              <p className="text-[#434653] text-sm">
                <span className="font-semibold">&quot;{query}&quot;</span> {t('için sonuç bulunamadı.')}
              </p>
              <p className="text-xs text-[#747684] mt-1">
                {t('Farklı bir sembol veya tür deneyin.')}
              </p>
            </div>
          )}

          {!loading && allItems.length === 0 && (
            <div className="px-4 py-10 text-center text-[#747684] text-sm">
              {t('Veri yüklenemedi. Lütfen tekrar deneyin.')}
            </div>
          )}

          {!loading && activeType === 'COMMODITY' && commodityView && !commodityEmptyQuery && (
            <>
              {commodityView.sections.map(section => (
                <div key={section.key}>
                  {section.title && (
                    <div className="px-4 py-2 text-[11px] font-bold text-[#434653] uppercase tracking-wide bg-[#f3f3fc] border-b border-[#e2e1eb] sticky top-0 z-[1]">
                      {t(section.title)}
                    </div>
                  )}
                  {section.items.map((item, i) => renderInstrumentRow(item, i, section.key))}
                </div>
              ))}
              {commodityView.showExpandButton && (
                <button
                  type="button"
                  onClick={() => setCommodityExpanded(true)}
                  className="w-full py-3 text-xs font-medium text-[#093eaa] hover:underline transition-colors text-center border-t border-[#e2e1eb]"
                >
                  {t('Diğer emtiaları göster ({count} gizli)', { count: commodityView.totalCount - commodityView.visibleCount })}
                </button>
              )}
              {!commodityView.showExpandButton && commodityExpanded && !query.trim() && (
                <button
                  type="button"
                  onClick={() => setCommodityExpanded(false)}
                  className="w-full py-2.5 text-xs text-[#747684] hover:text-[#434653] transition-colors text-center border-t border-[#e2e1eb]"
                >
                  {t('Daha az göster')}
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
              className="w-full py-3 text-xs font-medium text-[#093eaa] hover:underline transition-colors text-center border-t border-[#e2e1eb]"
            >
              {t('Daha fazla göster ({count} kaldı)', { count: filtered.length - displayed.length })}
            </button>
          )}
        </div>

        {/* Alt link */}
        {seeAll && (
          <div className="p-3 sm:p-4 bg-white border-t border-[#e2e1eb] flex justify-center shrink-0">
            <a
              href={seeAll.path}
              target="_blank"
              rel="noreferrer"
              className="text-sm font-medium text-[#093eaa] hover:underline flex items-center gap-1"
            >
              {t(seeAll.label)} →
            </a>
          </div>
        )}
      </div>
    </div>
  ), document.body);
}
