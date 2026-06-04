import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';

// ── react-router-dom: useSearchParams MOCK'lanır ─────────────────────────────
// Modül seviyesinde değiştirilebilir bir param haritası tutup useSearchParams onu döner;
// böylece her test ?add= / ?name= değerlerini kontrol edebilir.
let mockParams = new Map();
vi.mock('react-router-dom', () => ({
  useSearchParams: () => [
    { get: (k) => (mockParams.has(k) ? mockParams.get(k) : null) },
  ],
}));

// ── marketApi MOCK'lanır — gerçek ağ isteği YOK ──────────────────────────────
// Test __tests__/ içinde, kaynak (useStockCompare.js) bir üstte, api 5 seviye yukarıda.
vi.mock('../../../../../api/marketApi', () => ({
  getStockChart: vi.fn(),
  getStockMidasDetail: vi.fn(),
  getAllStocks: vi.fn(),
  getMarketPriceHistory: vi.fn(),
  getFundCompareHistory: vi.fn(),
}));

import * as marketApi from '../../../../../api/marketApi';
import { useStockCompare } from '../useStockCompare';

// Varsayılan: getAllStocks boş döner, hiçbir çağrı patlatmaz.
beforeEach(() => {
  vi.clearAllMocks();
  mockParams = new Map();
  localStorage.clear();
  marketApi.getAllStocks.mockResolvedValue([]);
  marketApi.getStockChart.mockResolvedValue({ timestamps: [], closePrices: [] });
  marketApi.getStockMidasDetail.mockResolvedValue(null);
  marketApi.getMarketPriceHistory.mockResolvedValue({ timestamps: [], closePrices: [] });
  marketApi.getFundCompareHistory.mockResolvedValue({ timestamps: [], closePrices: [] });
});

// Hook'u kurar ve ilk getAllStocks effect'i çözülene kadar bekler (stocksLoading=false).
async function setup() {
  const view = renderHook(() => useStockCompare());
  await waitFor(() => expect(view.result.current.stocksLoading).toBe(false));
  return view;
}

describe('useStockCompare — başlangıç durumu ve hisse listesi yükleme', () => {
  it('başlangıçta varsayılan state değerlerini döner', async () => {
    marketApi.getAllStocks.mockResolvedValue([{ symbol: 'AKBNK.IS', name: 'Akbank' }]);
    const { result } = await setup();

    expect(result.current.selectedSymbols).toEqual([]);
    expect(result.current.extraItems).toEqual([]);
    expect(result.current.searchQuery).toBe('');
    expect(result.current.showDropdown).toBe(false);
    expect(result.current.searchOpen).toBe(false);
    expect(result.current.compared).toBe(false);
    expect(result.current.investment).toBe('1000');
    expect(result.current.rangeIdx).toBe(2); // default 1A
    expect(result.current.chartData).toEqual([]);
    expect(result.current.rawPrices).toEqual({});
    expect(result.current.midasDetails).toEqual({});
    expect(result.current.bist100Prices).toEqual([]);
    expect(result.current.totalCount).toBe(0);
    expect(result.current.mixedMode).toBe(false);
    // activeRange = RANGES[2] = 1A
    expect(result.current.activeRange).toMatchObject({ label: '1A', range: '1mo' });
    expect(result.current.allStocks).toHaveLength(1);
  });

  it('getAllStocks başarılı olunca allStocks dolar ve stocksLoading false olur', async () => {
    const stocks = [{ symbol: 'THYAO.IS', name: 'Türk Hava Yolları' }];
    marketApi.getAllStocks.mockResolvedValue(stocks);
    const { result } = await setup();
    expect(result.current.allStocks).toEqual(stocks);
    expect(result.current.stocksLoading).toBe(false);
  });

  it('getAllStocks null dönerse allStocks boş diziye düşer (?? [])', async () => {
    marketApi.getAllStocks.mockResolvedValue(null);
    const { result } = await setup();
    expect(result.current.allStocks).toEqual([]);
  });

  it('getAllStocks reddederse catch ile allStocks boş kalır, loading biter', async () => {
    marketApi.getAllStocks.mockRejectedValue(new Error('ağ hatası'));
    const { result } = await setup();
    expect(result.current.allStocks).toEqual([]);
    expect(result.current.stocksLoading).toBe(false);
  });
});

describe('useStockCompare — ?add= ön-seçim effect\'i', () => {
  it('?add olmadan hiçbir ön-seçim yapmaz', async () => {
    const { result } = await setup();
    expect(result.current.selectedSymbols).toEqual([]);
    expect(result.current.extraItems).toEqual([]);
  });

  it('düz "?add=SEMBOL" (geriye uyumlu) hisseyi seçer', async () => {
    mockParams.set('add', 'GARAN.IS');
    const { result } = await setup();
    expect(result.current.selectedSymbols).toEqual(['GARAN.IS']);
  });

  it('"?add=STOCK|SEMBOL" formatı hisse listesine ekler', async () => {
    mockParams.set('add', 'STOCK|ISCTR.IS');
    const { result } = await setup();
    expect(result.current.selectedSymbols).toEqual(['ISCTR.IS']);
    expect(result.current.extraItems).toEqual([]);
  });

  it('"?add=TÜR|SEMBOL" (hisse-dışı) extraItems\'a name ile ekler', async () => {
    mockParams.set('add', 'CRYPTO|BTC');
    mockParams.set('name', 'Bitcoin');
    const { result } = await setup();
    expect(result.current.extraItems).toEqual([
      { assetType: 'CRYPTO', symbol: 'BTC', name: 'Bitcoin', key: 'CRYPTO|BTC' },
    ]);
    expect(result.current.mixedMode).toBe(true);
  });

  it('name verilmezse extra item adı sembole düşer', async () => {
    mockParams.set('add', 'FUND|TTE');
    const { result } = await setup();
    expect(result.current.extraItems[0]).toMatchObject({ symbol: 'TTE', name: 'TTE' });
  });

  it('eksik parçalı "?add=STOCK|" hiçbir şey eklemez (erken return)', async () => {
    mockParams.set('add', 'STOCK|');
    const { result } = await setup();
    expect(result.current.selectedSymbols).toEqual([]);
    expect(result.current.extraItems).toEqual([]);
  });
});

describe('useStockCompare — addSymbol / removeSymbol', () => {
  it('addSymbol yeni sembolü ekler ve yardımcı state\'leri sıfırlar', async () => {
    const { result } = await setup();
    act(() => {
      result.current.setSearchQuery('gar');
      result.current.setShowDropdown(true);
    });
    act(() => result.current.addSymbol('GARAN.IS'));
    expect(result.current.selectedSymbols).toEqual(['GARAN.IS']);
    expect(result.current.searchQuery).toBe('');
    expect(result.current.showDropdown).toBe(false);
    expect(result.current.compared).toBe(false);
    expect(result.current.totalCount).toBe(1);
  });

  it('addSymbol zaten seçili sembolü tekrar eklemez', async () => {
    const { result } = await setup();
    act(() => result.current.addSymbol('AKBNK.IS'));
    act(() => result.current.addSymbol('AKBNK.IS'));
    expect(result.current.selectedSymbols).toEqual(['AKBNK.IS']);
  });

  it('addSymbol MAX_STOCKS (4) sınırında daha fazla eklemez', async () => {
    const { result } = await setup();
    act(() => result.current.addSymbol('A.IS'));
    act(() => result.current.addSymbol('B.IS'));
    act(() => result.current.addSymbol('C.IS'));
    act(() => result.current.addSymbol('D.IS'));
    act(() => result.current.addSymbol('E.IS')); // 5. → reddedilmeli
    expect(result.current.selectedSymbols).toEqual(['A.IS', 'B.IS', 'C.IS', 'D.IS']);
    expect(result.current.totalCount).toBe(4);
  });

  it('removeSymbol sembolü ve ilgili rawPrices/midasDetails girdilerini siler', async () => {
    const { result } = await setup();
    act(() => result.current.addSymbol('AKBNK.IS'));
    act(() => result.current.addSymbol('THYAO.IS'));
    act(() => result.current.removeSymbol('AKBNK.IS'));
    expect(result.current.selectedSymbols).toEqual(['THYAO.IS']);
    expect(result.current.rawPrices).not.toHaveProperty('AKBNK.IS');
    expect(result.current.midasDetails).not.toHaveProperty('AKBNK.IS');
    expect(result.current.compared).toBe(false);
  });
});

describe('useStockCompare — addExtra / removeExtra (cross-kıyas)', () => {
  it('geçersiz seçim (assetType/symbol yok) hiçbir şey yapmaz', async () => {
    const { result } = await setup();
    act(() => result.current.addExtra(null));
    act(() => result.current.addExtra({ symbol: 'X' }));
    act(() => result.current.addExtra({ assetType: 'CRYPTO' }));
    expect(result.current.extraItems).toEqual([]);
    expect(result.current.selectedSymbols).toEqual([]);
  });

  it('assetType STOCK ise addSymbol\'e yönlenir ve searchOpen kapanır', async () => {
    const { result } = await setup();
    act(() => result.current.setSearchOpen(true));
    act(() => result.current.addExtra({ assetType: 'STOCK', symbol: 'SISE.IS' }));
    expect(result.current.selectedSymbols).toEqual(['SISE.IS']);
    expect(result.current.extraItems).toEqual([]);
    expect(result.current.searchOpen).toBe(false);
  });

  it('hisse-dışı enstrümanı extraItems\'a ekler, searchOpen kapanır, compared sıfırlanır', async () => {
    const { result } = await setup();
    act(() => result.current.addExtra({ assetType: 'GOLD', symbol: 'XAU', name: 'Altın' }));
    expect(result.current.extraItems).toEqual([
      { assetType: 'GOLD', symbol: 'XAU', name: 'Altın', key: 'GOLD|XAU' },
    ]);
    expect(result.current.searchOpen).toBe(false);
    expect(result.current.mixedMode).toBe(true);
  });

  it('aynı key\'li extra item tekrar eklenmez', async () => {
    const { result } = await setup();
    act(() => result.current.addExtra({ assetType: 'FX', symbol: 'USD', name: 'Dolar' }));
    act(() => result.current.addExtra({ assetType: 'FX', symbol: 'USD', name: 'Dolar' }));
    expect(result.current.extraItems).toHaveLength(1);
  });

  it('extra item name verilmezse sembole düşer', async () => {
    const { result } = await setup();
    act(() => result.current.addExtra({ assetType: 'BOND', symbol: 'TRT' }));
    expect(result.current.extraItems[0]).toMatchObject({ symbol: 'TRT', name: 'TRT' });
  });

  it('totalCount MAX_STOCKS\'a ulaşınca extra eklenmez', async () => {
    const { result } = await setup();
    act(() => result.current.addSymbol('A.IS'));
    act(() => result.current.addSymbol('B.IS'));
    act(() => result.current.addSymbol('C.IS'));
    act(() => result.current.addSymbol('D.IS')); // totalCount=4
    act(() => result.current.addExtra({ assetType: 'CRYPTO', symbol: 'ETH', name: 'Ether' }));
    expect(result.current.extraItems).toEqual([]);
  });

  it('removeExtra key\'e göre siler ve compared\'ı sıfırlar', async () => {
    const { result } = await setup();
    act(() => result.current.addExtra({ assetType: 'FX', symbol: 'EUR', name: 'Euro' }));
    act(() => result.current.removeExtra('FX|EUR'));
    expect(result.current.extraItems).toEqual([]);
    expect(result.current.compared).toBe(false);
  });
});

describe('useStockCompare — filteredStocks (arama filtresi)', () => {
  const stocks = [
    { symbol: 'AKBNK.IS', name: 'Akbank' },
    { symbol: 'THYAO.IS', name: 'Türk Hava Yolları' },
    { symbol: 'GARAN.IS', name: 'Garanti' },
  ];

  it('seçili semboller listeden çıkarılır', async () => {
    marketApi.getAllStocks.mockResolvedValue(stocks);
    const { result } = await setup();
    act(() => result.current.addSymbol('AKBNK.IS'));
    const syms = result.current.filteredStocks.map(s => s.symbol);
    expect(syms).not.toContain('AKBNK.IS');
    expect(syms).toContain('THYAO.IS');
  });

  it('arama sorgusu sembol veya isimle eşleşir (case-insensitive)', async () => {
    marketApi.getAllStocks.mockResolvedValue(stocks);
    const { result } = await setup();
    act(() => result.current.setSearchQuery('hava'));
    expect(result.current.filteredStocks.map(s => s.symbol)).toEqual(['THYAO.IS']);
  });

  it('boş sorguda (sadece boşluk) tüm seçilmemişler döner, en çok 20', async () => {
    marketApi.getAllStocks.mockResolvedValue(stocks);
    const { result } = await setup();
    act(() => result.current.setSearchQuery('   '));
    expect(result.current.filteredStocks).toHaveLength(3);
  });

  it('eşleşme yoksa boş dizi döner', async () => {
    marketApi.getAllStocks.mockResolvedValue(stocks);
    const { result } = await setup();
    act(() => result.current.setSearchQuery('zzz-yok'));
    expect(result.current.filteredStocks).toEqual([]);
  });

  it('filtre en fazla 20 sonuç döndürür (slice 0,20)', async () => {
    const many = Array.from({ length: 30 }, (_, i) => ({ symbol: `S${i}.IS`, name: `Hisse ${i}` }));
    marketApi.getAllStocks.mockResolvedValue(many);
    const { result } = await setup();
    expect(result.current.filteredStocks).toHaveLength(20);
  });
});

describe('useStockCompare — handleCompare guard', () => {
  it('totalCount < 2 ise runCompare çağrılmaz (hiçbir api isteği yok)', async () => {
    const { result } = await setup();
    act(() => result.current.addSymbol('AKBNK.IS')); // tek sembol
    await act(async () => { result.current.handleCompare(); });
    expect(marketApi.getStockChart).not.toHaveBeenCalled();
    expect(result.current.compared).toBe(false);
  });
});

describe('useStockCompare — runCompare: hepsi-hisse zengin akış', () => {
  it('iki hisse ile chart/midas/bist100 çekip chartData ve serileri üretir', async () => {
    marketApi.getStockChart.mockImplementation((sym) => {
      if (sym === 'XU100.IS') return Promise.resolve({ timestamps: [1000, 2000], closePrices: [100, 110] });
      if (sym === 'AKBNK.IS') return Promise.resolve({ timestamps: [1000, 2000], closePrices: [50, 60] });
      if (sym === 'THYAO.IS') return Promise.resolve({ timestamps: [1000, 2000], closePrices: [200, 180] });
      return Promise.resolve({ timestamps: [], closePrices: [] });
    });
    marketApi.getStockMidasDetail.mockImplementation((sym) =>
      Promise.resolve({ symbol: sym, lastPrice: 1 }));

    const { result } = await setup();
    act(() => result.current.addSymbol('AKBNK.IS'));
    act(() => result.current.addSymbol('THYAO.IS'));

    await act(async () => { result.current.handleCompare(); });
    await waitFor(() => expect(result.current.compared).toBe(true));

    // BIST100 fiyatları set edildi
    expect(result.current.bist100Prices).toEqual([100, 110]);
    // Ham fiyatlar her sembol için saklandı
    expect(result.current.rawPrices).toEqual({ 'AKBNK.IS': [50, 60], 'THYAO.IS': [200, 180] });
    // Midas detayları sembol bazında map'lendi
    expect(result.current.midasDetails['AKBNK.IS']).toMatchObject({ symbol: 'AKBNK.IS' });
    // Seri tanımları: iki seri, renkler atanmış
    expect(result.current.chartSeriesDefs).toHaveLength(2);
    expect(result.current.chartSeriesDefs[0]).toMatchObject({ key: 'AKBNK.IS', color: '#093eaa' });
    // chartData iki zaman noktası
    expect(result.current.chartData).toHaveLength(2);
    // ilk satır %0 baseline, ikinci satırda AKBNK +%20 (50→60)
    expect(result.current.chartData[0]['AKBNK.IS']).toBeCloseTo(0, 3);
    expect(result.current.chartData[1]['AKBNK.IS']).toBeCloseTo(20, 3);
    expect(result.current.chartLoading).toBe(false);
    expect(result.current.detailsLoading).toBe(false);
  });

  it('endeks kısayolu sembolü için seri adı INDEX_SHORTCUTS etiketinden gelir', async () => {
    marketApi.getStockChart.mockResolvedValue({ timestamps: [1000, 2000], closePrices: [10, 12] });
    const { result } = await setup();
    act(() => result.current.addSymbol('XU100.IS'));
    act(() => result.current.addSymbol('AKBNK.IS'));
    await act(async () => { result.current.handleCompare(); });
    await waitFor(() => expect(result.current.compared).toBe(true));
    const xuDef = result.current.chartSeriesDefs.find(d => d.key === 'XU100.IS');
    expect(xuDef.name).toBe('BIST 100');
    const akDef = result.current.chartSeriesDefs.find(d => d.key === 'AKBNK.IS');
    expect(akDef.name).toBe('AKBNK'); // .IS soyulur, büyük harf
  });

  it('chart çağrısı patlarsa boş seri ile devam eder (catch)', async () => {
    marketApi.getStockChart.mockRejectedValue(new Error('boom'));
    const { result } = await setup();
    act(() => result.current.addSymbol('AKBNK.IS'));
    act(() => result.current.addSymbol('THYAO.IS'));
    await act(async () => { result.current.handleCompare(); });
    await waitFor(() => expect(result.current.compared).toBe(true));
    expect(result.current.chartData).toEqual([]);
    expect(result.current.bist100Prices).toEqual([]); // bist100 de null → ?? []
  });
});

describe('useStockCompare — runCompare: karışık (mixed) akış', () => {
  it('hisse + ekstra varlık → generic price-history ile tarih bazlı hizalar', async () => {
    // İki ayrı gün (2021-01-01, 2021-01-02 UTC) timestamp'leri
    const t1 = Math.floor(Date.UTC(2021, 0, 1) / 1000);
    const t2 = Math.floor(Date.UTC(2021, 0, 2) / 1000);
    marketApi.getMarketPriceHistory.mockImplementation((type) => {
      if (type === 'STOCK') return Promise.resolve({ timestamps: [t1, t2], closePrices: [100, 110] });
      if (type === 'CRYPTO') return Promise.resolve({ timestamps: [t1, t2], closePrices: [200, 260] });
      return Promise.resolve({ timestamps: [], closePrices: [] });
    });

    const { result } = await setup();
    act(() => result.current.addSymbol('AKBNK.IS'));
    act(() => result.current.addExtra({ assetType: 'CRYPTO', symbol: 'BTC', name: 'Bitcoin' }));

    await act(async () => { result.current.handleCompare(); });
    await waitFor(() => expect(result.current.compared).toBe(true));

    // mixed yolda STOCK da generic endpoint ile çekilir; getStockChart KULLANILMAZ
    expect(marketApi.getStockChart).not.toHaveBeenCalled();
    expect(marketApi.getMarketPriceHistory).toHaveBeenCalled();
    // detailsLoading mixed yolda false bırakılır
    expect(result.current.detailsLoading).toBe(false);
    // İki seri tanımı (hisse + kripto)
    expect(result.current.chartSeriesDefs.map(d => d.key)).toEqual(['AKBNK.IS', 'CRYPTO|BTC']);
    // chartData iki gün; ilk gün %0 baseline, ikinci gün AKBNK +%10
    expect(result.current.chartData).toHaveLength(2);
    expect(result.current.chartData[0]['AKBNK.IS']).toBeCloseTo(0, 3);
    expect(result.current.chartData[1]['AKBNK.IS']).toBeCloseTo(10, 3);
    expect(result.current.chartData[1]['CRYPTO|BTC']).toBeCloseTo(30, 3);
  });

  it('kısa aralıkta FON için getFundCompareHistory kullanılır (fundShort dalı)', async () => {
    const t1 = Math.floor(Date.UTC(2021, 0, 1) / 1000);
    const t2 = Math.floor(Date.UTC(2021, 0, 2) / 1000);
    marketApi.getMarketPriceHistory.mockResolvedValue({ timestamps: [t1, t2], closePrices: [100, 110] });
    marketApi.getFundCompareHistory.mockResolvedValue({ timestamps: [t1, t2], closePrices: [10, 11] });

    const { result } = await setup();
    // rangeIdx default 2 = 1A → toGenericRange('1mo') = '1M' (≤1Y) → fundShort true
    act(() => result.current.addSymbol('AKBNK.IS'));
    act(() => result.current.addExtra({ assetType: 'FUND', symbol: 'TTE', name: 'TEB Fon' }));

    await act(async () => { result.current.handleCompare(); });
    await waitFor(() => expect(result.current.compared).toBe(true));

    expect(marketApi.getFundCompareHistory).toHaveBeenCalledWith('TTE', '1M');
    // Hisse generic ile, fon fund-compare ile
    expect(marketApi.getMarketPriceHistory).toHaveBeenCalledWith('STOCK', 'AKBNK.IS', '1M');
  });

  it('uzun aralıkta (5Y) FON generic endpoint kullanır (fundShort false dalı)', async () => {
    const t1 = Math.floor(Date.UTC(2021, 0, 1) / 1000);
    marketApi.getMarketPriceHistory.mockResolvedValue({ timestamps: [t1], closePrices: [100] });

    const { result } = await setup();
    // 5Y aralığına geç (RANGES index 6)
    act(() => result.current.handleRangeChange(6));
    act(() => result.current.addSymbol('AKBNK.IS'));
    act(() => result.current.addExtra({ assetType: 'FUND', symbol: 'TTE', name: 'TEB Fon' }));

    await act(async () => { result.current.handleCompare(); });
    await waitFor(() => expect(result.current.compared).toBe(true));

    // 5Y → fundShort false → fon da getMarketPriceHistory ile
    expect(marketApi.getFundCompareHistory).not.toHaveBeenCalled();
    expect(marketApi.getMarketPriceHistory).toHaveBeenCalledWith('FUND', 'TTE', '5Y');
  });

  it('mixed yolda bir çağrı patlarsa o seri boş kalır (catch), diğeri çalışır', async () => {
    const t1 = Math.floor(Date.UTC(2021, 0, 1) / 1000);
    const t2 = Math.floor(Date.UTC(2021, 0, 2) / 1000);
    marketApi.getMarketPriceHistory.mockImplementation((type) => {
      if (type === 'CRYPTO') return Promise.reject(new Error('kripto down'));
      return Promise.resolve({ timestamps: [t1, t2], closePrices: [100, 110] });
    });
    const { result } = await setup();
    act(() => result.current.addSymbol('AKBNK.IS'));
    act(() => result.current.addExtra({ assetType: 'CRYPTO', symbol: 'BTC', name: 'Bitcoin' }));
    await act(async () => { result.current.handleCompare(); });
    await waitFor(() => expect(result.current.compared).toBe(true));
    // Hisse serisi var, kripto verisi yok ama crash olmadı
    expect(result.current.chartData.length).toBeGreaterThan(0);
    expect(result.current.chartData[1]['AKBNK.IS']).toBeCloseTo(10, 3);
  });
});

describe('useStockCompare — handleRangeChange', () => {
  it('rangeIdx\'i günceller ve compared değilken yeniden karşılaştırmaz', async () => {
    const { result } = await setup();
    act(() => result.current.handleRangeChange(5));
    expect(result.current.rangeIdx).toBe(5);
    expect(result.current.activeRange).toMatchObject({ label: '1Y', range: '1y' });
    expect(marketApi.getStockChart).not.toHaveBeenCalled();
  });

  it('compared=true ve totalCount>=2 iken aralık değişince yeniden karşılaştırır', async () => {
    marketApi.getStockChart.mockResolvedValue({ timestamps: [1000, 2000], closePrices: [10, 12] });
    const { result } = await setup();
    act(() => result.current.addSymbol('AKBNK.IS'));
    act(() => result.current.addSymbol('THYAO.IS'));
    await act(async () => { result.current.handleCompare(); });
    await waitFor(() => expect(result.current.compared).toBe(true));

    marketApi.getStockChart.mockClear();
    await act(async () => { result.current.handleRangeChange(5); }); // 1Y → günlük yeniden çek
    await waitFor(() => expect(result.current.rangeIdx).toBe(5));
    // Yeni aralıkla tekrar chart çekildi (en az AKBNK + THYAO + XU100)
    expect(marketApi.getStockChart).toHaveBeenCalled();
  });

  it('investment setter çalışır', async () => {
    const { result } = await setup();
    act(() => result.current.setInvestment('5000'));
    expect(result.current.investment).toBe('5000');
  });
});
