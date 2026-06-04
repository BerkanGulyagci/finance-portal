import { describe, it, expect, beforeEach, vi } from 'vitest';

// '../../../../lib/http' default export'u (axios client) MOCK'lanır — gerçek ağ isteği atılmasın.
// numberFormat SAF gerçek kullanılır (parseTrNumber Türk sayı formatını parse eder).
vi.mock('../../../../lib/http', () => ({
  default: { get: vi.fn() },
}));

import client from '../../../../lib/http';
import {
  ASSET_TYPES,
  SEE_ALL_LINKS,
  STATIC_GOLD,
  BOND_CATEGORY_LABELS,
  STATIC_BOND,
  PRECIOUS_METALS_BIST,
  PRECIOUS_METALS_TRY,
  FEATURED_PRECIOUS_SYMBOLS,
  YAHOO_COMMODITIES,
  FX_NAMES,
  PAGE_SIZE,
  TYPE_CACHE,
  pickGoldSpotPrice,
  fetchAll,
  normalize,
  filterItems,
  preciousSortKey,
  sortPreciousByTier,
  buildCommoditySections,
} from '../instrumentSearchUtils';

// ── Sabitler ──────────────────────────────────────────────────────────────────

describe('Sabit listeler/haritalar', () => {
  it('ASSET_TYPES her tip için value/label/placeholder taşır', () => {
    expect(Array.isArray(ASSET_TYPES)).toBe(true);
    expect(ASSET_TYPES).toHaveLength(9);
    ASSET_TYPES.forEach((t) => {
      expect(typeof t.value).toBe('string');
      expect(typeof t.label).toBe('string');
      expect(typeof t.placeholder).toBe('string');
    });
    expect(ASSET_TYPES.map((t) => t.value)).toContain('STOCK');
    expect(ASSET_TYPES.map((t) => t.value)).toContain('INDICATOR');
  });

  it('SEE_ALL_LINKS her ASSET_TYPE değeri için label+path içerir', () => {
    ASSET_TYPES.forEach((t) => {
      expect(SEE_ALL_LINKS[t.value]).toBeDefined();
      expect(typeof SEE_ALL_LINKS[t.value].label).toBe('string');
      expect(SEE_ALL_LINKS[t.value].path.startsWith('/market')).toBe(true);
    });
  });

  it('STATIC_GOLD ve STATIC_BOND fallback listeleri symbol/name taşır', () => {
    expect(STATIC_GOLD.length).toBeGreaterThan(0);
    STATIC_GOLD.forEach((g) => {
      expect(g.symbol).toBeTruthy();
      expect(g.name).toBeTruthy();
    });
    expect(STATIC_BOND.length).toBe(5);
    STATIC_BOND.forEach((b) => expect(b.symbol).toMatch(/^TR/));
  });

  it('BOND_CATEGORY_LABELS bilinen enum anahtarlarını Türkçe etikete eşler', () => {
    expect(BOND_CATEGORY_LABELS.FIXED_COUPON_BOND).toBe('Kuponlu Devlet Tahvili');
    expect(BOND_CATEGORY_LABELS.LEASE_CERTIFICATE).toBe('Kira Sertifikası');
    // Bilinmeyen anahtar tanımsızdır (haritada yoktur).
    expect(BOND_CATEGORY_LABELS.UNKNOWN_KEY).toBeUndefined();
  });

  it('PRECIOUS_METALS_TRY yalnızca GRAM_TRY/KG_TRY kategorilerini içerir', () => {
    expect(PRECIOUS_METALS_TRY.length).toBeGreaterThan(0);
    PRECIOUS_METALS_TRY.forEach((p) => {
      expect(['GRAM_TRY', 'KG_TRY']).toContain(p.category);
    });
    // USD/EUR ons fiyatları TRY listesinden hariç tutulur.
    expect(PRECIOUS_METALS_TRY.some((p) => p.category === 'USD_ONS')).toBe(false);
    // BIST listesinden gerçekten süzülmüş olmalı (alt küme).
    expect(PRECIOUS_METALS_TRY.length).toBeLessThan(PRECIOUS_METALS_BIST.length);
  });

  it('FEATURED_PRECIOUS_SYMBOLS sadece GRAM_TRY sembollerini Set olarak tutar', () => {
    expect(FEATURED_PRECIOUS_SYMBOLS instanceof Set).toBe(true);
    expect(FEATURED_PRECIOUS_SYMBOLS.has('SILVER:GRAM_TRY')).toBe(true);
    expect(FEATURED_PRECIOUS_SYMBOLS.has('PLATINUM:GRAM_TRY')).toBe(true);
    // KG_TRY öne çıkanlarda değildir.
    expect(FEATURED_PRECIOUS_SYMBOLS.has('SILVER:KG_TRY')).toBe(false);
  });

  it('YAHOO_COMMODITIES Yahoo vadeli sembolleri (=F) taşır', () => {
    expect(YAHOO_COMMODITIES.length).toBeGreaterThan(0);
    YAHOO_COMMODITIES.forEach((c) => expect(c.symbol).toMatch(/=F$/));
  });

  it('FX_NAMES bilinen para birimi kodlarını Türkçe isme eşler', () => {
    expect(FX_NAMES.USD).toBe('ABD Doları');
    expect(FX_NAMES.EUR).toBe('Euro');
    expect(FX_NAMES.TRY).toBe('Türk Lirası');
    expect(FX_NAMES.ZZZ).toBeUndefined();
  });

  it('PAGE_SIZE = 15 ve TYPE_CACHE bir Map', () => {
    expect(PAGE_SIZE).toBe(15);
    expect(TYPE_CACHE instanceof Map).toBe(true);
  });
});

// ── pickGoldSpotPrice ──────────────────────────────────────────────────────────

describe('pickGoldSpotPrice', () => {
  it('g null/obje değilse null döner', () => {
    expect(pickGoldSpotPrice('GRAM', null)).toBeNull();
    expect(pickGoldSpotPrice('GRAM', undefined)).toBeNull();
    expect(pickGoldSpotPrice('GRAM', 'string')).toBeNull();
    expect(pickGoldSpotPrice('GRAM', 42)).toBeNull();
  });

  it('GRAM: gramGoldTry sayısal değeri seçer', () => {
    expect(pickGoldSpotPrice('GRAM', { gramGoldTry: 2500 })).toBe(2500);
  });

  it('GRAM: ilk alan yoksa sonraki fallback alana düşer', () => {
    // gramGoldTry yok → gramTl → officialPureGoldGramTry sırası.
    expect(pickGoldSpotPrice('GRAM', { gramTl: 2510 })).toBe(2510);
    expect(pickGoldSpotPrice('GRAM', { officialPureGoldGramTry: 2520 })).toBe(2520);
  });

  it('sembol küçük harf gelse de büyük harfe normalize edilir', () => {
    expect(pickGoldSpotPrice('gram', { gramGoldTry: 100 })).toBe(100);
  });

  it('GOLD: onsTry → priceTl → gramGoldTry önceliğiyle seçer', () => {
    expect(pickGoldSpotPrice('GOLD', { onsTry: 80000 })).toBe(80000);
    expect(pickGoldSpotPrice('GOLD', { priceTl: 81000 })).toBe(81000);
    expect(pickGoldSpotPrice('GOLD', { gramGoldTry: 2500 })).toBe(2500);
  });

  it('CEYREK/YARIM/TAM ilgili alanları seçer', () => {
    expect(pickGoldSpotPrice('CEYREK', { quarterGoldTry: 4000 })).toBe(4000);
    expect(pickGoldSpotPrice('YARIM', { halfGoldTry: 8000 })).toBe(8000);
    expect(pickGoldSpotPrice('TAM', { ziynetGoldTry: 16000 })).toBe(16000);
  });

  it('14AYAR ve AYAR14 aynı alanı (fourteenKBraceletTry) seçer', () => {
    expect(pickGoldSpotPrice('14AYAR', { fourteenKBraceletTry: 1200 })).toBe(1200);
    expect(pickGoldSpotPrice('AYAR14', { fourteenKBraceletTry: 1200 })).toBe(1200);
    // fallback alan ayar14Tl.
    expect(pickGoldSpotPrice('14AYAR', { ayar14Tl: 1250 })).toBe(1250);
  });

  it('22AYAR ve AYAR22 aynı alanı (twentyTwoKBraceletTry) seçer', () => {
    expect(pickGoldSpotPrice('22AYAR', { twentyTwoKBraceletTry: 2300 })).toBe(2300);
    expect(pickGoldSpotPrice('AYAR22', { twentyTwoKBraceletTry: 2300 })).toBe(2300);
  });

  it('ZIYNET → ziynetGoldTry, CUMHUR/ATA → republicGoldTry', () => {
    expect(pickGoldSpotPrice('ZIYNET', { ziynetGoldTry: 15500 })).toBe(15500);
    expect(pickGoldSpotPrice('CUMHUR', { republicGoldTry: 17000 })).toBe(17000);
    expect(pickGoldSpotPrice('ATA', { republicGoldTry: 17000 })).toBe(17000);
    // ATA fallback alanı cumhuriyetTl.
    expect(pickGoldSpotPrice('ATA', { cumhuriyetTl: 17100 })).toBe(17100);
  });

  it('bilinmeyen sembol default dala düşer (gramGoldTry/officialPureGoldGramTry)', () => {
    expect(pickGoldSpotPrice('BILINMEYEN', { gramGoldTry: 2500 })).toBe(2500);
    expect(pickGoldSpotPrice('XYZ', { officialPureGoldGramTry: 2600 })).toBe(2600);
    // İlgili alan yoksa null.
    expect(pickGoldSpotPrice('XYZ', { somethingElse: 99 })).toBeNull();
  });

  it('null sembol → "" → büyük harf → default dal', () => {
    expect(pickGoldSpotPrice(null, { gramGoldTry: 300 })).toBe(300);
  });

  it('Türkçe string fiyatı parseTrNumber ile çözer (gerçek numberFormat)', () => {
    // "5.734,00" → 5734 (binlik nokta atılır, ondalık virgül noktaya döner).
    expect(pickGoldSpotPrice('GRAM', { gramGoldTry: '5.734,00' })).toBe(5734);
  });

  it('sıfır, negatif, boş string ve null değerler pozitif-değil sayılır → fallback/null', () => {
    // 0 ve negatif pozitif değil → atlanır; tek alan buysa null.
    expect(pickGoldSpotPrice('GRAM', { gramGoldTry: 0 })).toBeNull();
    expect(pickGoldSpotPrice('GRAM', { gramGoldTry: -5 })).toBeNull();
    expect(pickGoldSpotPrice('GRAM', { gramGoldTry: '' })).toBeNull();
    expect(pickGoldSpotPrice('GRAM', { gramGoldTry: null })).toBeNull();
    // İlk alan 0 → fallback alana düşer.
    expect(pickGoldSpotPrice('GRAM', { gramGoldTry: 0, gramTl: 2510 })).toBe(2510);
  });

  it('NaN sayısal değer kabul edilmez → null', () => {
    // NaN: Number.isFinite(NaN)=false → parseTrNumber(NaN)=null → pick null döner.
    expect(pickGoldSpotPrice('GRAM', { gramGoldTry: NaN })).toBeNull();
  });

  it('Infinity, parseTrNumber yolundan geçip Infinity döner (>0 sayılır)', () => {
    // isFinite(Infinity)=false → number dalı atlanır → parseTrNumber(Infinity)=Infinity,
    // Infinity>0 true → pick Infinity döndürür. (parseTrNumber'ın bilinen Infinity davranışı.)
    expect(pickGoldSpotPrice('GRAM', { gramGoldTry: Infinity })).toBe(Infinity);
  });
});

// ── normalize ──────────────────────────────────────────────────────────────────

describe('normalize', () => {
  it('null/undefined → boş string', () => {
    expect(normalize(null)).toBe('');
    expect(normalize(undefined)).toBe('');
  });

  it('sayı gibi non-string girdiyi string yapıp küçültür', () => {
    expect(normalize(123)).toBe('123');
  });

  it('Türkçe locale ile küçültür: nokta korunur', () => {
    expect(normalize('AAPL')).toBe('aapl');
    // Nokta korunur; ".IS" Türkçe locale'de ".ıs" (noktasız ı) olur.
    expect(normalize('GARAN.IS')).toBe('garan.ıs');
  });

  it('Türkçe locale tuzağı: büyük I → noktasız ı', () => {
    // toLocaleLowerCase("tr") büyük "I" harfini noktasız "ı" yapar (toLowerCase'den farklı).
    expect(normalize('IS')).toBe('ıs');
  });
});

// ── filterItems ─────────────────────────────────────────────────────────────────

describe('filterItems', () => {
  const items = [
    { symbol: 'THYAO.IS', name: 'Türk Hava Yolları' },
    { symbol: 'GARAN.IS', name: 'Garanti Bankası' },
    { symbol: 'BTC', name: null },
  ];

  it('boş/whitespace sorgu → tüm listeyi (aynı referansı) döner', () => {
    expect(filterItems(items, '')).toBe(items);
    expect(filterItems(items, '   ')).toBe(items);
  });

  it('sembole göre filtreler (büyük/küçük harf duyarsız)', () => {
    const out = filterItems(items, 'thy');
    expect(out).toHaveLength(1);
    expect(out[0].symbol).toBe('THYAO.IS');
  });

  it('isme göre filtreler', () => {
    const out = filterItems(items, 'garanti');
    expect(out).toHaveLength(1);
    expect(out[0].symbol).toBe('GARAN.IS');
  });

  it('name null olan kayıt sembolle yine de eşleşir, isim taramada patlamaz', () => {
    const out = filterItems(items, 'btc');
    expect(out).toHaveLength(1);
    expect(out[0].symbol).toBe('BTC');
  });

  it('eşleşme yoksa boş dizi', () => {
    expect(filterItems(items, 'zzz')).toEqual([]);
  });
});

// ── preciousSortKey ─────────────────────────────────────────────────────────────

describe('preciousSortKey', () => {
  it(': içermeyen / null sembol → 999', () => {
    expect(preciousSortKey('GOLD')).toBe(999);
    expect(preciousSortKey(null)).toBe(999);
    expect(preciousSortKey(undefined)).toBe(999);
  });

  it('metal*10 + kategori ofseti hesaplar', () => {
    // SILVER(0)*10 + GRAM_TRY(0) = 0
    expect(preciousSortKey('SILVER:GRAM_TRY')).toBe(0);
    // SILVER(0)*10 + KG_TRY(1) = 1
    expect(preciousSortKey('SILVER:KG_TRY')).toBe(1);
    // PLATINUM(1)*10 + GRAM_TRY(0) = 10
    expect(preciousSortKey('PLATINUM:GRAM_TRY')).toBe(10);
    // PALLADIUM(2)*10 + KG_TRY(1) = 21
    expect(preciousSortKey('PALLADIUM:KG_TRY')).toBe(21);
  });

  it('bilinmeyen metal → 5, bilinmeyen kategori → 9', () => {
    // GOLD(metal yok→5)*10 + USD_ONS(kategori yok→9) = 59
    expect(preciousSortKey('GOLD:USD_ONS')).toBe(59);
    // SILVER(0)*10 + EUR_ONS(yok→9) = 9
    expect(preciousSortKey('SILVER:EUR_ONS')).toBe(9);
  });
});

describe('sortPreciousByTier', () => {
  it('preciousSortKey artan sırasına göre sıralar (orijinali değiştirmez)', () => {
    const input = [
      { symbol: 'PALLADIUM:KG_TRY' }, // 21
      { symbol: 'SILVER:GRAM_TRY' },  // 0
      { symbol: 'PLATINUM:GRAM_TRY' },// 10
    ];
    const out = sortPreciousByTier(input);
    expect(out.map((i) => i.symbol)).toEqual([
      'SILVER:GRAM_TRY',
      'PLATINUM:GRAM_TRY',
      'PALLADIUM:KG_TRY',
    ]);
    // Saf: girdi dizisi mutasyona uğramaz (kopya döner).
    expect(input[0].symbol).toBe('PALLADIUM:KG_TRY');
    expect(out).not.toBe(input);
  });

  it('boş dizi → boş dizi', () => {
    expect(sortPreciousByTier([])).toEqual([]);
  });
});

// ── buildCommoditySections ──────────────────────────────────────────────────────

describe('buildCommoditySections', () => {
  // metal alanı olanlar kıymetli maden; olmayanlar Yahoo emtia.
  const featured = { symbol: 'SILVER:GRAM_TRY', name: 'Gram Gümüş', metal: 'silver' };
  const featured2 = { symbol: 'PLATINUM:GRAM_TRY', name: 'Gram Platin', metal: 'platinum' };
  const kg = { symbol: 'SILVER:KG_TRY', name: 'Kg Gümüş', metal: 'silver' };
  const yahoo = { symbol: 'CL=F', name: 'WTI Ham Petrol' };
  const all = [featured, featured2, kg, yahoo];

  it('sorgu YOK + daraltılmış: yalnız öne çıkanlar, kalan varsa genişlet butonu', () => {
    const res = buildCommoditySections(all, '', false);
    expect(res.sections).toHaveLength(1);
    expect(res.sections[0].key).toBe('feat');
    expect(res.sections[0].items.map((i) => i.symbol)).toEqual([
      'SILVER:GRAM_TRY',
      'PLATINUM:GRAM_TRY',
    ]);
    expect(res.showExpandButton).toBe(true); // kg + yahoo dışarıda
    expect(res.totalCount).toBe(4);
    expect(res.visibleCount).toBe(2);
  });

  it('sorgu YOK + daraltılmış + kalan yok → genişlet butonu kapalı', () => {
    // Sadece öne çıkanlar varsa restCount=0.
    const res = buildCommoditySections([featured, featured2], '', false);
    expect(res.showExpandButton).toBe(false);
    expect(res.visibleCount).toBe(2);
  });

  it('sorgu YOK + genişletilmiş: feat + diğer maden + diğer emtia bölümleri', () => {
    const res = buildCommoditySections(all, '', true);
    const keys = res.sections.map((s) => s.key);
    expect(keys).toEqual(['feat', 'otherMetal', 'yahoo']);
    expect(res.showExpandButton).toBe(false);
    expect(res.visibleCount).toBe(4);
    // otherMetal = metal olup öne çıkmayan (kg).
    const other = res.sections.find((s) => s.key === 'otherMetal');
    expect(other.items.map((i) => i.symbol)).toEqual(['SILVER:KG_TRY']);
    // yahoo = metal alanı olmayan.
    const y = res.sections.find((s) => s.key === 'yahoo');
    expect(y.items.map((i) => i.symbol)).toEqual(['CL=F']);
  });

  it('sorgu YOK + genişletilmiş + sadece Yahoo varsa yalnız yahoo bölümü', () => {
    const res = buildCommoditySections([yahoo], '', true);
    expect(res.sections.map((s) => s.key)).toEqual(['yahoo']);
  });

  it('sorgu VAR + hem birincil hem ikincil → "Öne çıkan sonuçlar" + "Diğer sonuçlar"', () => {
    // "gümüş" → SILVER gram (birincil) + SILVER kg (ikincil) eşleşir.
    const res = buildCommoditySections(all, 'gümüş', false);
    expect(res.sections.map((s) => s.key)).toEqual(['s1', 's2']);
    expect(res.sections[0].title).toBe('Öne çıkan sonuçlar');
    expect(res.sections[1].title).toBe('Diğer sonuçlar');
    // s1 birincil madenler + yahoo (burada yahoo eşleşmez) → sadece SILVER:GRAM_TRY.
    expect(res.sections[0].items.map((i) => i.symbol)).toEqual(['SILVER:GRAM_TRY']);
    expect(res.sections[1].items.map((i) => i.symbol)).toEqual(['SILVER:KG_TRY']);
    expect(res.showExpandButton).toBe(false);
    expect(res.totalCount).toBe(2);
  });

  it('sorgu VAR + sadece birincil(+yahoo) blok, ikincil yok → tek bölüm', () => {
    // "platin" sadece PLATINUM:GRAM_TRY (birincil) eşleşir, ikincil yok.
    const res = buildCommoditySections(all, 'platin', false);
    expect(res.sections).toHaveLength(1);
    expect(res.sections[0].key).toBe('s1');
    // Tek sonuç → başlık null (filteredAll.length > 1 değil).
    expect(res.sections[0].title).toBeNull();
    expect(res.sections[0].items.map((i) => i.symbol)).toEqual(['PLATINUM:GRAM_TRY']);
  });

  it('sorgu VAR + birincil blok çoklu (>1) → başlık "Sonuçlar"', () => {
    // "gram" → SILVER:GRAM_TRY + PLATINUM:GRAM_TRY, ikisi de birincil; ikincil yok.
    const res = buildCommoditySections(all, 'gram', false);
    expect(res.sections).toHaveLength(1);
    expect(res.sections[0].key).toBe('s1');
    expect(res.sections[0].title).toBe('Sonuçlar');
    expect(res.sections[0].items.map((i) => i.symbol)).toEqual([
      'SILVER:GRAM_TRY',
      'PLATINUM:GRAM_TRY',
    ]);
  });

  it('sorgu VAR + sadece ikincil maden → başlıksız s2 bölümü', () => {
    // Sadece kg madeni (öne çıkmayan) listesinde "kg" arar; birincil yok.
    const res = buildCommoditySections([kg], 'kg', false);
    expect(res.sections).toHaveLength(1);
    expect(res.sections[0].key).toBe('s2');
    expect(res.sections[0].title).toBeNull();
    expect(res.sections[0].items.map((i) => i.symbol)).toEqual(['SILVER:KG_TRY']);
  });

  it('sorgu VAR + hiç eşleşme yok → bölüm yok', () => {
    const res = buildCommoditySections(all, 'zzzzz', false);
    expect(res.sections).toEqual([]);
    expect(res.totalCount).toBe(0);
    expect(res.visibleCount).toBe(0);
  });
});

// ── fetchAll (client MOCK'lu) ───────────────────────────────────────────────────

describe('fetchAll', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('STOCK: tek sayfa → mapler, ilk sayfayı onPartial ile yayınlar', async () => {
    client.get.mockResolvedValueOnce({
      data: { data: { size: 200, totalPages: 1, content: [
        { symbol: 'THYAO.IS', name: 'Türk Hava Yolları' },
        { symbol: 'GARAN.IS' }, // name yok → symbol fallback
        { name: 'NoSymbol' },   // symbol yok → filtrelenir
      ] } },
    });
    const onPartial = vi.fn();
    const out = await fetchAll('STOCK', onPartial);
    expect(out).toEqual([
      { symbol: 'THYAO.IS', name: 'Türk Hava Yolları' },
      { symbol: 'GARAN.IS', name: 'GARAN.IS' },
    ]);
    expect(onPartial).toHaveBeenCalledWith(out);
  });

  it('STOCK: çok sayfa → kalan sayfaları da çeker ve birleştirir', async () => {
    client.get
      .mockResolvedValueOnce({ data: { data: { size: 1, totalPages: 2, content: [{ symbol: 'A' }] } } })
      .mockResolvedValueOnce({ data: { data: { content: [{ symbol: 'B' }] } } });
    const out = await fetchAll('STOCK');
    expect(out.map((s) => s.symbol)).toEqual(['A', 'B']);
    expect(client.get).toHaveBeenCalledTimes(2);
  });

  it('STOCK: istek hata atarsa boş dizi (iç try/catch)', async () => {
    client.get.mockRejectedValueOnce(new Error('boom'));
    expect(await fetchAll('STOCK')).toEqual([]);
  });

  it('CRYPTO: sembolü büyütür, id/image taşır', async () => {
    client.get.mockResolvedValueOnce({
      data: { data: [{ symbol: 'btc', name: 'Bitcoin', id: 'bitcoin', image: 'x.png' }] },
    });
    const out = await fetchAll('CRYPTO');
    expect(out).toEqual([
      { symbol: 'BTC', name: 'Bitcoin', id: 'bitcoin', image: 'x.png' },
    ]);
  });

  it('CRYPTO: data.data null → boş dizi', async () => {
    client.get.mockResolvedValueOnce({ data: {} });
    expect(await fetchAll('CRYPTO')).toEqual([]);
  });

  it('FX: name yoksa FX_NAMES fallback, o da yoksa symbol', async () => {
    client.get.mockResolvedValueOnce({
      data: { data: { rates: [
        { symbol: 'USD' },              // FX_NAMES → ABD Doları
        { symbol: 'XXX' },              // bilinmiyor → symbol
        { symbol: 'EUR', name: 'Avro' },// API name öncelikli
      ] } },
    });
    const out = await fetchAll('FX');
    expect(out).toEqual([
      { symbol: 'USD', name: 'ABD Doları' },
      { symbol: 'XXX', name: 'XXX' },
      { symbol: 'EUR', name: 'Avro' },
    ]);
  });

  it('FUND: 4 kaynağı birleştirir, kategori atar, boş sembolü eler, tekrarı kaldırır', async () => {
    // allSettled sırası: tefas, bes, oks, osmanli.
    client.get
      .mockResolvedValueOnce({ data: { data: [{ code: 'AAA', name: 'Tefas A' }, { name: 'NoCode' }] } })
      .mockResolvedValueOnce({ data: { data: [{ code: 'BBB', name: 'Bes B' }] } })
      .mockResolvedValueOnce({ data: { data: [{ code: 'AAA', name: 'Dup tefas' }] } }) // tekrar → atılır
      .mockResolvedValueOnce({ data: { data: [{ uniqueCode: 'CCC', name: 'Osmanli C' }] } });
    const out = await fetchAll('FUND');
    expect(out).toEqual([
      { symbol: 'AAA', name: 'Tefas A', category: 'TEFAS' },
      { symbol: 'BBB', name: 'Bes B', category: 'BES' },
      { symbol: 'CCC', name: 'Osmanli C', category: 'Osmanlı' },
    ]);
  });

  it('FUND: bir kaynak reddedilse (rejected) diğerleri yine döner', async () => {
    client.get
      .mockResolvedValueOnce({ data: { data: [{ code: 'AAA', name: 'Tefas A' }] } })
      .mockRejectedValueOnce(new Error('bes down'))
      .mockResolvedValueOnce({ data: { data: [] } })
      .mockResolvedValueOnce({ data: { data: [] } });
    const out = await fetchAll('FUND');
    expect(out).toEqual([{ symbol: 'AAA', name: 'Tefas A', category: 'TEFAS' }]);
  });

  it('FUTURE: VİOP sözleşmeleri → tam ad symbol, parantez öncesi kısa label', async () => {
    client.get.mockResolvedValueOnce({
      data: { data: [
        { name: 'XAUTRYM26 (Altın Vadeli)', lastPrice: 10, settlementPrice: 11 },
        { contractName: 'F_THYAO', lastPrice: 5 }, // name yok → contractName
        { name: '' }, // boş → filtrelenir
      ] },
    });
    const out = await fetchAll('FUTURE');
    expect(out).toEqual([
      { symbol: 'XAUTRYM26 (Altın Vadeli)', name: 'XAUTRYM26', lastPrice: 10, settlementPrice: 11 },
      { symbol: 'F_THYAO', name: 'F_THYAO', lastPrice: 5, settlementPrice: undefined },
    ]);
  });

  it('FUTURE: VİOP boş liste → statik vadeli listesine düşer', async () => {
    client.get.mockResolvedValueOnce({ data: { data: [] } });
    const out = await fetchAll('FUTURE');
    expect(out.length).toBe(15);
    expect(out[0]).toEqual({ symbol: 'ES=F', name: 'S&P 500 Vadeli' });
  });

  it('FUTURE: VİOP isteği hata atarsa statik listeye düşer', async () => {
    client.get.mockRejectedValueOnce(new Error('viop down'));
    const out = await fetchAll('FUTURE');
    expect(out.find((c) => c.symbol === 'GC=F')).toBeTruthy();
  });

  it('GOLD: statik altın listesini döner (API çağrısı yok)', async () => {
    const out = await fetchAll('GOLD');
    expect(out).toBe(STATIC_GOLD);
    expect(client.get).not.toHaveBeenCalled();
  });

  it('COMMODITY: TRY kıymetli madenler + Yahoo emtiaları birleşik döner', async () => {
    const out = await fetchAll('COMMODITY');
    expect(out.length).toBe(PRECIOUS_METALS_TRY.length + YAHOO_COMMODITIES.length);
    expect(client.get).not.toHaveBeenCalled();
  });

  it('BOND: EVDS + eurobond birleşir; couponRate/indicatorValue sayıya çevrilir', async () => {
    client.get
      .mockResolvedValueOnce({ // EVDS sayfa 0 (tek sayfa)
        data: { data: { size: 100, totalPages: 1, items: [
          { instrumentCode: 'TRD0707', type: 'DİBS', couponRate: '5.5', indicatorValue: '99.1' },
          { type: 'NoCode' }, // instrumentCode yok → filtrelenir
        ] } },
      })
      .mockResolvedValueOnce({ // eurobond global
        data: { data: [
          { isin: 'XS123', name: 'Türkiye 2030', lastPrice: '101.2' },
          { issuer: 'NoIsin' }, // isin yok → filtrelenir
        ] },
      });
    const out = await fetchAll('BOND');
    expect(out).toHaveLength(2);
    expect(out[0]).toMatchObject({ symbol: 'TRD0707', couponRate: 5.5, indicatorValue: 99.1 });
    expect(out[1]).toMatchObject({ symbol: 'XS123', type: 'Eurobond', subType: 'EUROBOND', indicatorValue: 101.2 });
  });

  it('BOND: EVDS çok sayfa → kalan sayfaları çeker', async () => {
    client.get
      .mockResolvedValueOnce({ data: { data: { size: 1, totalPages: 2, items: [{ instrumentCode: 'A' }] } } })
      .mockResolvedValueOnce({ data: { data: { items: [{ instrumentCode: 'B' }] } } }) // sayfa 1
      .mockResolvedValueOnce({ data: { data: [] } }); // eurobond boş
    const out = await fetchAll('BOND');
    expect(out.map((b) => b.symbol)).toEqual(['A', 'B']);
  });

  it('BOND: her iki kaynak da hata/boşsa STATIC_BOND fallback', async () => {
    client.get
      .mockRejectedValueOnce(new Error('evds down'))
      .mockRejectedValueOnce(new Error('euro down'));
    const out = await fetchAll('BOND');
    expect(out).toBe(STATIC_BOND);
  });

  it('INDICATOR: endeks kod/isim/kategori maplenir', async () => {
    client.get.mockResolvedValueOnce({
      data: { data: [
        { code: 'XU100', name: 'BIST 100', category: 'Ana' },
        { code: 'XBANK', name: 'Banka', category: 'Sektör' },
      ] },
    });
    const out = await fetchAll('INDICATOR');
    expect(out).toEqual([
      { symbol: 'XU100', name: 'BIST 100', category: 'Ana' },
      { symbol: 'XBANK', name: 'Banka', category: 'Sektör' },
    ]);
  });

  it('bilinmeyen tip → boş dizi (hiçbir dal eşleşmez)', async () => {
    expect(await fetchAll('UNKNOWN_TYPE')).toEqual([]);
    expect(client.get).not.toHaveBeenCalled();
  });
});
