import { describe, it, expect } from 'vitest';
import {
  computeTrend,
  buildTrendItem,
  TREND_SIGNAL,
  TREND_METHOD_LABEL,
} from './trendUtils';

describe('TREND_SIGNAL / TREND_METHOD_LABEL sabitleri', () => {
  it('beklenen sinyal değerlerini içerir', () => {
    expect(TREND_SIGNAL).toEqual({ UP: 'UP', DOWN: 'DOWN', SIDEWAYS: 'SIDEWAYS' });
  });

  it('tüm method anahtarları için label tanımlı', () => {
    ['explicit', 'ma-crossover', 'ma20', '52w-position', 'fund-return-3m',
     'fund-return-1m', 'fund-nav-ma', 'crypto-7d', 'daily-change',
     'multi-signal', 'no-data'].forEach((k) => {
      expect(typeof TREND_METHOD_LABEL[k]).toBe('string');
    });
  });
});

describe('computeTrend — temel/edge', () => {
  it('null/boş item için no-data döner', () => {
    expect(computeTrend(null)).toEqual({ signal: null, method: 'no-data' });
    expect(computeTrend(undefined)).toEqual({ signal: null, method: 'no-data' });
  });

  it('hiç sinyal verisi yoksa no-data döner', () => {
    expect(computeTrend({ assetType: 'STOCK' })).toEqual({ signal: null, method: 'no-data' });
  });
});

describe('computeTrend — explicit backend trend (öncelik 1)', () => {
  it('UP / YUKSELIŞ değerlerini tanır', () => {
    expect(computeTrend({ trend: 'UP' })).toEqual({ signal: 'UP', method: 'explicit' });
    expect(computeTrend({ trend: 'Yükseliş' })).toEqual({ signal: 'UP', method: 'explicit' });
  });

  it('DOWN / DUSUS değerlerini tanır', () => {
    expect(computeTrend({ trend: 'DOWN' })).toEqual({ signal: 'DOWN', method: 'explicit' });
    expect(computeTrend({ trend: 'düşüş' })).toEqual({ signal: 'DOWN', method: 'explicit' });
  });

  it('SIDEWAYS / YATAY değerlerini tanır', () => {
    expect(computeTrend({ trend: 'SIDEWAYS' })).toEqual({ signal: 'SIDEWAYS', method: 'explicit' });
    expect(computeTrend({ trend: 'yatay' })).toEqual({ signal: 'SIDEWAYS', method: 'explicit' });
  });

  it('tanınmayan explicit değer için bir sonraki yönteme düşer (no-data)', () => {
    expect(computeTrend({ trend: 'GARBAGE' })).toEqual({ signal: null, method: 'no-data' });
  });
});

describe('computeTrend — FUND', () => {
  it('3 aylık getiri > %2 → UP (fund-return-3m)', () => {
    expect(computeTrend({ assetType: 'FUND', returnThreeMonths: 5 }))
      .toEqual({ signal: 'UP', method: 'fund-return-3m' });
  });

  it('3 aylık getiri < -%2 → DOWN', () => {
    expect(computeTrend({ assetType: 'FUND', returnThreeMonths: -3 }))
      .toEqual({ signal: 'DOWN', method: 'fund-return-3m' });
  });

  it('3 aylık getiri eşik içinde → SIDEWAYS', () => {
    expect(computeTrend({ assetType: 'FUND', returnThreeMonths: 1 }))
      .toEqual({ signal: 'SIDEWAYS', method: 'fund-return-3m' });
  });

  it('3A yoksa 1 aylık getiriye düşer', () => {
    expect(computeTrend({ assetType: 'FUND', returnOneMonth: 4 }))
      .toEqual({ signal: 'UP', method: 'fund-return-1m' });
  });

  it('getiri yoksa NAV MA20/MA50 kesişimine düşer', () => {
    expect(computeTrend({ assetType: 'FUND', currentPrice: 110, ma20: 105, ma50: 100 }))
      .toEqual({ signal: 'UP', method: 'fund-nav-ma' });
    expect(computeTrend({ assetType: 'FUND', currentPrice: 90, ma20: 95, ma50: 100 }))
      .toEqual({ signal: 'DOWN', method: 'fund-nav-ma' });
  });

  it('fon için hiçbir veri yoksa no-data', () => {
    expect(computeTrend({ assetType: 'FUND' })).toEqual({ signal: null, method: 'no-data' });
  });
});

describe('computeTrend — MA-only varlıklar (GOLD/BOND/SILVER)', () => {
  it('GOLD: MA-crossover UP', () => {
    expect(computeTrend({ assetType: 'GOLD', currentPrice: 120, ma20: 110, ma50: 100 }))
      .toEqual({ signal: 'UP', method: 'ma-crossover' });
  });

  it('BOND: MA-crossover DOWN', () => {
    expect(computeTrend({ assetType: 'BOND', currentPrice: 80, ma20: 90, ma50: 100 }))
      .toEqual({ signal: 'DOWN', method: 'ma-crossover' });
  });

  it('GOLD: karışık MA → SIDEWAYS', () => {
    expect(computeTrend({ assetType: 'GOLD', currentPrice: 105, ma20: 100, ma50: 110 }))
      .toEqual({ signal: 'SIDEWAYS', method: 'ma-crossover' });
  });

  it('SILVER:* sembollü COMMODITY MA-only davranır', () => {
    expect(computeTrend({ assetType: 'COMMODITY', symbol: 'SILVER:XAG', currentPrice: 50, ma20: 40, ma50: 30 }))
      .toEqual({ signal: 'UP', method: 'ma-crossover' });
  });

  it('MA-only varlıkta sadece MA20 varsa ma20 yöntemi (tolerans)', () => {
    expect(computeTrend({ assetType: 'GOLD', currentPrice: 101, ma20: 100 }))
      .toEqual({ signal: 'UP', method: 'ma20' });
    expect(computeTrend({ assetType: 'GOLD', currentPrice: 100, ma20: 100 }))
      .toEqual({ signal: 'SIDEWAYS', method: 'ma20' });
  });

  it('MA-only varlıkta veri yoksa no-data', () => {
    expect(computeTrend({ assetType: 'GOLD' })).toEqual({ signal: null, method: 'no-data' });
  });
});

describe('computeTrend — genel çok-sinyalli oylama (multi-signal)', () => {
  it('güçlü MA yükselişi UP verir', () => {
    expect(computeTrend({ assetType: 'STOCK', currentPrice: 120, ma20: 110, ma50: 100 }))
      .toEqual({ signal: 'UP', method: 'multi-signal' });
  });

  it('güçlü MA düşüşü DOWN verir', () => {
    expect(computeTrend({ assetType: 'STOCK', currentPrice: 80, ma20: 90, ma50: 100 }))
      .toEqual({ signal: 'DOWN', method: 'multi-signal' });
  });

  it('52 hafta üst konumu UP destekler', () => {
    const r = computeTrend({ assetType: 'STOCK', currentPrice: 95, week52High: 100, week52Low: 10 });
    expect(r.signal).toBe('UP');
    expect(r.method).toBe('multi-signal');
  });

  it('kripto 7g momentumu tek başına UP verir', () => {
    expect(computeTrend({ assetType: 'CRYPTO', return7d: 5 }))
      .toEqual({ signal: 'UP', method: 'multi-signal' });
  });

  it('günlük % düşüş (>%3) DOWN verir (7g yokken fallback)', () => {
    expect(computeTrend({ assetType: 'STOCK', changePercent: -5 }))
      .toEqual({ signal: 'DOWN', method: 'multi-signal' });
  });

  it('nötr/dengeli sinyaller SIDEWAYS verir', () => {
    expect(computeTrend({ assetType: 'STOCK', currentPrice: 100, ma20: 100, ma50: 100 }))
      .toEqual({ signal: 'SIDEWAYS', method: 'multi-signal' });
  });
});

describe('buildTrendItem', () => {
  it('2den az geçerli kapanış için null döner', () => {
    expect(buildTrendItem([], 'STOCK')).toBeNull();
    expect(buildTrendItem([10], 'STOCK')).toBeNull();
    expect(buildTrendItem(null, 'STOCK')).toBeNull();
    expect(buildTrendItem([0, -1, NaN], 'STOCK')).toBeNull(); // geçerli pozitif < 2
  });

  it('son fiyatı ve MA20/MA50 hesaplar', () => {
    const closes = Array.from({ length: 50 }, (_, i) => i + 1); // 1..50
    const item = buildTrendItem(closes, 'STOCK');
    expect(item.assetType).toBe('STOCK');
    expect(item.currentPrice).toBe(50);
    // son 20 = 31..50 → ort 40.5
    expect(item.ma20).toBeCloseTo(40.5);
    // son 50 = 1..50 → ort 25.5
    expect(item.ma50).toBeCloseTo(25.5);
  });

  it('seri 20den kısaysa ma20/ma50 null olur', () => {
    const item = buildTrendItem([10, 20, 30], 'CRYPTO');
    expect(item.currentPrice).toBe(30);
    expect(item.ma20).toBeNull();
    expect(item.ma50).toBeNull();
  });

  it('extra alanlarını item içine yayar', () => {
    const item = buildTrendItem([10, 20], 'FUND', { returnThreeMonths: 7 });
    expect(item.returnThreeMonths).toBe(7);
  });

  it('buildTrendItem çıktısı computeTrend ile zincirlenebilir', () => {
    const rising = Array.from({ length: 60 }, (_, i) => 100 + i); // sürekli artan
    const item = buildTrendItem(rising, 'STOCK');
    expect(computeTrend(item).signal).toBe('UP');
  });
});
