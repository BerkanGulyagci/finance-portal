import { describe, it, expect } from 'vitest';
import {
  BOND_CHART_PERIODS,
  downsampleWeeklyLast,
  filterBondHistoryByDays,
  prepareBondLineChartPoints,
  toKlineData,
  normalizeSeries,
  findCommonStartDate,
  calculatePeriodChangePercent,
  buildMALine,
  mergeByDate,
  fmtNum,
  fmtPct,
} from '../bondChartUtils';

// Yardımcı: indicatorValue'lu history noktası üretir.
const pt = (date, val) => ({ date, indicatorValue: val });

describe('BOND_CHART_PERIODS', () => {
  it('6 periyot tanımlı, gün sayıları artan sırada', () => {
    expect(BOND_CHART_PERIODS).toHaveLength(6);
    const days = BOND_CHART_PERIODS.map((p) => p.days);
    expect(days).toEqual([7, 30, 90, 180, 365, 1825]);
  });
});

describe('downsampleWeeklyLast', () => {
  it('boş/null girdi için boş dizi döner', () => {
    expect(downsampleWeeklyLast([])).toEqual([]);
    expect(downsampleWeeklyLast(null)).toEqual([]);
    expect(downsampleWeeklyLast(undefined)).toEqual([]);
  });

  it('aynı haftadaki birden çok noktadan SON değeri tutar', () => {
    // 2024-01-01 Pzt, 02 Sal, 03 Çar → aynı ISO hafta → tek nokta (sonuncu kalır).
    const out = downsampleWeeklyLast([
      pt('2024-01-01', 10),
      pt('2024-01-02', 11),
      pt('2024-01-03', 12),
    ]);
    expect(out).toHaveLength(1);
    expect(out[0].date).toBe('2024-01-03');
  });

  it('farklı haftalardaki noktaları ayrı tutar', () => {
    const out = downsampleWeeklyLast([
      pt('2024-01-03', 12), // 1. hafta
      pt('2024-01-10', 20), // 2. hafta
    ]);
    expect(out).toHaveLength(2);
    expect(out.map((p) => p.date)).toEqual(['2024-01-03', '2024-01-10']);
  });

  it('sırasız girdiyi tarihe göre sıralar', () => {
    const out = downsampleWeeklyLast([
      pt('2024-01-10', 20),
      pt('2024-01-03', 12),
    ]);
    expect(out[0].date).toBe('2024-01-03');
    expect(out[1].date).toBe('2024-01-10');
  });
});

describe('filterBondHistoryByDays', () => {
  it('boş points → boş dizi; days yoksa points aynen döner', () => {
    expect(filterBondHistoryByDays([], 30)).toEqual([]);
    expect(filterBondHistoryByDays(null, 30)).toEqual([]);
    const pts = [pt('2024-01-01', 1)];
    expect(filterBondHistoryByDays(pts, 0)).toBe(pts); // days=0 → falsy → aynen
  });

  it('cutoff öncesi noktaları eler', () => {
    // Çok eski bir nokta her zaman elenir; bugünün noktası her zaman kalır.
    const today = new Date().toISOString().slice(0, 10);
    const out = filterBondHistoryByDays(
      [pt('1990-01-01', 1), pt(today, 2)],
      30,
    );
    expect(out).toHaveLength(1);
    expect(out[0].date).toBe(today);
  });
});

describe('prepareBondLineChartPoints', () => {
  it('bilinmeyen periyot → ONE_MONTH (30 gün) fallback', () => {
    const today = new Date().toISOString().slice(0, 10);
    const res = prepareBondLineChartPoints([pt(today, 5)], 'BILINMEYEN');
    expect(res.usesWeeklySampling).toBe(false);
    expect(res.points).toHaveLength(1);
  });

  it('tüm aralıklarda haftalık örnekleme KAPALI (MA200 tutarlılığı)', () => {
    const today = new Date().toISOString().slice(0, 10);
    const res = prepareBondLineChartPoints([pt(today, 5)], 'FIVE_YEARS');
    expect(res.usesWeeklySampling).toBe(false);
  });
});

describe('toKlineData', () => {
  it('OHLC değerlerini aynı (indicatorValue) yapar, hacim 0', () => {
    const out = toKlineData([{ date: '2024-01-02', indicatorValue: '7.5', dateText: '02.01.2024' }]);
    expect(out).toHaveLength(1);
    expect(out[0]).toMatchObject({
      open: 7.5, high: 7.5, low: 7.5, close: 7.5, volume: 0, _value: 7.5, _dateText: '02.01.2024',
    });
    expect(out[0].timestamp).toBe(new Date('2024-01-02').getTime());
  });

  it('parse edilemeyen değeri (NaN) atar ve zaman damgasına göre sıralar', () => {
    const out = toKlineData([
      pt('2024-01-10', '5'),
      pt('2024-01-01', 'abc'), // NaN → düşer
      pt('2024-01-05', '6'),
    ]);
    expect(out).toHaveLength(2);
    expect(out[0]._date).toBe('2024-01-05');
    expect(out[1]._date).toBe('2024-01-10');
  });
});

describe('normalizeSeries', () => {
  it('ilk geçerli değeri 100 baz alır', () => {
    const out = normalizeSeries([pt('2024-01-01', '50'), pt('2024-01-02', '75')]);
    expect(out[0].indicatorValue).toBe(100);
    expect(out[1].indicatorValue).toBe(150); // 75/50*100
    expect(out[1]._rawValue).toBe(75);
  });

  it('baseDate verilirse o tarihteki değeri baz alır', () => {
    const out = normalizeSeries(
      [pt('2024-01-01', '50'), pt('2024-01-02', '100')],
      '2024-01-02',
    );
    expect(out[1].indicatorValue).toBe(100); // base = 100
    expect(out[0].indicatorValue).toBe(50);  // 50/100*100
  });

  it('NaN değer → indicatorValue null', () => {
    const out = normalizeSeries([pt('2024-01-01', '50'), pt('2024-01-02', 'xx')]);
    expect(out[1].indicatorValue).toBeNull();
  });

  it('geçerli baz değer yoksa points aynen döner', () => {
    const pts = [pt('2024-01-01', '0'), pt('2024-01-02', 'abc')];
    // hepsi 0 veya NaN → firstValue null → aynen döner
    expect(normalizeSeries(pts)).toBe(pts);
  });
});

describe('findCommonStartDate', () => {
  it('iki seride de olan en erken tarihi döner', () => {
    const main = [pt('2024-01-01', 1), pt('2024-01-02', 2), pt('2024-01-03', 3)];
    const cmp = [pt('2024-01-02', 9), pt('2024-01-03', 8)];
    expect(findCommonStartDate(main, cmp)).toBe('2024-01-02');
  });

  it('ortak tarih yoksa null', () => {
    expect(findCommonStartDate([pt('2024-01-01', 1)], [pt('2024-02-01', 1)])).toBeNull();
  });
});

describe('calculatePeriodChangePercent', () => {
  it('ilk→son değer yüzde değişimi', () => {
    expect(calculatePeriodChangePercent([pt('a', '100'), pt('b', '110')])).toBeCloseTo(10);
    expect(calculatePeriodChangePercent([pt('a', '100'), pt('b', '90')])).toBeCloseTo(-10);
  });

  it('2 geçerli noktadan az → null', () => {
    expect(calculatePeriodChangePercent([pt('a', '100')])).toBeNull();
    expect(calculatePeriodChangePercent([pt('a', 'xx'), pt('b', '0')])).toBeNull();
  });
});

describe('buildMALine', () => {
  it('ilk n-1 eleman null, sonrası kayan ortalama', () => {
    const ma = buildMALine([2, 4, 6, 8], 2);
    expect(ma).toEqual([null, 3, 5, 7]);
  });

  it('n=3 penceresi', () => {
    expect(buildMALine([3, 6, 9, 12], 3)).toEqual([null, null, 6, 9]);
  });
});

describe('mergeByDate', () => {
  it('tarih bazında birleştirir; eksik compare → null', () => {
    const main = [pt('2024-01-01', '10'), pt('2024-01-02', '20')];
    const cmp = [pt('2024-01-01', '5')];
    const out = mergeByDate(main, cmp);
    expect(out).toEqual([
      { date: '2024-01-01', dateText: '2024-01-01', mainValue: 10, compareValue: 5 },
      { date: '2024-01-02', dateText: '2024-01-02', mainValue: 20, compareValue: null },
    ]);
  });

  it('indicatorValue null olan noktaları atlar', () => {
    const main = [{ date: '2024-01-01', indicatorValue: null }, pt('2024-01-02', '20')];
    const out = mergeByDate(main, []);
    expect(out).toHaveLength(1);
    expect(out[0].date).toBe('2024-01-02');
  });
});

describe('fmtNum', () => {
  it('null/NaN → "-"', () => {
    expect(fmtNum(null)).toBe('-');
    expect(fmtNum('abc')).toBe('-');
  });

  it('Türkçe formatla, varsayılan 2 ondalık', () => {
    expect(fmtNum(1234.5)).toBe('1.234,50');
    expect(fmtNum(7.1, 1)).toBe('7,1');
  });
});

describe('fmtPct', () => {
  it('pozitif → +%, negatif → -%, sıfır → %', () => {
    expect(fmtPct(3.22)).toBe('+%3,22');
    expect(fmtPct(-8.57)).toBe('-%8,57');
    expect(fmtPct(0)).toBe('%0,00');
  });

  it('null/NaN → "-"', () => {
    expect(fmtPct(null)).toBe('-');
    expect(fmtPct(NaN)).toBe('-');
  });
});
