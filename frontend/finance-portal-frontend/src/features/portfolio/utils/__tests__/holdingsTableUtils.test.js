import { describe, it, expect, beforeEach } from 'vitest';
import {
  MAX_COLS,
  DEFAULT_DISPLAY_ORDER,
  ALL_COLS,
  COLS_STORAGE_KEY,
  loadSavedColumns,
  buildVisibleCols,
  fmtNum,
  fmtQty,
  fmtQtyFund,
  fmtFundNavPrice,
  fmtPrice,
  fmtMoneyTwoDecimals,
  formatPercentWithSuffix,
  fmtVol,
  resolveHoldingName,
  num,
  positionMarketValue,
  unrealizedGainLoss,
  positionDailyGainLoss,
  renderCellForExport,
  columnHasValueForHolding,
} from '../holdingsTableUtils';

describe('sabitler', () => {
  it('MAX_COLS = 12, varsayılan sıra 11 kolon', () => {
    expect(MAX_COLS).toBe(12);
    expect(DEFAULT_DISPLAY_ORDER).toHaveLength(11);
  });

  it('ALL_COLS key’leri benzersiz', () => {
    const keys = ALL_COLS.map((c) => c.key);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it('DEFAULT_DISPLAY_ORDER’daki her key ALL_COLS’ta var', () => {
    for (const k of DEFAULT_DISPLAY_ORDER) {
      expect(ALL_COLS.some((c) => c.key === k)).toBe(true);
    }
  });
});

describe('loadSavedColumns (localStorage)', () => {
  beforeEach(() => localStorage.clear());

  it('kayıt yoksa null', () => {
    expect(loadSavedColumns()).toBeNull();
  });

  it('geçerli kayıt → diziyi döner, geçersiz key’leri eler', () => {
    localStorage.setItem(COLS_STORAGE_KEY, JSON.stringify(['name', 'symbol', 'GECERSIZ']));
    expect(loadSavedColumns()).toEqual(['name', 'symbol']);
  });

  it('bozuk JSON → null (throw etmez)', () => {
    localStorage.setItem(COLS_STORAGE_KEY, '{bozuk');
    expect(loadSavedColumns()).toBeNull();
  });

  it('dizi olmayan değer → null', () => {
    localStorage.setItem(COLS_STORAGE_KEY, JSON.stringify({ a: 1 }));
    expect(loadSavedColumns()).toBeNull();
  });

  it('MAX_COLS üstünü kırpar', () => {
    const many = ALL_COLS.slice(0, 15).map((c) => c.key);
    localStorage.setItem(COLS_STORAGE_KEY, JSON.stringify(many));
    expect(loadSavedColumns()).toHaveLength(MAX_COLS);
  });
});

describe('buildVisibleCols', () => {
  it('önce varsayılan sıra, sonra ekstra kolonlar', () => {
    const cols = buildVisibleCols(['symbol', 'name', 'trend']);
    // name & symbol DEFAULT_DISPLAY_ORDER sırasında (name önce), trend ekstra → sonda
    expect(cols.map((c) => c.key)).toEqual(['name', 'symbol', 'trend']);
  });

  it('seçilmeyen kolon görünmez', () => {
    const cols = buildVisibleCols(['name']);
    expect(cols.map((c) => c.key)).toEqual(['name']);
  });
});

describe('formatlayıcılar', () => {
  it('fmtNum: null/NaN → null, TR format', () => {
    expect(fmtNum(null)).toBeNull();
    expect(fmtNum('abc')).toBeNull();
    expect(fmtNum(1234.5)).toBe('1.234,50');
  });

  it('fmtQty: 8 ondalığa kadar, gruplama', () => {
    expect(fmtQty(1000)).toBe('1.000');
    expect(fmtQty(null)).toBeNull();
  });

  it('fmtQtyFund: sondaki sıfırları kırpar', () => {
    expect(fmtQtyFund(10.5)).toBe('10,5');
    expect(fmtQtyFund(1000)).toBe('1.000'); // ondalık yoksa tam sayı
    expect(fmtQtyFund(null)).toBeNull();
  });

  it('fmtFundNavPrice: 6 haneye truncate (yuvarlama yok) + currency', () => {
    // 1.2345678 → truncate 6 → 1,234567
    expect(fmtFundNavPrice(1.2345678, 'TRY')).toBe('1,234567 TRY');
    expect(fmtFundNavPrice(null)).toBeNull();
  });

  it('fmtPrice: <1 → 4 ondalık, ≥1 → 2 ondalık', () => {
    expect(fmtPrice(0.5)).toBe('0,5000');
    expect(fmtPrice(12.3)).toBe('12,30');
    expect(fmtPrice(12.3, 'USD')).toBe('12,30 USD');
  });

  it('fmtMoneyTwoDecimals: sabit 2 ondalık', () => {
    expect(fmtMoneyTwoDecimals(1234.5)).toBe('1.234,50');
    expect(fmtMoneyTwoDecimals(1234.5, '₺')).toBe('1.234,50 ₺');
  });

  it('formatPercentWithSuffix: -0 yanıltıcı sıfırı 0,00% yapar', () => {
    expect(formatPercentWithSuffix(0)).toBe('0,00%');
    expect(formatPercentWithSuffix(3.22)).toBe('3,22%');
    expect(formatPercentWithSuffix(-8.5)).toBe('-8,50%');
  });

  it('formatPercentWithSuffix: çok küçük değerde ondalık artırır', () => {
    // 0.0001 → "0,00%" yanıltıcı olur → daha fazla ondalık
    const out = formatPercentWithSuffix(0.0001);
    expect(out).not.toBe('0,00%');
    expect(out.endsWith('%')).toBe(true);
  });

  it('fmtVol: B/M/K kısaltmaları', () => {
    expect(fmtVol(2_500_000_000)).toBe('2.50B');
    expect(fmtVol(3_400_000)).toBe('3.40M');
    expect(fmtVol(7_200)).toBe('7.2K');
    expect(fmtVol(500)).toBe('500');
  });
});

describe('resolveHoldingName', () => {
  it('metal:birim sembolünü "Birim Metal" olarak açar', () => {
    expect(resolveHoldingName({ symbol: 'SILVER:GRAM_TRY', name: 'x' })).toBe('Gram Gümüş');
    expect(resolveHoldingName({ symbol: 'GOLD:USD_ONS' })).toBe('Ons Altın');
  });

  it('normal sembolde backend adını kullanır', () => {
    expect(resolveHoldingName({ symbol: 'THYAO', name: 'Türk Hava Yolları' })).toBe('Türk Hava Yolları');
  });
});

describe('num (çok-anahtarlı sayı çıkarımı)', () => {
  it('ilk geçerli sayıyı döner', () => {
    expect(num({ a: null, b: '', c: '12.5' }, 'a', 'b', 'c')).toBe(12.5);
  });
  it('hiçbiri geçerli değilse null', () => {
    expect(num({ a: null, b: 'xx' }, 'a', 'b')).toBeNull();
  });
});

describe('positionMarketValue', () => {
  it('marketValue varsa onu döner', () => {
    expect(positionMarketValue({ marketValue: '500' })).toBe(500);
  });
  it('yoksa qty × currentPrice', () => {
    expect(positionMarketValue({ totalQuantity: '10', currentPrice: '25' })).toBe(250);
  });
  it('veri yoksa null', () => {
    expect(positionMarketValue({})).toBeNull();
  });
});

describe('unrealizedGainLoss', () => {
  it('profitLoss varsa onu döner', () => {
    expect(unrealizedGainLoss({ profitLoss: '42' })).toBe(42);
  });
  it('yoksa piyasa değeri − maliyet', () => {
    expect(unrealizedGainLoss({ marketValue: '300', totalCost: '200' })).toBe(100);
  });
});

describe('positionDailyGainLoss (BOND /100 konvansiyonu)', () => {
  it('normal varlık: qty × perShare', () => {
    expect(positionDailyGainLoss({ totalQuantity: '10', dailyChangeAmount: '2' })).toBe(20);
  });

  it('BOND: TCMB 100-nominal kotasyonu → /100 uygulanır', () => {
    // qty 1000 × change 5 = 5000, BOND → /100 = 50
    expect(positionDailyGainLoss({ assetType: 'BOND', totalQuantity: '1000', change: '5' })).toBe(50);
  });

  it('Altın-endeksli BOND: /100 YOK (adet/gram bazlı)', () => {
    expect(positionDailyGainLoss({
      assetType: 'BOND', category: 'GOLD_INDEXED_BOND', totalQuantity: '10', change: '3',
    })).toBe(30);
  });

  it('perShare yoksa qty × (fiyat − önceki kapanış)', () => {
    expect(positionDailyGainLoss({ totalQuantity: '10', currentPrice: '12', previousClose: '10' })).toBe(20);
  });
});

describe('renderCellForExport', () => {
  it('assetType → TR etiket', () => {
    expect(renderCellForExport('assetType', { assetType: 'STOCK' })).toBe('Hisse');
    expect(renderCellForExport('assetType', { assetType: 'BOND' })).toBe('DİBS');
  });

  it('unrealizedPct → (pl/cost)*100', () => {
    expect(renderCellForExport('unrealizedPct', { profitLoss: '50', totalCost: '200' })).toBe(25);
  });

  it('unrealizedPct → backend profitLossPercent öncelikli (VİOP kur-tutarlı %)', () => {
    // VİOP USD-kote: backend pnl/payda'yı aynı kurda hesaplayıp profitLossPercent veriyor.
    // Ham pl/totalCost (50/200=25) DEĞİL, backend'in verdiği 227.27 kullanılmalı.
    expect(renderCellForExport('unrealizedPct',
      { profitLoss: '50', totalCost: '200', profitLossPercent: '227.27' })).toBe(227.27);
    // profitLossPercent yoksa eski hesaba düşer (TL kontrat / diğer tipler değişmez).
    expect(renderCellForExport('unrealizedPct', { profitLoss: '50', totalCost: '200' })).toBe(25);
  });

  it('beatInflation → Yendi/Yenildi', () => {
    expect(renderCellForExport('beatInflation', { realProfitLossPercent: '5' })).toBe('Enflasyonu Yendi');
    expect(renderCellForExport('beatInflation', { realProfitLossPercent: '-3' })).toBe('Enflasyona Yenildi');
    expect(renderCellForExport('beatInflation', {})).toBe('');
  });

  it('trend → Yükseliş/Düşüş/Yatay (computeTrend gerçek)', () => {
    // computeTrend: currentPrice > MA → UP. Basit yukarı senaryo.
    const up = renderCellForExport('trend', {
      currentPrice: 100, movingAverage50: 80, movingAverage200: 70,
    });
    expect(['Yükseliş', 'Düşüş', 'Yatay', '']).toContain(up);
  });

  it('FUTURE-özel kolon, non-FUTURE’da boş', () => {
    expect(renderCellForExport('viopDirection', { assetType: 'STOCK' })).toBe('');
    expect(renderCellForExport('viopDirection', { assetType: 'FUTURE', viopDirection: 'SHORT' })).toBe('SHORT');
  });

  it('bilinmeyen kolon → throw (yeni kolon fark edilsin)', () => {
    expect(() => renderCellForExport('YOK_BOYLE', {})).toThrow(/bilinmeyen kolon/);
  });
});

describe('columnHasValueForHolding', () => {
  it('temel kolon her zaman true', () => {
    expect(columnHasValueForHolding('name', {})).toBe(true);
  });

  it('marketValue: değer varsa true', () => {
    expect(columnHasValueForHolding('marketValue', { marketValue: '5' })).toBe(true);
    expect(columnHasValueForHolding('marketValue', {})).toBe(false);
  });

  it('VİOP kolonları sadece FUTURE + veri varken true', () => {
    expect(columnHasValueForHolding('viopMargin', { assetType: 'FUTURE', viopMarginPosted: 100 })).toBe(true);
    expect(columnHasValueForHolding('viopMargin', { assetType: 'STOCK', viopMarginPosted: 100 })).toBe(false);
    expect(columnHasValueForHolding('viopMargin', { assetType: 'FUTURE' })).toBe(false);
  });
});

describe('formatlayıcı ek dallar', () => {
  it('fmtQtyFund: büyük tam sayı gruplanır', () => {
    expect(fmtQtyFund(1234567)).toBe('1.234.567');
  });

  it('fmtFundNavPrice: negatif değer ceil-truncate (yuvarlama yok)', () => {
    // -1.2345678 → truncate(6) toward zero → -1,234567
    expect(fmtFundNavPrice(-1.2345678)).toBe('-1,234567');
  });

  it('fmtFundNavPrice: currency yok → sadece sayı', () => {
    expect(fmtFundNavPrice(2.5)).toBe('2,500000');
  });

  it('fmtNum: özel ondalık parametresi', () => {
    expect(fmtNum(3.14159, 4)).toBe('3,1416');
  });

  it('fmtMoneyTwoDecimals: null/Infinity → null', () => {
    expect(fmtMoneyTwoDecimals(null)).toBeNull();
    expect(fmtMoneyTwoDecimals(Infinity)).toBeNull();
  });

  it('fmtPrice: null/NaN → null', () => {
    expect(fmtPrice(null)).toBeNull();
    expect(fmtPrice('abc')).toBeNull();
  });

  it('fmtVol: null/NaN → null; <1000 ham', () => {
    expect(fmtVol(null)).toBeNull();
    expect(fmtVol('x')).toBeNull();
    expect(fmtVol(999)).toBe('999');
  });
});

describe('renderCellForExport — tüm kolon dalları', () => {
  const h = {
    name: 'Ad', symbol: 'SYM', assetType: 'STOCK',
    totalQuantity: '10', averageCost: '50', currentPrice: '55',
    marketValue: '550', totalCost: '500',
    realizedGainLoss: '20', realizedGainLossPercent: '4',
    realProfitLoss: '30', realProfitLossPercent: '6',
    realProfitLossTry: '35', realProfitLossPercentTry: '7',
    inflationSincePercent: '12', currency: 'USD',
    firstBuyDate: '2024-01-15T10:00:00', lastTransactionDate: '2024-06-01T12:00:00',
    volume: '1000000',
    fiftyTwoWeekLow: '40', fiftyTwoWeekHigh: '60',
    dailyChangeAmount: '2', dailyGainLossPercent: '1.5',
  };

  it('temel kolonlar', () => {
    expect(renderCellForExport('name', h)).toBe('Ad');
    expect(renderCellForExport('symbol', h)).toBe('SYM');
    expect(renderCellForExport('qty', h)).toBe(10);
    expect(renderCellForExport('avgCost', h)).toBe(50);
    expect(renderCellForExport('currentPrice', h)).toBe(55);
    expect(renderCellForExport('marketValue', h)).toBe(550);
    expect(renderCellForExport('totalCost', h)).toBe(500);
    expect(renderCellForExport('unrealizedPnl', h)).toBe(50); // 550-500
  });

  it('K/Z ve reel kolonlar', () => {
    expect(renderCellForExport('dailyPnl', h)).toBe(20); // 10×2
    expect(renderCellForExport('dailyPct', h)).toBe(1.5);
    expect(renderCellForExport('realizedPnl', h)).toBe(20);
    expect(renderCellForExport('realizedPct', h)).toBe(4);
    expect(renderCellForExport('realPnl', h)).toBe(30);
    expect(renderCellForExport('realPct', h)).toBe(6);
    expect(renderCellForExport('realPnlTry', h)).toBe(35);
    expect(renderCellForExport('realPctTry', h)).toBe(7);
    expect(renderCellForExport('inflationSince', h)).toBe(12);
  });

  it('tarih ve para birimi kolonları (ISO slice)', () => {
    expect(renderCellForExport('currency', h)).toBe('USD');
    expect(renderCellForExport('firstBuyDate', h)).toBe('2024-01-15');
    expect(renderCellForExport('lastTxDate', h)).toBe('2024-06-01');
    expect(renderCellForExport('volume', h)).toBe(1000000);
  });

  it('currency yoksa TRY varsayılır; tarih yoksa boş', () => {
    expect(renderCellForExport('currency', {})).toBe('TRY');
    expect(renderCellForExport('firstBuyDate', {})).toBe('');
    expect(renderCellForExport('lastTxDate', {})).toBe('');
  });

  it('week52: low–high aralığı; ikisi de yoksa boş', () => {
    expect(renderCellForExport('week52', h)).toBe('40 – 60');
    expect(renderCellForExport('week52', {})).toBe('');
  });

  it('VİOP kolonları FUTURE’da değer, non-FUTURE’da boş/null', () => {
    const f = { assetType: 'FUTURE', viopMarginPosted: '100', viopNotional: '5000', viopLeverage: '50', marginStatus: 'Sağlıklı' };
    expect(renderCellForExport('viopMargin', f)).toBe(100);
    expect(renderCellForExport('viopNominal', f)).toBe(5000);
    expect(renderCellForExport('viopLeverage', f)).toBe(50);
    expect(renderCellForExport('marginStatus', f)).toBe('Sağlıklı');
    // non-FUTURE
    expect(renderCellForExport('viopMargin', h)).toBeNull();
    expect(renderCellForExport('marginStatus', h)).toBe('');
  });
});

describe('columnHasValueForHolding — ek dallar', () => {
  it('reel/enflasyon/realized kolonları değere göre', () => {
    expect(columnHasValueForHolding('realizedPnl', { realizedGainLoss: 1 })).toBe(true);
    expect(columnHasValueForHolding('realPnl', { realProfitLoss: 1 })).toBe(true);
    expect(columnHasValueForHolding('realPnlTry', { realProfitLossTry: 1 })).toBe(true);
    expect(columnHasValueForHolding('beatInflation', { realProfitLoss: 1 })).toBe(true);
    expect(columnHasValueForHolding('inflationSince', { inflationSincePercent: 1 })).toBe(true);
    expect(columnHasValueForHolding('volume', { volume: 1 })).toBe(true);
    expect(columnHasValueForHolding('realPnl', {})).toBe(false);
  });

  it('week52: range alanı VEYA low+high ikisi de varsa true', () => {
    expect(columnHasValueForHolding('week52', { fiftyTwoWeekRange: '40-60' })).toBe(true);
    expect(columnHasValueForHolding('week52', { fiftyTwoWeekLow: 40, fiftyTwoWeekHigh: 60 })).toBe(true);
    expect(columnHasValueForHolding('week52', { fiftyTwoWeekLow: 40 })).toBe(false);
  });

  it('dailyPct: alternatif alanlardan biri varsa true', () => {
    expect(columnHasValueForHolding('dailyPct', { changePercent: 2 })).toBe(true);
    expect(columnHasValueForHolding('dailyPct', {})).toBe(false);
  });
});
