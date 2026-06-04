import { describe, it, expect } from 'vitest';
import {
  WATCHLIST_ASSET_LABELS,
  parseDailyChangePercent,
  groupByAssetType,
  calculateDailyStatus,
  calculateAverageChangeByType,
  hasAnyChangePercentData,
  getTopGainers,
  getTopLosers,
  getWatchlistChartDisplayTitle,
  getWatchlistTopListSubtitle,
  formatPctForChart,
  formatSharePercent,
  countSharePercent,
  formatPctAxis,
  formatInstrumentColumn,
  formatWatchlistTablePrice,
  formatWatchlistSymbolDisplay,
} from '../watchlistChartHelpers';

// Not: Bu dosya SAF mantık — dış bağımlılık yok; tr-TR toLocaleString davranışı
// gerçek (jsdom) ortamda baz alınarak assert edildi.

describe('WATCHLIST_ASSET_LABELS', () => {
  it('bilinen asset tipleri için Türkçe etiketleri içerir', () => {
    expect(WATCHLIST_ASSET_LABELS.STOCK).toBe('Hisse');
    expect(WATCHLIST_ASSET_LABELS.CRYPTO).toBe('Kripto');
    expect(WATCHLIST_ASSET_LABELS.FUND).toBe('Fon');
    expect(WATCHLIST_ASSET_LABELS.COMMODITY).toBe('Emtia');
    expect(WATCHLIST_ASSET_LABELS.GOLD).toBe('Altın');
    expect(WATCHLIST_ASSET_LABELS.FUTURE).toBe('Vadeli');
    expect(WATCHLIST_ASSET_LABELS.FX).toBe('Döviz');
    expect(WATCHLIST_ASSET_LABELS.BOND).toBe('DİBS');
    expect(WATCHLIST_ASSET_LABELS.OTHER).toBe('Diğer');
  });

  it('tam olarak 9 tip tanımlı', () => {
    expect(Object.keys(WATCHLIST_ASSET_LABELS)).toHaveLength(9);
  });
});

describe('parseDailyChangePercent', () => {
  it('null/undefined item için null döner', () => {
    expect(parseDailyChangePercent(null)).toBeNull();
    expect(parseDailyChangePercent(undefined)).toBeNull();
  });

  it('changePercent null/undefined/boş string ise null döner', () => {
    expect(parseDailyChangePercent({ changePercent: null })).toBeNull();
    expect(parseDailyChangePercent({ changePercent: undefined })).toBeNull();
    expect(parseDailyChangePercent({ changePercent: '' })).toBeNull();
    // changePercent alanı hiç yoksa (undefined) null
    expect(parseDailyChangePercent({})).toBeNull();
  });

  it('sayısal değeri olduğu gibi döner', () => {
    expect(parseDailyChangePercent({ changePercent: 2.5 })).toBe(2.5);
    expect(parseDailyChangePercent({ changePercent: -3 })).toBe(-3);
    expect(parseDailyChangePercent({ changePercent: 0 })).toBe(0);
  });

  it('string sayıyı parseFloat ile çevirir', () => {
    expect(parseDailyChangePercent({ changePercent: '1.25' })).toBeCloseTo(1.25);
    expect(parseDailyChangePercent({ changePercent: '-4.5' })).toBeCloseTo(-4.5);
    // baştaki sayıyı alır (parseFloat davranışı)
    expect(parseDailyChangePercent({ changePercent: '12abc' })).toBeCloseTo(12);
  });

  it('parse edilemeyen string ve sonlu olmayan değerler için null döner', () => {
    expect(parseDailyChangePercent({ changePercent: 'abc' })).toBeNull();
    expect(parseDailyChangePercent({ changePercent: Infinity })).toBeNull();
    expect(parseDailyChangePercent({ changePercent: NaN })).toBeNull();
  });
});

describe('groupByAssetType', () => {
  it('null/undefined giriş için boş nesne döner', () => {
    expect(groupByAssetType(null)).toEqual({});
    expect(groupByAssetType(undefined)).toEqual({});
    expect(groupByAssetType([])).toEqual({});
  });

  it('assetType’a göre gruplar', () => {
    const items = [
      { assetType: 'STOCK', symbol: 'A' },
      { assetType: 'STOCK', symbol: 'B' },
      { assetType: 'CRYPTO', symbol: 'BTC' },
    ];
    const g = groupByAssetType(items);
    expect(g.STOCK).toHaveLength(2);
    expect(g.CRYPTO).toHaveLength(1);
    expect(g.STOCK[0].symbol).toBe('A');
  });

  it('assetType yoksa OTHER altında toplar', () => {
    const items = [{ symbol: 'X' }, { assetType: '', symbol: 'Y' }];
    const g = groupByAssetType(items);
    // hem undefined hem boş string OTHER'a düşer
    expect(g.OTHER).toHaveLength(2);
  });
});

describe('calculateDailyStatus', () => {
  it('null/undefined için tüm sayaçlar sıfır', () => {
    expect(calculateDailyStatus(null)).toEqual({ total: 0, up: 0, down: 0, flat: 0, nodata: 0 });
    expect(calculateDailyStatus(undefined)).toEqual({ total: 0, up: 0, down: 0, flat: 0, nodata: 0 });
  });

  it('up/down/flat/nodata dallarını doğru sayar', () => {
    const items = [
      { changePercent: 5 },     // up
      { changePercent: 2 },     // up
      { changePercent: -1 },    // down
      { changePercent: 0 },     // flat
      { changePercent: null },  // nodata
      { changePercent: 'abc' }, // nodata (parse edilemez)
    ];
    expect(calculateDailyStatus(items)).toEqual({
      total: 6,
      up: 2,
      down: 1,
      flat: 1,
      nodata: 2,
    });
  });

  it('total daima dizinin uzunluğunu yansıtır', () => {
    const items = [{ changePercent: null }, { changePercent: null }];
    const res = calculateDailyStatus(items);
    expect(res.total).toBe(2);
    expect(res.nodata).toBe(2);
  });
});

describe('calculateAverageChangeByType', () => {
  it('null/undefined için boş dizi döner', () => {
    expect(calculateAverageChangeByType(null)).toEqual([]);
    expect(calculateAverageChangeByType([])).toEqual([]);
  });

  it('tip başına ortalama ve count hesaplar', () => {
    const items = [
      { assetType: 'STOCK', changePercent: 2 },
      { assetType: 'STOCK', changePercent: 4 },
      { assetType: 'CRYPTO', changePercent: -3 },
    ];
    const res = calculateAverageChangeByType(items);
    const stock = res.find(r => r.type === 'STOCK');
    const crypto = res.find(r => r.type === 'CRYPTO');
    expect(stock.avg).toBeCloseTo(3);
    expect(stock.count).toBe(2);
    expect(stock.label).toBe('Hisse');
    expect(crypto.avg).toBeCloseTo(-3);
    expect(crypto.count).toBe(1);
  });

  it('verisi olmayan tipleri listelemez (tüm değerler null)', () => {
    const items = [
      { assetType: 'BOND', changePercent: null },
      { assetType: 'BOND', changePercent: '' },
      { assetType: 'STOCK', changePercent: 1 },
    ];
    const res = calculateAverageChangeByType(items);
    expect(res.find(r => r.type === 'BOND')).toBeUndefined();
    expect(res).toHaveLength(1);
    expect(res[0].type).toBe('STOCK');
  });

  it('bilinmeyen tip için label olarak tipin kendisini kullanır', () => {
    const items = [{ assetType: 'WEIRD', changePercent: 1 }];
    const res = calculateAverageChangeByType(items);
    expect(res[0].label).toBe('WEIRD');
  });

  it('label’a göre tr alfabetik sıralar', () => {
    const items = [
      { assetType: 'FX', changePercent: 1 },    // Döviz
      { assetType: 'STOCK', changePercent: 1 }, // Hisse
      { assetType: 'CRYPTO', changePercent: 1 },// Kripto
    ];
    const labels = calculateAverageChangeByType(items).map(r => r.label);
    expect(labels).toEqual(['Döviz', 'Hisse', 'Kripto']);
  });
});

describe('hasAnyChangePercentData', () => {
  it('en az bir geçerli değer varsa true', () => {
    expect(hasAnyChangePercentData([{ changePercent: null }, { changePercent: 1 }])).toBe(true);
  });

  it('hiç geçerli değer yoksa / boş / null ise false', () => {
    expect(hasAnyChangePercentData([{ changePercent: null }, { changePercent: '' }])).toBe(false);
    expect(hasAnyChangePercentData([])).toBe(false);
    expect(hasAnyChangePercentData(null)).toBe(false);
    expect(hasAnyChangePercentData(undefined)).toBe(false);
  });
});

describe('getTopGainers', () => {
  it('null/undefined için boş dizi döner', () => {
    expect(getTopGainers(null)).toEqual([]);
    expect(getTopGainers(undefined)).toEqual([]);
  });

  it('yalnızca pozitif değişimleri büyükten küçüğe döner', () => {
    const items = [
      { symbol: 'A', changePercent: 1 },
      { symbol: 'B', changePercent: 5 },
      { symbol: 'C', changePercent: -2 }, // dışlanır (negatif)
      { symbol: 'D', changePercent: 0 },  // dışlanır (sıfır, >0 değil)
      { symbol: 'E', changePercent: null },// dışlanır (veri yok)
    ];
    const res = getTopGainers(items);
    expect(res).toHaveLength(2);
    expect(res[0].item.symbol).toBe('B');
    expect(res[0].changePercent).toBe(5);
    expect(res[1].item.symbol).toBe('A');
  });

  it('eşitlikte sembole göre alfabetik (tie-break) sıralar', () => {
    const items = [
      { symbol: 'Z', changePercent: 3 },
      { symbol: 'A', changePercent: 3 },
    ];
    const res = getTopGainers(items);
    expect(res[0].item.symbol).toBe('A');
    expect(res[1].item.symbol).toBe('Z');
  });

  it('limit parametresine uyar (varsayılan 5)', () => {
    const items = Array.from({ length: 8 }, (_, i) => ({ symbol: `S${i}`, changePercent: i + 1 }));
    expect(getTopGainers(items)).toHaveLength(5);
    expect(getTopGainers(items, 3)).toHaveLength(3);
  });

  it('symbol eksik olsa bile tie-break çökmeden çalışır', () => {
    const items = [{ changePercent: 2 }, { changePercent: 2 }];
    const res = getTopGainers(items);
    expect(res).toHaveLength(2);
  });
});

describe('getTopLosers', () => {
  it('null/undefined için boş dizi döner', () => {
    expect(getTopLosers(null)).toEqual([]);
  });

  it('yalnızca negatif değişimleri en negatiften başlayarak döner', () => {
    const items = [
      { symbol: 'A', changePercent: -1 },
      { symbol: 'B', changePercent: -5 },
      { symbol: 'C', changePercent: 2 },  // dışlanır (pozitif)
      { symbol: 'D', changePercent: 0 },  // dışlanır (sıfır)
      { symbol: 'E', changePercent: null },// dışlanır
    ];
    const res = getTopLosers(items);
    expect(res).toHaveLength(2);
    expect(res[0].item.symbol).toBe('B');
    expect(res[0].changePercent).toBe(-5);
    expect(res[1].item.symbol).toBe('A');
  });

  it('eşitlikte sembole göre alfabetik sıralar', () => {
    const items = [
      { symbol: 'Z', changePercent: -3 },
      { symbol: 'A', changePercent: -3 },
    ];
    const res = getTopLosers(items);
    expect(res[0].item.symbol).toBe('A');
  });

  it('limit parametresine uyar', () => {
    const items = Array.from({ length: 7 }, (_, i) => ({ symbol: `S${i}`, changePercent: -(i + 1) }));
    expect(getTopLosers(items)).toHaveLength(5);
    expect(getTopLosers(items, 2)).toHaveLength(2);
  });
});

describe('getWatchlistChartDisplayTitle', () => {
  it('null/undefined item için "-" döner', () => {
    expect(getWatchlistChartDisplayTitle(null)).toBe('-');
    expect(getWatchlistChartDisplayTitle(undefined)).toBe('-');
  });

  it('COMMODITY: önce değerli maden adı, sonra emtia adı, sonra sembol', () => {
    expect(getWatchlistChartDisplayTitle({ assetType: 'COMMODITY', symbol: 'SILVER:GRAM_TRY' }))
      .toBe('Gram Gümüş (₺)');
    expect(getWatchlistChartDisplayTitle({ assetType: 'COMMODITY', symbol: 'CL=F' }))
      .toBe('WTI Ham Petrol');
    // bilinmeyen sembol → sembolün kendisi
    expect(getWatchlistChartDisplayTitle({ assetType: 'COMMODITY', symbol: 'XX=F' }))
      .toBe('XX=F');
  });

  it('GOLD: bilinen kod adını, bilinmeyen kod için sembolü döner', () => {
    expect(getWatchlistChartDisplayTitle({ assetType: 'GOLD', symbol: 'GRAM' })).toBe('Gram Altın');
    expect(getWatchlistChartDisplayTitle({ assetType: 'GOLD', symbol: 'ZZZ' })).toBe('ZZZ');
  });

  it('FUND: fundName varsa onu (trim’li), yoksa sembolü döner', () => {
    expect(getWatchlistChartDisplayTitle({ assetType: 'FUND', symbol: 'AFA', fundName: '  Ak Fon  ' }))
      .toBe('Ak Fon');
    // fundName boş/whitespace → sembol
    expect(getWatchlistChartDisplayTitle({ assetType: 'FUND', symbol: 'AFA', fundName: '   ' }))
      .toBe('AFA');
    expect(getWatchlistChartDisplayTitle({ assetType: 'FUND', symbol: 'AFA' })).toBe('AFA');
  });

  it('CRYPTO: sembolü büyük harfe çevirir, boşsa "-" döner', () => {
    expect(getWatchlistChartDisplayTitle({ assetType: 'CRYPTO', symbol: ' btc ' })).toBe('BTC');
    expect(getWatchlistChartDisplayTitle({ assetType: 'CRYPTO', symbol: '   ' })).toBe('-');
    expect(getWatchlistChartDisplayTitle({ assetType: 'CRYPTO', symbol: '' })).toBe('-');
  });

  it('diğer tipler / tip yok: sembolü olduğu gibi döner', () => {
    expect(getWatchlistChartDisplayTitle({ assetType: 'STOCK', symbol: 'THYAO' })).toBe('THYAO');
    expect(getWatchlistChartDisplayTitle({ symbol: 'FOO' })).toBe('FOO');
    // sembol yoksa boş string
    expect(getWatchlistChartDisplayTitle({ assetType: 'STOCK' })).toBe('');
  });
});

describe('getWatchlistTopListSubtitle', () => {
  it('null/undefined için null döner', () => {
    expect(getWatchlistTopListSubtitle(null)).toBeNull();
    expect(getWatchlistTopListSubtitle(undefined)).toBeNull();
  });

  it('COMMODITY ve GOLD için teknik kod gösterilmez (null)', () => {
    expect(getWatchlistTopListSubtitle({ assetType: 'COMMODITY', symbol: 'CL=F' })).toBeNull();
    expect(getWatchlistTopListSubtitle({ assetType: 'GOLD', symbol: 'GRAM' })).toBeNull();
  });

  it('FUND + fundName varsa fon kodunu (trim) alt satır olarak döner', () => {
    expect(getWatchlistTopListSubtitle({ assetType: 'FUND', symbol: '  AFA  ', fundName: 'Ak Fon' }))
      .toBe('AFA');
  });

  it('FUND fundName var ama kod boşsa null döner', () => {
    expect(getWatchlistTopListSubtitle({ assetType: 'FUND', symbol: '   ', fundName: 'Ak Fon' }))
      .toBeNull();
  });

  it('FUND fundName yoksa null döner', () => {
    expect(getWatchlistTopListSubtitle({ assetType: 'FUND', symbol: 'AFA' })).toBeNull();
  });

  it('diğer tipler için null döner', () => {
    expect(getWatchlistTopListSubtitle({ assetType: 'STOCK', symbol: 'THYAO' })).toBeNull();
  });
});

describe('formatPctForChart', () => {
  it('null/undefined/NaN için "-" döner', () => {
    expect(formatPctForChart(null)).toBe('-');
    expect(formatPctForChart(undefined)).toBe('-');
    expect(formatPctForChart(NaN)).toBe('-');
  });

  it('pozitif değerlere "+" öneki ve % eki ekler (tr-TR, 2 ondalık)', () => {
    expect(formatPctForChart(2.5)).toBe('+2,50%');
    expect(formatPctForChart(0)).toBe('+0,00%'); // 0 >= 0 → "+"
  });

  it('negatif değerlerde "+" eklemez', () => {
    expect(formatPctForChart(-3.1)).toBe('-3,10%');
  });

  it('ikiden fazla ondalığı yuvarlar', () => {
    expect(formatPctForChart(1.2345)).toBe('+1,23%');
  });
});

describe('formatSharePercent', () => {
  it('sonlu olmayan değer için "-" döner', () => {
    expect(formatSharePercent(NaN)).toBe('-');
    expect(formatSharePercent(Infinity)).toBe('-');
    expect(formatSharePercent(null)).toBe('-');
    expect(formatSharePercent(undefined)).toBe('-');
  });

  it('tam 0 için "%0" döner', () => {
    expect(formatSharePercent(0)).toBe('%0');
  });

  it('|değer| < 0,005 → "%0" (ihmal edilebilir)', () => {
    expect(formatSharePercent(0.004)).toBe('%0');
    expect(formatSharePercent(-0.004)).toBe('%0');
  });

  it('0,005 <= |değer| < 0,01 → "<%0,01"', () => {
    expect(formatSharePercent(0.005)).toBe('<%0,01');
    expect(formatSharePercent(0.009)).toBe('<%0,01');
    expect(formatSharePercent(-0.006)).toBe('<%0,01');
  });

  it('99,99 < değer < 100 → "%99,99+"', () => {
    expect(formatSharePercent(99.995)).toBe('%99,99+');
  });

  it('tam 100 sahte-koruma dalına girmez, normal formatlanır', () => {
    expect(formatSharePercent(100)).toBe('%100,00');
  });

  it('normal değerleri 2 ondalıkla tr-TR formatlar', () => {
    expect(formatSharePercent(12.34)).toBe('%12,34');
    expect(formatSharePercent(50)).toBe('%50,00');
    // sınır: tam 0,01 → normal format
    expect(formatSharePercent(0.01)).toBe('%0,01');
  });
});

describe('countSharePercent', () => {
  it('total yok/sıfır/negatif ise null döner', () => {
    expect(countSharePercent(5, 0)).toBeNull();
    expect(countSharePercent(5, -1)).toBeNull();
    expect(countSharePercent(5, null)).toBeNull();
    expect(countSharePercent(5, undefined)).toBeNull();
  });

  it('count sonlu değilse null döner', () => {
    expect(countSharePercent(NaN, 10)).toBeNull();
    expect(countSharePercent(Infinity, 10)).toBeNull();
  });

  it('count/total * 100 hesaplar', () => {
    expect(countSharePercent(1, 4)).toBeCloseTo(25);
    expect(countSharePercent(3, 3)).toBeCloseTo(100);
    expect(countSharePercent(0, 5)).toBeCloseTo(0); // count=0 sonlu, total>0
  });
});

describe('formatPctAxis', () => {
  it('sonlu olmayan değer için boş string döner', () => {
    expect(formatPctAxis(NaN)).toBe('');
    expect(formatPctAxis(Infinity)).toBe('');
    expect(formatPctAxis(null)).toBe('');
  });

  it('% eki ekler ama "+" öneki EKLEMEZ', () => {
    expect(formatPctAxis(2.5)).toBe('2,5%');
    expect(formatPctAxis(-3)).toBe('-3%');
    expect(formatPctAxis(0)).toBe('0%');
  });

  it('en fazla 2 ondalığa yuvarlar (gereksiz sıfır eklemez)', () => {
    expect(formatPctAxis(1.2345)).toBe('1,23%');
    expect(formatPctAxis(5)).toBe('5%'); // trailing sıfır yok
  });
});

describe('formatInstrumentColumn', () => {
  it('isim boş veya "-" ise "-" döner', () => {
    expect(formatInstrumentColumn('AAA', '')).toBe('-');
    expect(formatInstrumentColumn('AAA', null)).toBe('-');
    expect(formatInstrumentColumn('AAA', '   ')).toBe('-');
    expect(formatInstrumentColumn('AAA', '-')).toBe('-');
  });

  it('isim sembol ile aynıysa "-" döner', () => {
    expect(formatInstrumentColumn('THYAO', 'THYAO')).toBe('-');
    // trim sonrası eşleşir
    expect(formatInstrumentColumn(' THYAO ', ' THYAO ')).toBe('-');
  });

  it('isim sembolden farklıysa trim’li ismi döner', () => {
    expect(formatInstrumentColumn('THYAO', '  Türk Hava Yolları  ')).toBe('Türk Hava Yolları');
  });

  it('sembol boşken isim varsa ismi döner (eşitlik koşulu tetiklenmez)', () => {
    expect(formatInstrumentColumn('', 'Bir İsim')).toBe('Bir İsim');
    expect(formatInstrumentColumn(null, 'Bir İsim')).toBe('Bir İsim');
  });
});

describe('formatWatchlistTablePrice', () => {
  it('null/undefined item için "-" döner', () => {
    expect(formatWatchlistTablePrice(null)).toBe('-');
    expect(formatWatchlistTablePrice(undefined)).toBe('-');
  });

  it('FX: buy/sell varsa "alış / satış" formatında 2 ondalık döner', () => {
    expect(formatWatchlistTablePrice({ assetType: 'FX', buy: 32.1, sell: 32.5 }))
      .toBe('32,10 / 32,50');
  });

  it('FX: yalnızca biri dolu olsa bile alış/satış dalına girer (diğeri "-")', () => {
    expect(formatWatchlistTablePrice({ assetType: 'FX', buy: 32.1, sell: null }))
      .toBe('32,10 / -');
    expect(formatWatchlistTablePrice({ assetType: 'FX', buy: '', sell: 9 }))
      .toBe('- / 9,00');
  });

  it('FX ama buy/sell yoksa lastPrice’a düşer', () => {
    expect(formatWatchlistTablePrice({ assetType: 'FX', buy: null, sell: '', lastPrice: 5 }))
      .toBe('5,00');
  });

  it('FX dışı tipler için lastPrice’ı 2 ondalıkla biçimler', () => {
    expect(formatWatchlistTablePrice({ assetType: 'STOCK', lastPrice: 123.456 }))
      .toBe('123,46');
    // string lastPrice parse edilir
    expect(formatWatchlistTablePrice({ assetType: 'STOCK', lastPrice: '12.5' }))
      .toBe('12,50');
  });

  it('lastPrice yok/parse edilemez ise "-" döner', () => {
    expect(formatWatchlistTablePrice({ assetType: 'STOCK' })).toBe('-');
    expect(formatWatchlistTablePrice({ assetType: 'STOCK', lastPrice: '' })).toBe('-');
    expect(formatWatchlistTablePrice({ assetType: 'STOCK', lastPrice: 'abc' })).toBe('-');
  });
});

describe('formatWatchlistSymbolDisplay', () => {
  it('null/undefined item için "-" döner', () => {
    expect(formatWatchlistSymbolDisplay(null)).toBe('-');
    expect(formatWatchlistSymbolDisplay(undefined)).toBe('-');
  });

  it('boş/whitespace sembol için "-" döner', () => {
    expect(formatWatchlistSymbolDisplay({ symbol: '' })).toBe('-');
    expect(formatWatchlistSymbolDisplay({ symbol: '   ' })).toBe('-');
    expect(formatWatchlistSymbolDisplay({})).toBe('-');
  });

  it('CRYPTO sembolünü büyük harfe çevirir', () => {
    expect(formatWatchlistSymbolDisplay({ assetType: 'CRYPTO', symbol: ' xrp ' })).toBe('XRP');
  });

  it('CRYPTO dışı tipte sembolü trim’li ama olduğu gibi döner', () => {
    expect(formatWatchlistSymbolDisplay({ assetType: 'STOCK', symbol: ' THYAO ' })).toBe('THYAO');
    expect(formatWatchlistSymbolDisplay({ symbol: 'eurusd' })).toBe('eurusd');
  });
});
