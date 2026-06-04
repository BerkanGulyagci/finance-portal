import { describe, it, expect } from 'vitest';
import {
  PORTFOLIO_ASSET_LABELS,
  CHART_DONUT_COLORS,
  parseMarketValue,
  parseTotalCost,
  parseMarketValueExtended,
  parseProfitLoss,
  parseDailyProfitLoss,
  hasAnyDailyProfitLossData,
  parseDailyChangePercent,
  parseUnrealizedChangePercent,
  parseRankingChangePercent,
  groupByAssetType,
  calculateAllocationByType,
  calculateTopHoldingsDistribution,
  calculateDailyStatus,
  hasAnyDailyChangeData,
  calculateAverageChangeByType,
  getTopGainers,
  getTopLosers,
  formatSharePercent,
  formatPctSigned,
  formatPctAxis,
  holdingDisplayName,
  assetTypeBadgeClass,
  pctToneClass,
  sumMarketValue,
  calculateCostVsMarketRows,
  calculateProfitLossByHolding,
  calculateProfitLossByType,
  calculateDailyContributionRows,
  getConcentrationRiskLevel,
  calculateConcentrationMetrics,
  truncateChartLabel,
} from '../portfolioAnalyticsHelpers';

describe('sabitler', () => {
  it('PORTFOLIO_ASSET_LABELS bilinen tüm türleri TR etiketle eşler', () => {
    expect(PORTFOLIO_ASSET_LABELS.STOCK).toBe('Hisse');
    expect(PORTFOLIO_ASSET_LABELS.BOND).toBe('DİBS');
    expect(PORTFOLIO_ASSET_LABELS.FX).toBe('Döviz');
    expect(PORTFOLIO_ASSET_LABELS.OTHER).toBe('Diğer');
  });

  it('CHART_DONUT_COLORS 8 hex renk içerir', () => {
    expect(CHART_DONUT_COLORS).toHaveLength(8);
    for (const c of CHART_DONUT_COLORS) {
      expect(c).toMatch(/^#[0-9a-f]{6}$/i);
    }
  });
});

describe('parseMarketValue', () => {
  it('marketValue alanını sayıya çevirir (string dahil)', () => {
    expect(parseMarketValue({ marketValue: '1234.5' })).toBe(1234.5);
    expect(parseMarketValue({ marketValue: 500 })).toBe(500);
  });

  it('yok / boş / geçersizse null', () => {
    expect(parseMarketValue({})).toBeNull();
    expect(parseMarketValue({ marketValue: '' })).toBeNull();
    expect(parseMarketValue({ marketValue: 'abc' })).toBeNull();
    expect(parseMarketValue(null)).toBeNull();
  });
});

describe('parseTotalCost', () => {
  it('totalCost alanını döner', () => {
    expect(parseTotalCost({ totalCost: '200' })).toBe(200);
  });
  it('yoksa null', () => {
    expect(parseTotalCost({})).toBeNull();
  });
});

describe('parseMarketValueExtended', () => {
  it('marketValue varsa onu döner', () => {
    expect(parseMarketValueExtended({ marketValue: '999' })).toBe(999);
  });

  it('marketValue yoksa miktar × güncel fiyat', () => {
    expect(parseMarketValueExtended({ totalQuantity: '10', currentPrice: '25' })).toBe(250);
  });

  it('hiçbir veri yoksa null', () => {
    expect(parseMarketValueExtended({})).toBeNull();
    // miktar var ama fiyat yok → null (her ikisi gerekli)
    expect(parseMarketValueExtended({ totalQuantity: '10' })).toBeNull();
  });
});

describe('parseProfitLoss', () => {
  it('doğrudan profitLoss / unrealizedGainLoss alanını kullanır', () => {
    expect(parseProfitLoss({ profitLoss: '42' })).toBe(42);
    expect(parseProfitLoss({ unrealizedGainLoss: '15' })).toBe(15);
  });

  it('doğrudan değer yoksa piyasa değeri − maliyet', () => {
    expect(parseProfitLoss({ marketValue: '300', totalCost: '200' })).toBe(100);
    // marketValue yok → extended fallback (qty × fiyat) − maliyet
    expect(parseProfitLoss({ totalQuantity: '10', currentPrice: '30', totalCost: '200' })).toBe(100);
  });

  it('hesaplanamıyorsa null', () => {
    expect(parseProfitLoss({})).toBeNull();
    expect(parseProfitLoss({ marketValue: '300' })).toBeNull(); // maliyet yok
  });
});

describe('parseDailyProfitLoss', () => {
  it('doğrudan günlük K/Z alanlarını kullanır', () => {
    expect(parseDailyProfitLoss({ dailyGainLoss: '12' })).toBe(12);
    expect(parseDailyProfitLoss({ dailyPnl: '7' })).toBe(7);
    expect(parseDailyProfitLoss({ dailyProfitLoss: '3' })).toBe(3);
    expect(parseDailyProfitLoss({ dayGain: '9' })).toBe(9);
  });

  it('doğrudan yoksa qty × perShare (dailyChangeAmount/change)', () => {
    expect(parseDailyProfitLoss({ totalQuantity: '10', dailyChangeAmount: '2' })).toBe(20);
    expect(parseDailyProfitLoss({ totalQuantity: '5', change: '4' })).toBe(20);
  });

  it('perShare yoksa qty × (güncel fiyat − önceki kapanış)', () => {
    expect(parseDailyProfitLoss({ totalQuantity: '10', currentPrice: '12', previousClose: '10' })).toBe(20);
    expect(parseDailyProfitLoss({ totalQuantity: '10', currentPrice: '12', prevClose: '11' })).toBe(10);
  });

  it('hiçbir veri yoksa null', () => {
    expect(parseDailyProfitLoss({})).toBeNull();
    expect(parseDailyProfitLoss({ totalQuantity: '10' })).toBeNull();
  });
});

describe('hasAnyDailyProfitLossData', () => {
  it('en az bir holding günlük K/Z verisi varsa true', () => {
    expect(hasAnyDailyProfitLossData([{ dailyGainLoss: '5' }, {}])).toBe(true);
  });
  it('hiç veri yoksa / boş / null → false', () => {
    expect(hasAnyDailyProfitLossData([{}, { foo: 1 }])).toBe(false);
    expect(hasAnyDailyProfitLossData([])).toBe(false);
    expect(hasAnyDailyProfitLossData(null)).toBe(false);
  });
});

describe('parseDailyChangePercent', () => {
  it('bilinen yüzde alanlarını döner', () => {
    expect(parseDailyChangePercent({ dailyGainLossPercent: '1.5' })).toBe(1.5);
    expect(parseDailyChangePercent({ changePercent: '-2' })).toBe(-2);
    expect(parseDailyChangePercent({ returnOneDay: '0.3' })).toBeCloseTo(0.3, 5);
  });
  it('yoksa null', () => {
    expect(parseDailyChangePercent({})).toBeNull();
  });
});

describe('parseUnrealizedChangePercent', () => {
  it('doğrudan yüzde alanını kullanır', () => {
    expect(parseUnrealizedChangePercent({ profitLossPercent: '12' })).toBe(12);
    expect(parseUnrealizedChangePercent({ unrealizedGainLossPercent: '8' })).toBe(8);
  });

  it('doğrudan yoksa (mv − cost) / cost × 100', () => {
    expect(parseUnrealizedChangePercent({ marketValue: '150', totalCost: '100' })).toBeCloseTo(50, 5);
  });

  it('maliyet 0/negatif/eksik → null (sıfıra bölme koruması)', () => {
    expect(parseUnrealizedChangePercent({ marketValue: '150', totalCost: '0' })).toBeNull();
    expect(parseUnrealizedChangePercent({ marketValue: '150', totalCost: '-5' })).toBeNull();
    expect(parseUnrealizedChangePercent({ marketValue: '150' })).toBeNull();
    expect(parseUnrealizedChangePercent({})).toBeNull();
  });
});

describe('parseRankingChangePercent', () => {
  it('önce günlük %, varsa onu döner', () => {
    expect(parseRankingChangePercent({ changePercent: '3', marketValue: '150', totalCost: '100' })).toBe(3);
  });
  it('günlük % yoksa açık K/Z %', () => {
    expect(parseRankingChangePercent({ marketValue: '150', totalCost: '100' })).toBeCloseTo(50, 5);
  });
  it('hiçbiri yoksa null', () => {
    expect(parseRankingChangePercent({})).toBeNull();
  });
});

describe('groupByAssetType', () => {
  it('assetType’e göre gruplar', () => {
    const map = groupByAssetType([
      { assetType: 'STOCK', symbol: 'A' },
      { assetType: 'STOCK', symbol: 'B' },
      { assetType: 'CRYPTO', symbol: 'C' },
    ]);
    expect(map.STOCK).toHaveLength(2);
    expect(map.CRYPTO).toHaveLength(1);
  });

  it('assetType yoksa OTHER kovasına koyar', () => {
    const map = groupByAssetType([{ symbol: 'X' }]);
    expect(map.OTHER).toHaveLength(1);
  });

  it('null/boş giriş → boş obje', () => {
    expect(groupByAssetType(null)).toEqual({});
    expect(groupByAssetType([])).toEqual({});
  });
});

describe('calculateAllocationByType', () => {
  it('tür bazında değer toplar, sharePct hesaplar, değere göre sıralar', () => {
    const { rows, total } = calculateAllocationByType([
      { assetType: 'STOCK', marketValue: '300' },
      { assetType: 'CRYPTO', marketValue: '100' },
    ]);
    expect(total).toBe(400);
    // en büyük (STOCK) önce
    expect(rows[0].type).toBe('STOCK');
    expect(rows[0].name).toBe('Hisse');
    expect(rows[0].sharePct).toBeCloseTo(75, 5);
    expect(rows[1].sharePct).toBeCloseTo(25, 5);
  });

  it('marketValue yoksa qty × fiyat fallback ile değer üretir', () => {
    const { total } = calculateAllocationByType([
      { assetType: 'STOCK', totalQuantity: '10', currentPrice: '5' },
    ]);
    expect(total).toBe(50);
  });

  it('değeri 0/negatif olan türü atlar', () => {
    const { rows, total } = calculateAllocationByType([
      { assetType: 'STOCK', marketValue: '0' },
      { assetType: 'CRYPTO', marketValue: '100' },
    ]);
    expect(total).toBe(100);
    expect(rows).toHaveLength(1);
    expect(rows[0].type).toBe('CRYPTO');
  });

  it('bilinmeyen tür için etiket = türün kendisi', () => {
    const { rows } = calculateAllocationByType([{ assetType: 'XYZ', marketValue: '10' }]);
    expect(rows[0].name).toBe('XYZ');
  });

  it('boş / null giriş → boş', () => {
    expect(calculateAllocationByType([])).toEqual({ rows: [], total: 0 });
    expect(calculateAllocationByType(null)).toEqual({ rows: [], total: 0 });
  });
});

describe('calculateTopHoldingsDistribution', () => {
  it('en büyük N pozisyon + kalanı "Diğer" olarak toplar', () => {
    const holdings = [
      { symbol: 'A', marketValue: '500' },
      { symbol: 'B', marketValue: '300' },
      { symbol: 'C', marketValue: '100' },
      { symbol: 'D', marketValue: '50' },
    ];
    const { rows, total } = calculateTopHoldingsDistribution(holdings, 2);
    expect(total).toBe(950);
    expect(rows).toHaveLength(3); // 2 top + Diğer
    expect(rows[0].name).toBe('A');
    expect(rows[2].name).toBe('Diğer');
    expect(rows[2].value).toBe(150); // 100 + 50
    expect(rows[2].type).toBe('OTHER');
  });

  it('kalan yoksa "Diğer" satırı eklenmez', () => {
    const { rows } = calculateTopHoldingsDistribution([{ symbol: 'A', marketValue: '10' }], 5);
    expect(rows).toHaveLength(1);
    expect(rows.some(r => r.name === 'Diğer')).toBe(false);
  });

  it('name === symbol ise sembolü, farklıysa adı etiket yapar', () => {
    const { rows } = calculateTopHoldingsDistribution([
      { symbol: 'THYAO', name: 'Türk Hava Yolları', marketValue: '10' },
    ]);
    expect(rows[0].name).toBe('Türk Hava Yolları');
  });

  it('geçerli marketValue yoksa boş döner', () => {
    expect(calculateTopHoldingsDistribution([{ symbol: 'A' }])).toEqual({ rows: [], total: 0 });
    expect(calculateTopHoldingsDistribution(null)).toEqual({ rows: [], total: 0 });
  });
});

describe('calculateDailyStatus', () => {
  it('yükselen/düşen/yatay/veri-yok sayar', () => {
    const res = calculateDailyStatus([
      { changePercent: '1' },
      { changePercent: '-2' },
      { changePercent: '0' },
      {},
    ]);
    expect(res).toEqual({ total: 4, up: 1, down: 1, flat: 1, nodata: 1 });
  });

  it('null/boş giriş → tümü 0', () => {
    expect(calculateDailyStatus(null)).toEqual({ total: 0, up: 0, down: 0, flat: 0, nodata: 0 });
  });
});

describe('hasAnyDailyChangeData', () => {
  it('en az bir günlük % varsa true', () => {
    expect(hasAnyDailyChangeData([{ changePercent: '1' }, {}])).toBe(true);
  });
  it('yoksa / null → false', () => {
    expect(hasAnyDailyChangeData([{}])).toBe(false);
    expect(hasAnyDailyChangeData(null)).toBe(false);
  });
});

describe('calculateAverageChangeByType', () => {
  it('tür bazında günlük % ortalaması + adet', () => {
    const out = calculateAverageChangeByType([
      { assetType: 'STOCK', changePercent: '2' },
      { assetType: 'STOCK', changePercent: '4' },
      { assetType: 'CRYPTO', changePercent: '-1' },
    ]);
    const stock = out.find(o => o.type === 'STOCK');
    expect(stock.avg).toBeCloseTo(3, 5);
    expect(stock.count).toBe(2);
    expect(stock.label).toBe('Hisse');
  });

  it('etikete göre TR sıralama yapar', () => {
    const out = calculateAverageChangeByType([
      { assetType: 'STOCK', changePercent: '2' }, // Hisse
      { assetType: 'CRYPTO', changePercent: '1' }, // Kripto
    ]);
    // Hisse < Kripto (TR alfabetik)
    expect(out[0].label).toBe('Hisse');
    expect(out[1].label).toBe('Kripto');
  });

  it('hiç günlük % verisi olmayan türü atlar', () => {
    const out = calculateAverageChangeByType([{ assetType: 'STOCK' }]);
    expect(out).toEqual([]);
  });

  it('null giriş → boş', () => {
    expect(calculateAverageChangeByType(null)).toEqual([]);
  });
});

describe('getTopGainers', () => {
  it('pozitif değişimleri büyükten küçüğe, limitle döner', () => {
    const res = getTopGainers([
      { symbol: 'A', changePercent: '5' },
      { symbol: 'B', changePercent: '10' },
      { symbol: 'C', changePercent: '-3' },
    ], 5);
    expect(res).toHaveLength(2); // negatif elenir
    expect(res[0].holding.symbol).toBe('B');
    expect(res[0].changePercent).toBe(10);
  });

  it('limit uygular', () => {
    const res = getTopGainers([
      { symbol: 'A', changePercent: '5' },
      { symbol: 'B', changePercent: '10' },
    ], 1);
    expect(res).toHaveLength(1);
    expect(res[0].holding.symbol).toBe('B');
  });

  it('pozitif yoksa / null → boş', () => {
    expect(getTopGainers([{ symbol: 'A', changePercent: '-1' }])).toEqual([]);
    expect(getTopGainers(null)).toEqual([]);
  });
});

describe('getTopLosers', () => {
  it('negatif değişimleri küçükten büyüğe (en kötü önce) döner', () => {
    const res = getTopLosers([
      { symbol: 'A', changePercent: '-5' },
      { symbol: 'B', changePercent: '-10' },
      { symbol: 'C', changePercent: '3' },
    ]);
    expect(res).toHaveLength(2);
    expect(res[0].holding.symbol).toBe('B'); // en çok düşen
    expect(res[0].changePercent).toBe(-10);
  });

  it('negatif yoksa / null → boş', () => {
    expect(getTopLosers([{ symbol: 'A', changePercent: '1' }])).toEqual([]);
    expect(getTopLosers(null)).toEqual([]);
  });
});

describe('formatSharePercent', () => {
  it('değerler gizliyse maskeler', () => {
    expect(formatSharePercent(50, true)).toBe('••••');
  });

  it('sonsuz/NaN → "-"', () => {
    expect(formatSharePercent(Infinity)).toBe('-');
    expect(formatSharePercent(NaN)).toBe('-');
  });

  it('tam 0 → "%0"', () => {
    expect(formatSharePercent(0)).toBe('%0');
  });

  it('< 0,005 → "%0" (ihmal edilebilir, ondalık gizlenir)', () => {
    expect(formatSharePercent(0.004)).toBe('%0');
  });

  it('0,005–0,01 arası → "<%0,01" (yuvarlama 0 göstermesin)', () => {
    expect(formatSharePercent(0.008)).toBe('<%0,01');
  });

  it('> 99,99 ama < 100 → "%99,99+" (sahte 100% koruması)', () => {
    expect(formatSharePercent(99.9974)).toBe('%99,99+');
  });

  it('normal değer → 2 ondalıklı TR format (virgül ayraç)', () => {
    expect(formatSharePercent(12.34)).toBe('%12,34');
    expect(formatSharePercent(100)).toBe('%100,00'); // tam 100 normal yola düşer
  });
});

describe('formatPctSigned', () => {
  it('değer gizliyse maske', () => {
    expect(formatPctSigned(5, true)).toBe('••••');
  });
  it('null/NaN → "-"', () => {
    expect(formatPctSigned(null)).toBe('-');
    expect(formatPctSigned(NaN)).toBe('-');
  });
  it('pozitifte + işareti, suffix %', () => {
    expect(formatPctSigned(3.2)).toBe('+3,20%');
  });
  it('negatifte işaret eklenmez (sayı kendi -’sini taşır)', () => {
    expect(formatPctSigned(-3.2)).toBe('-3,20%');
  });
  it('0 → +0,00% (sıfır >= 0 sayılır)', () => {
    expect(formatPctSigned(0)).toBe('+0,00%');
  });
});

describe('formatPctAxis', () => {
  it('en fazla 2 ondalık + % (zorunlu ondalık yok)', () => {
    expect(formatPctAxis(5)).toBe('5%');
    expect(formatPctAxis(5.5)).toBe('5,5%');
  });
  it('sonsuz/NaN → boş string', () => {
    expect(formatPctAxis(Infinity)).toBe('');
    expect(formatPctAxis(NaN)).toBe('');
  });
});

describe('holdingDisplayName', () => {
  it('ad sembolden farklıysa adı döner', () => {
    expect(holdingDisplayName({ name: 'Türk Hava Yolları', symbol: 'THYAO' })).toBe('Türk Hava Yolları');
  });
  it('ad yoksa / sembole eşitse sembolü döner', () => {
    expect(holdingDisplayName({ symbol: 'THYAO' })).toBe('THYAO');
    expect(holdingDisplayName({ name: 'THYAO', symbol: 'THYAO' })).toBe('THYAO');
  });
  it('hiçbiri yoksa / null → "—"', () => {
    expect(holdingDisplayName({})).toBe('—');
    expect(holdingDisplayName(null)).toBe('—');
  });
  it('ad/sembol baştaki-sondaki boşluğu kırpılır', () => {
    expect(holdingDisplayName({ name: '  Apple  ', symbol: 'AAPL' })).toBe('Apple');
  });
});

describe('assetTypeBadgeClass', () => {
  it('her bilinen tür için sınıf döner', () => {
    expect(assetTypeBadgeClass('STOCK')).toContain('sky');
    expect(assetTypeBadgeClass('CRYPTO')).toContain('violet');
    expect(assetTypeBadgeClass('FUND')).toContain('indigo');
    expect(assetTypeBadgeClass('COMMODITY')).toContain('amber');
    expect(assetTypeBadgeClass('GOLD')).toContain('yellow');
    expect(assetTypeBadgeClass('FX')).toContain('teal');
    expect(assetTypeBadgeClass('BOND')).toContain('slate');
    expect(assetTypeBadgeClass('FUTURE')).toContain('orange');
  });
  it('bilinmeyen / undefined → gri default', () => {
    expect(assetTypeBadgeClass('XYZ')).toContain('gray');
    expect(assetTypeBadgeClass(undefined)).toContain('gray');
  });
});

describe('pctToneClass', () => {
  it('pozitif → emerald, negatif → rose, 0 → gray-500', () => {
    expect(pctToneClass(1)).toContain('emerald');
    expect(pctToneClass(-1)).toContain('rose');
    expect(pctToneClass(0)).toContain('gray-500');
  });
  it('null/NaN → gray-400', () => {
    expect(pctToneClass(null)).toContain('gray-400');
    expect(pctToneClass(NaN)).toContain('gray-400');
  });
});

describe('sumMarketValue', () => {
  it('pozitif piyasa değerlerini toplar (fallback dahil)', () => {
    expect(sumMarketValue([
      { marketValue: '100' },
      { totalQuantity: '10', currentPrice: '5' }, // 50
    ])).toBe(150);
  });
  it('hiç geçerli değer yoksa / null → 0', () => {
    expect(sumMarketValue([{ marketValue: '0' }, {}])).toBe(0);
    expect(sumMarketValue(null)).toBe(0);
  });
});

describe('calculateCostVsMarketRows', () => {
  it('maliyet/piyasa satırları üretir, piyasa değerine göre sıralar', () => {
    const { rows, total, truncated } = calculateCostVsMarketRows([
      { symbol: 'A', totalCost: '100', marketValue: '120' },
      { symbol: 'B', totalCost: '50', marketValue: '300' },
    ]);
    expect(total).toBe(2);
    expect(truncated).toBe(false);
    expect(rows[0].name).toBe('B'); // büyük mv önce
    expect(rows[0].cost).toBe(50);
    expect(rows[0].marketValue).toBe(300);
  });

  it('maliyet ve piyasa ikisi de <= 0 olan satırı eler', () => {
    const { rows } = calculateCostVsMarketRows([{ symbol: 'A' }]);
    expect(rows).toHaveLength(0);
  });

  it('maxRows üstünde truncated=true ve kırpar', () => {
    const many = Array.from({ length: 5 }, (_, i) => ({ symbol: `S${i}`, marketValue: String(i + 1) }));
    const { rows, truncated, total } = calculateCostVsMarketRows(many, 3);
    expect(total).toBe(5);
    expect(rows).toHaveLength(3);
    expect(truncated).toBe(true);
  });

  it('id yoksa key olarak symbol kullanır', () => {
    const { rows } = calculateCostVsMarketRows([{ symbol: 'A', marketValue: '10' }]);
    expect(rows[0].key).toBe('A');
  });

  it('null giriş → boş', () => {
    expect(calculateCostVsMarketRows(null)).toEqual({ rows: [], truncated: false, total: 0 });
  });
});

describe('calculateProfitLossByHolding', () => {
  it('açık K/Z satırlarını mutlak değere göre sıralar', () => {
    const { rows } = calculateProfitLossByHolding([
      { symbol: 'A', profitLoss: '10' },
      { symbol: 'B', profitLoss: '-50' },
    ]);
    expect(rows[0].name).toBe('B'); // |−50| > |10|
    expect(rows[0].profitLoss).toBe(-50);
  });

  it('K/Z hesaplanamayan satırı eler', () => {
    const { rows } = calculateProfitLossByHolding([{ symbol: 'A' }]);
    expect(rows).toHaveLength(0);
  });

  it('maxRows kırpma + truncated', () => {
    const many = Array.from({ length: 4 }, (_, i) => ({ symbol: `S${i}`, profitLoss: String(i + 1) }));
    const { rows, truncated, total } = calculateProfitLossByHolding(many, 2);
    expect(total).toBe(4);
    expect(rows).toHaveLength(2);
    expect(truncated).toBe(true);
  });

  it('null giriş → boş', () => {
    expect(calculateProfitLossByHolding(null)).toEqual({ rows: [], truncated: false, total: 0 });
  });
});

describe('calculateProfitLossByType', () => {
  it('tür bazında K/Z toplar, mutlak değere göre sıralar', () => {
    const rows = calculateProfitLossByType([
      { assetType: 'STOCK', profitLoss: '10' },
      { assetType: 'STOCK', profitLoss: '20' },
      { assetType: 'CRYPTO', profitLoss: '-100' },
    ]);
    expect(rows[0].type).toBe('CRYPTO'); // |−100| en büyük
    expect(rows[0].profitLoss).toBe(-100);
    const stock = rows.find(r => r.type === 'STOCK');
    expect(stock.profitLoss).toBe(30);
    expect(stock.label).toBe('Hisse');
  });

  it('K/Z verisi olmayan türü atlar', () => {
    const rows = calculateProfitLossByType([{ assetType: 'STOCK' }]);
    expect(rows).toEqual([]);
  });

  it('null giriş → boş', () => {
    expect(calculateProfitLossByType(null)).toEqual([]);
  });
});

describe('calculateDailyContributionRows', () => {
  it('günlük K/Z katkısını mutlak değere göre sıralar', () => {
    const { rows } = calculateDailyContributionRows([
      { symbol: 'A', dailyGainLoss: '5' },
      { symbol: 'B', dailyGainLoss: '-30' },
    ]);
    expect(rows[0].name).toBe('B');
    expect(rows[0].daily).toBe(-30);
  });

  it('günlük K/Z olmayan satırı eler', () => {
    const { rows } = calculateDailyContributionRows([{ symbol: 'A' }]);
    expect(rows).toHaveLength(0);
  });

  it('maxRows kırpma + truncated', () => {
    const many = Array.from({ length: 4 }, (_, i) => ({ symbol: `S${i}`, dailyGainLoss: String(i + 1) }));
    const { rows, truncated, total } = calculateDailyContributionRows(many, 2);
    expect(total).toBe(4);
    expect(rows).toHaveLength(2);
    expect(truncated).toBe(true);
  });

  it('null giriş → boş', () => {
    expect(calculateDailyContributionRows(null)).toEqual({ rows: [], truncated: false, total: 0 });
  });
});

describe('getConcentrationRiskLevel', () => {
  it('>= 70 yüksek, >= 40 orta, altı dengeli', () => {
    expect(getConcentrationRiskLevel(80).level).toBe('high');
    expect(getConcentrationRiskLevel(70).level).toBe('high');
    expect(getConcentrationRiskLevel(50).level).toBe('medium');
    expect(getConcentrationRiskLevel(40).level).toBe('medium');
    expect(getConcentrationRiskLevel(10).level).toBe('low');
  });
  it('null/NaN → null', () => {
    expect(getConcentrationRiskLevel(null)).toBeNull();
    expect(getConcentrationRiskLevel(NaN)).toBeNull();
  });
});

describe('calculateConcentrationMetrics', () => {
  it('toplam mv, en büyük pozisyon/kategori, sayılar ve risk üretir', () => {
    const res = calculateConcentrationMetrics([
      { assetType: 'STOCK', symbol: 'A', name: 'Apple', marketValue: '800' },
      { assetType: 'CRYPTO', symbol: 'B', marketValue: '200' },
    ]);
    expect(res.totalMv).toBe(1000);
    expect(res.positionCount).toBe(2);
    expect(res.typeCount).toBe(2);
    expect(res.topPosition.name).toBe('Apple');
    expect(res.topPosition.sharePct).toBeCloseTo(80, 5);
    expect(res.topCategory.name).toBe('Hisse'); // STOCK en büyük tür
    expect(res.topCategory.sharePct).toBeCloseTo(80, 5);
    expect(res.risk.level).toBe('high'); // %80 >= 70
    expect(res.hasData).toBe(true);
  });

  it('geçerli pozisyon yoksa boş metrikler (hasData false)', () => {
    const res = calculateConcentrationMetrics([{ symbol: 'A' }]);
    expect(res.totalMv).toBe(0);
    expect(res.topPosition).toBeNull();
    expect(res.topCategory).toBeNull();
    expect(res.positionCount).toBe(0);
    expect(res.risk).toBeNull();
    expect(res.hasData).toBe(false);
  });

  it('null giriş → güvenli boş sonuç', () => {
    const res = calculateConcentrationMetrics(null);
    expect(res.totalMv).toBe(0);
    expect(res.hasData).toBe(false);
  });
});

describe('truncateChartLabel', () => {
  it('max altındaki adı olduğu gibi döner', () => {
    expect(truncateChartLabel('Kısa', 16)).toBe('Kısa');
  });
  it('max üstünde kısaltır ve … ekler', () => {
    // 20 karakter, max 10 → ilk 9 + …
    const out = truncateChartLabel('ABCDEFGHIJKLMNOPQRST', 10);
    expect(out).toBe('ABCDEFGHI…');
    expect(out).toHaveLength(10);
  });
  it('boş / null → "—"', () => {
    expect(truncateChartLabel('')).toBe('—');
    expect(truncateChartLabel(null)).toBe('—');
  });
});
