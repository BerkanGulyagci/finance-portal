import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';

// Hook (ve içe aktardığı instrumentSearchUtils) HTTP için '../../../lib/http' default
// export'unu (axios client) kullanır. Tek bir mock hem hook'taki client.get'i hem
// fetchAll içindeki client.get'i karşılar → gerçek ağ isteği atılmaz.
vi.mock('../../../../lib/http', () => ({
  default: { get: vi.fn() },
}));

import client from '../../../../lib/http';
import { TYPE_CACHE, PAGE_SIZE } from '../../utils/instrumentSearchUtils';
import { useInstrumentSearch } from '../useInstrumentSearch';

// Belirli bir adet öğe üreten yardımcı (sayfalama testleri için).
const makeItems = (n, prefix = 'SYM') =>
  Array.from({ length: n }, (_, i) => ({ symbol: `${prefix}${i}`, name: `Name ${i}` }));

// STOCK fetchAll'ı tek sayfada çözmek için standart yanıt.
const stockPageResponse = (items) => ({
  data: { data: { size: 200, totalPages: 1, content: items } },
});

beforeEach(() => {
  vi.clearAllMocks();
  // Modül-seviyesi cache testler arası SIZAR — her testte temizlenmeli.
  TYPE_CACHE.clear();
  localStorage.clear();
  // Varsayılan: tanımsız her client.get çağrısı boş/çözülmüş yanıt döndürsün ki
  // beklenmeyen bir çağrı reddedilip testi kırmasın.
  client.get.mockResolvedValue({ data: { data: null } });
});

// ── Başlangıç state'i + ilk yükleme ─────────────────────────────────────────────

describe('useInstrumentSearch — başlangıç ve ilk yükleme', () => {
  it('başlangıç state değerlerini doğru kurar', async () => {
    client.get.mockResolvedValue(stockPageResponse([]));
    const onSelect = vi.fn();
    const { result } = renderHook(() => useInstrumentSearch({ initialType: 'STOCK', onSelect }));

    // İlk render'da activeType initialType olur.
    expect(result.current.activeType).toBe('STOCK');
    expect(result.current.query).toBe('');
    expect(result.current.priceLoading).toBe(false);
    expect(result.current.commodityExpanded).toBe(false);
    expect(typeof result.current.setQuery).toBe('function');
    expect(typeof result.current.handleSelect).toBe('function');

    // loadAll bittikten sonra loading kapanır.
    await waitFor(() => expect(result.current.loading).toBe(false));
  });

  it('STOCK ilk yüklemede listeyi çeker, ilk sayfayı gösterir ve hasMore=true', async () => {
    const items = makeItems(PAGE_SIZE + 5); // PAGE_SIZE'dan fazla → hasMore true
    client.get.mockResolvedValue(stockPageResponse(items));
    const { result } = renderHook(() =>
      useInstrumentSearch({ initialType: 'STOCK', onSelect: vi.fn() }),
    );

    await waitFor(() => expect(result.current.allItems.length).toBe(items.length));
    expect(result.current.displayed).toHaveLength(PAGE_SIZE);
    expect(result.current.hasMore).toBe(true);
    expect(result.current.loading).toBe(false);
  });

  it('cache doluysa API çağrısı yapılmadan cache uygulanır', async () => {
    const cached = makeItems(3);
    TYPE_CACHE.set('STOCK', cached);
    const { result } = renderHook(() =>
      useInstrumentSearch({ initialType: 'STOCK', onSelect: vi.fn() }),
    );

    await waitFor(() => expect(result.current.allItems).toBe(cached));
    expect(result.current.displayed).toHaveLength(3);
    expect(result.current.hasMore).toBe(false);
    // STOCK için fetchAll'ın /api/market/stocks çağrısı YAPILMAMALI (cache hit).
    expect(client.get).not.toHaveBeenCalled();
  });

  it('fetchAll hata atarsa state boşa düşer (catch dalı)', async () => {
    // STOCK fetchAll iç try/catch ile boş döndüğünden, dış catch'i tetiklemek için
    // applyItems'i patlatmıyoruz; bunun yerine COMMODITY-dışı boş liste yolunu doğrularız.
    client.get.mockRejectedValue(new Error('boom'));
    const { result } = renderHook(() =>
      useInstrumentSearch({ initialType: 'STOCK', onSelect: vi.fn() }),
    );
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.allItems).toEqual([]);
    expect(result.current.displayed).toEqual([]);
    expect(result.current.hasMore).toBe(false);
  });

  it('COMMODITY ilk yüklemede displayed boş, hasMore false (özel dal)', async () => {
    const { result } = renderHook(() =>
      useInstrumentSearch({ initialType: 'COMMODITY', onSelect: vi.fn() }),
    );
    // COMMODITY fetchAll statik döndüğü için allItems dolar ama displayed boş kalır.
    await waitFor(() => expect(result.current.allItems.length).toBeGreaterThan(0));
    expect(result.current.displayed).toEqual([]);
    expect(result.current.hasMore).toBe(false);
    expect(result.current.commodityExpanded).toBe(false);
    // commodityView üretilir (COMMODITY aktif).
    expect(result.current.commodityView).not.toBeNull();
  });
});

// ── Türetilmiş değerler ─────────────────────────────────────────────────────────

describe('useInstrumentSearch — türetilmiş alanlar', () => {
  it('seeAll, currentType ve filtered doğru türetilir', async () => {
    client.get.mockResolvedValue(stockPageResponse(makeItems(2)));
    const { result } = renderHook(() =>
      useInstrumentSearch({ initialType: 'STOCK', onSelect: vi.fn() }),
    );
    await waitFor(() => expect(result.current.allItems.length).toBe(2));

    expect(result.current.seeAll).toEqual({ label: 'Tüm hisseleri gör', path: '/market/stocks' });
    expect(result.current.currentType.value).toBe('STOCK');
    expect(result.current.filtered).toHaveLength(2);
    // COMMODITY aktif değilken commodityView null.
    expect(result.current.commodityView).toBeNull();
  });
});

// ── handleTypeChange ────────────────────────────────────────────────────────────

describe('useInstrumentSearch — handleTypeChange', () => {
  it('tip değişince activeType güncellenir, query sıfırlanır, yeni liste yüklenir', async () => {
    client.get.mockResolvedValue(stockPageResponse(makeItems(2)));
    const { result } = renderHook(() =>
      useInstrumentSearch({ initialType: 'STOCK', onSelect: vi.fn() }),
    );
    await waitFor(() => expect(result.current.allItems.length).toBe(2));

    // GOLD'a geç — fetchAll statik STATIC_GOLD döndürür (API çağrısı yok).
    act(() => {
      result.current.handleTypeChange('GOLD');
    });
    expect(result.current.activeType).toBe('GOLD');
    expect(result.current.query).toBe('');
    expect(result.current.commodityExpanded).toBe(false);

    await waitFor(() => expect(result.current.allItems.length).toBeGreaterThan(0));
    // GOLD listesi STATIC_GOLD (8 öğe) → PAGE_SIZE'dan az → hasMore false.
    expect(result.current.hasMore).toBe(false);
    expect(result.current.seeAll.path).toBe('/market/gold');
  });
});

// ── Arama filtresi (debounce'lu effect) ─────────────────────────────────────────

describe('useInstrumentSearch — query filtresi', () => {
  it('setQuery debounce sonrası displayed listesini filtreler', async () => {
    const items = [
      { symbol: 'THYAO', name: 'Türk Hava' },
      { symbol: 'GARAN', name: 'Garanti' },
      { symbol: 'AKBNK', name: 'Akbank' },
    ];
    client.get.mockResolvedValue(stockPageResponse(items));
    const { result } = renderHook(() =>
      useInstrumentSearch({ initialType: 'STOCK', onSelect: vi.fn() }),
    );
    await waitFor(() => expect(result.current.displayed.length).toBe(3));

    act(() => {
      result.current.setQuery('garan');
    });
    // 150ms debounce → waitFor ile beklenir.
    await waitFor(() => {
      expect(result.current.displayed).toHaveLength(1);
      expect(result.current.displayed[0].symbol).toBe('GARAN');
    });
    expect(result.current.filtered).toHaveLength(1);
  });

  it('COMMODITY aktifken query effect erken döner (displayed boş kalır)', async () => {
    const { result } = renderHook(() =>
      useInstrumentSearch({ initialType: 'COMMODITY', onSelect: vi.fn() }),
    );
    await waitFor(() => expect(result.current.allItems.length).toBeGreaterThan(0));

    act(() => {
      result.current.setQuery('gümüş');
    });
    // COMMODITY dalında effect filtrelemez → displayed boş; commodityView güncellenir.
    await waitFor(() => expect(result.current.commodityView).not.toBeNull());
    expect(result.current.displayed).toEqual([]);
  });
});

// ── loadMore + handleScroll ─────────────────────────────────────────────────────

describe('useInstrumentSearch — sayfalama', () => {
  it('loadMore bir sonraki sayfayı ekler ve hasMore günceller', async () => {
    const items = makeItems(PAGE_SIZE * 2 + 3); // 3 sayfaya yetecek
    client.get.mockResolvedValue(stockPageResponse(items));
    const { result } = renderHook(() =>
      useInstrumentSearch({ initialType: 'STOCK', onSelect: vi.fn() }),
    );
    await waitFor(() => expect(result.current.displayed.length).toBe(PAGE_SIZE));
    expect(result.current.hasMore).toBe(true);

    act(() => {
      result.current.loadMore();
    });
    expect(result.current.displayed).toHaveLength(PAGE_SIZE * 2);
    expect(result.current.hasMore).toBe(true);

    act(() => {
      result.current.loadMore();
    });
    // 3. sayfa → toplam items.length; artık fazlası yok.
    expect(result.current.displayed).toHaveLength(items.length);
    expect(result.current.hasMore).toBe(false);
  });

  it('handleScroll eşik aşılınca loadMore tetikler', async () => {
    const items = makeItems(PAGE_SIZE * 2);
    client.get.mockResolvedValue(stockPageResponse(items));
    const { result } = renderHook(() =>
      useInstrumentSearch({ initialType: 'STOCK', onSelect: vi.fn() }),
    );
    await waitFor(() => expect(result.current.displayed.length).toBe(PAGE_SIZE));

    // scrollTop + clientHeight >= scrollHeight - 40 → loadMore.
    const evt = { currentTarget: { scrollTop: 900, clientHeight: 100, scrollHeight: 1000 } };
    act(() => {
      result.current.handleScroll(evt);
    });
    expect(result.current.displayed).toHaveLength(PAGE_SIZE * 2);
  });

  it('handleScroll eşiğin altında loadMore tetiklemez', async () => {
    const items = makeItems(PAGE_SIZE * 2);
    client.get.mockResolvedValue(stockPageResponse(items));
    const { result } = renderHook(() =>
      useInstrumentSearch({ initialType: 'STOCK', onSelect: vi.fn() }),
    );
    await waitFor(() => expect(result.current.displayed.length).toBe(PAGE_SIZE));

    const evt = { currentTarget: { scrollTop: 0, clientHeight: 100, scrollHeight: 1000 } };
    act(() => {
      result.current.handleScroll(evt);
    });
    // Eşik aşılmadı → displayed değişmez.
    expect(result.current.displayed).toHaveLength(PAGE_SIZE);
  });

  it('handleScroll COMMODITY aktifken erken döner (hiç bir şey yapmaz)', async () => {
    const { result } = renderHook(() =>
      useInstrumentSearch({ initialType: 'COMMODITY', onSelect: vi.fn() }),
    );
    await waitFor(() => expect(result.current.allItems.length).toBeGreaterThan(0));
    const evt = { currentTarget: { scrollTop: 900, clientHeight: 100, scrollHeight: 1000 } };
    act(() => {
      result.current.handleScroll(evt);
    });
    expect(result.current.displayed).toEqual([]);
  });
});

// ── handleSelect — varlık türlerine göre fiyat türetme ───────────────────────────

describe('useInstrumentSearch — handleSelect', () => {
  // Belirli activeType ile bir hook kurar (önce o tipin listesini yüklenmiş yapar).
  const mountWithType = async (type, listResponse = { data: { data: null } }) => {
    const onSelect = vi.fn();
    client.get.mockResolvedValue(listResponse);
    const { result } = renderHook(() => useInstrumentSearch({ initialType: type, onSelect }));
    await waitFor(() => expect(result.current.loading).toBe(false));
    vi.clearAllMocks();
    return { result, onSelect };
  };

  it('STOCK: özet fiyatını çeker ve onSelect ile iletir', async () => {
    const { result, onSelect } = await mountWithType('STOCK', stockPageResponse([]));
    client.get.mockResolvedValueOnce({ data: { data: { summary: { price: 123.45 } } } });

    await act(async () => {
      await result.current.handleSelect({ symbol: 'THYAO.IS', name: 'Türk Hava' });
    });

    expect(client.get).toHaveBeenCalledWith('/api/v1/market/stocks/THYAO.IS');
    expect(onSelect).toHaveBeenCalledTimes(1);
    const arg = onSelect.mock.calls[0][0];
    expect(arg).toMatchObject({
      symbol: 'THYAO.IS',
      assetType: 'STOCK',
      name: 'Türk Hava',
      price: 123.45,
    });
    // STOCK için currency undefined.
    expect(arg.currency).toBeUndefined();
    // priceLoading tekrar false olur.
    expect(result.current.priceLoading).toBe(false);
  });

  it('CRYPTO: id ile coin bulup currentPrice iletir', async () => {
    const { result, onSelect } = await mountWithType('CRYPTO');
    client.get.mockResolvedValueOnce({
      data: { data: [
        { id: 'bitcoin', symbol: 'btc', currentPrice: 50000 },
        { id: 'ethereum', symbol: 'eth', currentPrice: 3000 },
      ] },
    });

    await act(async () => {
      await result.current.handleSelect({ id: 'ethereum', symbol: 'ETH', name: 'Ethereum' });
    });

    expect(onSelect.mock.calls[0][0]).toMatchObject({ symbol: 'ETH', price: 3000, assetType: 'CRYPTO' });
  });

  it('CRYPTO: id yoksa sembol (küçük harf) eşleşmesiyle bulur', async () => {
    const { result, onSelect } = await mountWithType('CRYPTO');
    client.get.mockResolvedValueOnce({
      data: { data: [{ id: 'bitcoin', symbol: 'btc', currentPrice: 51000 }] },
    });

    await act(async () => {
      await result.current.handleSelect({ symbol: 'BTC', name: 'Bitcoin' });
    });
    expect(onSelect.mock.calls[0][0].price).toBe(51000);
  });

  it('FX: alış/satış kurunu unit ile böler, price=fxSell, currency=symbol', async () => {
    const { result, onSelect } = await mountWithType('FX');
    client.get.mockResolvedValueOnce({
      data: { data: { rates: [
        { symbol: 'JPY', unit: 100, forexSelling: 250, forexBuying: 240 },
      ] } },
    });

    await act(async () => {
      await result.current.handleSelect({ symbol: 'jpy', name: 'Japon Yeni' });
    });

    const arg = onSelect.mock.calls[0][0];
    // unit=100 → 250/100=2.5 (sell), 240/100=2.4 (buy). price=fxSell.
    expect(arg.fxSell).toBeCloseTo(2.5, 5);
    expect(arg.fxBuy).toBeCloseTo(2.4, 5);
    expect(arg.price).toBeCloseTo(2.5, 5);
    // currency FX'te item.symbol (orijinal hali).
    expect(arg.currency).toBe('jpy');
  });

  it('FX: rate bulunamazsa price/fx null kalır', async () => {
    const { result, onSelect } = await mountWithType('FX');
    client.get.mockResolvedValueOnce({ data: { data: { rates: [{ symbol: 'EUR', buy: 35, sell: 36 }] } } });

    await act(async () => {
      await result.current.handleSelect({ symbol: 'USD', name: 'Dolar' });
    });
    const arg = onSelect.mock.calls[0][0];
    expect(arg.price).toBeNull();
    expect(arg.fxSell).toBeNull();
    expect(arg.fxBuy).toBeNull();
  });

  it('GOLD: gold/spot çekip pickGoldSpotPrice ile fiyatı bulur, currency=TRY', async () => {
    const { result, onSelect } = await mountWithType('GOLD');
    client.get.mockResolvedValueOnce({ data: { data: { gramGoldTry: 2500 } } });

    await act(async () => {
      await result.current.handleSelect({ symbol: 'GRAM', name: 'Gram Altın' });
    });
    const arg = onSelect.mock.calls[0][0];
    expect(client.get).toHaveBeenCalledWith('/api/v1/gold/spot');
    expect(arg.price).toBe(2500);
    expect(arg.currency).toBe('TRY');
  });

  it('BOND: item.indicatorValue doğrudan kullanılır (ek istek yok), currency=TRY', async () => {
    const { result, onSelect } = await mountWithType('BOND');
    // indicatorValue zaten dolu → ikinci EVDS isteği atılmamalı.
    await act(async () => {
      await result.current.handleSelect({
        symbol: 'TRD0707', name: 'Tahvil', indicatorValue: 99.5,
        cbrtCode: 'XYZ', maturityDate: '2027-01-01', couponRate: 5,
      });
    });
    const arg = onSelect.mock.calls[0][0];
    expect(arg.price).toBe(99.5);
    expect(arg.currency).toBe('TRY');
    // BOND alanları iletilir.
    expect(arg).toMatchObject({ cbrtCode: 'XYZ', maturityDate: '2027-01-01', couponRate: 5 });
    expect(client.get).not.toHaveBeenCalled();
  });

  it('BOND: liste değeri yoksa EVDS detayından indicatorValue çeker', async () => {
    const { result, onSelect } = await mountWithType('BOND');
    client.get.mockResolvedValueOnce({ data: { data: { indicatorValue: 101.25 } } });

    await act(async () => {
      await result.current.handleSelect({ symbol: 'TRD9999', name: 'Tahvil 2' });
    });
    expect(client.get).toHaveBeenCalledWith('/api/v1/market/bonds/evds/TRD9999');
    expect(onSelect.mock.calls[0][0].price).toBe(101.25);
  });

  it('COMMODITY (gümüş GRAM_TRY, ":" sembol): silver spot+history → fiyat, currency=TRY', async () => {
    const { result, onSelect } = await mountWithType('COMMODITY');
    // Promise.all sırası: [silver/spot, silver/history]. pickSilverGramCloseTry history close'u alır.
    client.get
      .mockResolvedValueOnce({ data: { data: { silverGramCloseTry: 40 } } })       // spot
      .mockResolvedValueOnce({ data: { data: { points: [{ close: 42.5 }] } } });   // history

    await act(async () => {
      await result.current.handleSelect({ symbol: 'SILVER:GRAM_TRY', name: 'Gram Gümüş' });
    });
    const arg = onSelect.mock.calls[0][0];
    // history son close=42.5 öncelikli.
    expect(arg.price).toBeCloseTo(42.5, 5);
    expect(arg.currency).toBe('TRY');
    // Yahoo emtia değil → commoditySpot null.
    expect(arg.commoditySpot).toBeNull();
  });

  it('COMMODITY (Yahoo sembol CL=F): commodities/spot → TRY fiyat + commoditySpot iletilir', async () => {
    const { result, onSelect } = await mountWithType('COMMODITY');
    const spot = { displayCurrency: 'TRY', displayPrice: 1850.5 };
    client.get.mockResolvedValueOnce({ data: { data: spot } });

    await act(async () => {
      await result.current.handleSelect({ symbol: 'CL=F', name: 'WTI Ham Petrol' });
    });
    const arg = onSelect.mock.calls[0][0];
    expect(client.get).toHaveBeenCalledWith('/api/v1/commodities/spot', { params: { symbol: 'CL=F' } });
    // pickCommoditySpotPriceTry → displayPrice (TRY).
    expect(arg.price).toBeCloseTo(1850.5, 5);
    expect(arg.currency).toBe('TRY');
    // Yahoo emtia → commoditySpot iletilir.
    expect(arg.commoditySpot).toEqual(spot);
  });

  it('COMMODITY (KG_TRY metal != silver): precious-metals spot → closeTryKg', async () => {
    const { result, onSelect } = await mountWithType('COMMODITY');
    client.get.mockResolvedValueOnce({ data: { data: { closeTryKg: 90000 } } });

    await act(async () => {
      await result.current.handleSelect({ symbol: 'PLATINUM:KG_TRY', name: 'Kg Platin' });
    });
    expect(client.get).toHaveBeenCalledWith('/api/v1/precious-metals/platinum/spot');
    expect(onSelect.mock.calls[0][0].price).toBe(90000);
  });

  it('FUTURE (Yahoo tarzı sembol): commodities/spot USD fiyat, currency=USD', async () => {
    const { result, onSelect } = await mountWithType('FUTURE');
    // isYahooCommoditySymbol('CL=F') true → currency USD.
    client.get.mockResolvedValueOnce({ data: { data: { rawPrice: 80, rawCurrency: 'USD' } } });

    await act(async () => {
      await result.current.handleSelect({ symbol: 'CL=F', name: 'Ham Petrol Vadeli' });
    });
    const arg = onSelect.mock.calls[0][0];
    expect(client.get).toHaveBeenCalledWith('/api/v1/commodities/spot', { params: { symbol: 'CL=F' } });
    expect(arg.price).toBeCloseTo(80, 5);
    expect(arg.currency).toBe('USD');
  });

  it('FUTURE (VİOP, liste fiyatı var): ek istek atmadan lastPrice kullanır', async () => {
    const { result, onSelect } = await mountWithType('FUTURE');
    // sym Yahoo tarzı DEĞİL (boşluk/uzun ad) → fromList yolu. parseTrNumber("1.234,5")=1234.5
    await act(async () => {
      await result.current.handleSelect({
        symbol: 'XAUTRYM26 Altın', name: 'Altın', lastPrice: '1.234,5',
      });
    });
    const arg = onSelect.mock.calls[0][0];
    expect(arg.price).toBeCloseTo(1234.5, 5);
    // VİOP isteği atılmadı (liste fiyatı vardı). Yahoo tarzı olmadığı için currency undefined.
    expect(client.get).not.toHaveBeenCalled();
    expect(arg.currency).toBeUndefined();
  });

  it('FUND: kategori → sourceCode eşlemesiyle tefas fiyatını çeker', async () => {
    const { result, onSelect } = await mountWithType('FUND');
    client.get.mockResolvedValueOnce({ data: { data: { price: 12.34 } } });

    await act(async () => {
      await result.current.handleSelect({ symbol: 'AAA', name: 'Fon A', category: 'BES' });
    });
    // category BES → sourceCode TPF.
    expect(client.get).toHaveBeenCalledWith('/api/v1/market/funds/tefas/AAA', { params: { sourceCode: 'TPF' } });
    const arg = onSelect.mock.calls[0][0];
    expect(arg.price).toBe(12.34);
    expect(arg.category).toBe('BES');
  });

  it('fiyat isteği hata atsa bile onSelect price=null ile çağrılır (catch yutar)', async () => {
    const { result, onSelect } = await mountWithType('STOCK', stockPageResponse([]));
    client.get.mockRejectedValueOnce(new Error('price down'));

    await act(async () => {
      await result.current.handleSelect({ symbol: 'ZZZ', name: 'Z' });
    });
    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect.mock.calls[0][0]).toMatchObject({ symbol: 'ZZZ', price: null });
    expect(result.current.priceLoading).toBe(false);
  });

  it('name yoksa symbol fallback, subType varsa iletilir', async () => {
    const { result, onSelect } = await mountWithType('STOCK', stockPageResponse([]));
    client.get.mockResolvedValueOnce({ data: { data: { summary: { price: 5 } } } });

    await act(async () => {
      await result.current.handleSelect({ symbol: 'NONAME', subType: 'EUROBOND' });
    });
    const arg = onSelect.mock.calls[0][0];
    expect(arg.name).toBe('NONAME'); // name yok → symbol
    expect(arg.subType).toBe('EUROBOND');
  });

  it('eski seçim yarışı: yeni seçim gelince eski sonuç onSelect çağırmaz', async () => {
    const { result, onSelect } = await mountWithType('STOCK', stockPageResponse([]));

    // İlk seçim geç çözülen bir istek; ikinci seçim hemen çözülür ve seq'i artırır.
    let resolveSlow;
    const slow = new Promise((res) => { resolveSlow = res; });
    client.get
      .mockReturnValueOnce(slow)                                              // 1. seçim — askıda
      .mockResolvedValueOnce({ data: { data: { summary: { price: 9 } } } }); // 2. seçim — hızlı

    await act(async () => {
      const p1 = result.current.handleSelect({ symbol: 'A', name: 'A' });
      const p2 = result.current.handleSelect({ symbol: 'B', name: 'B' });
      // Önce 2. (hızlı) tamamlanır → selectSeqRef ilerler.
      await p2;
      // Sonra 1. (yavaş) çözülür ama seq eşleşmediği için onSelect ÇAĞIRMAMALI.
      resolveSlow({ data: { data: { summary: { price: 1 } } } });
      await p1;
    });

    // Yalnızca 2. seçim onSelect tetikledi.
    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect.mock.calls[0][0].symbol).toBe('B');
  });
});
