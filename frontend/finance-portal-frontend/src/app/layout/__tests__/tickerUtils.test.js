import { describe, it, expect } from 'vitest';
import {
  COLOR_UP,
  COLOR_DOWN,
  COLOR_NEUTRAL,
  tickerHref,
  trendSparkline,
  dirFromChangePct,
  dailyChangeFromSeries,
  sparkFromFxHistory,
  downsample,
  scaleSparkToEnd,
  changeDirFromSeries,
  sparkFromGeckoChart,
  sparkFromGoldHist,
} from '../tickerUtils';

describe('renk sabitleri', () => {
  it('yukarı/aşağı/nötr renkleri tanımlı', () => {
    expect(COLOR_UP).toBe('#10b981');
    expect(COLOR_DOWN).toBe('#ef4444');
    expect(COLOR_NEUTRAL).toBe('#64748b');
  });
});

describe('tickerHref', () => {
  it('fx/bank → /market/fx/{SEMBOL} (büyük harf)', () => {
    expect(tickerHref({ key: 'fx:usdtry' })).toBe('/market/fx/USDTRY');
    expect(tickerHref({ key: 'bank:eur' })).toBe('/market/fx/EUR');
  });

  it('fx eki yoksa null', () => {
    expect(tickerHref({ key: 'fx:' })).toBeNull();
    expect(tickerHref({ key: 'fx' })).toBeNull();
  });

  it('crypto → coinId varsa /market/crypto/{coinId}, yoksa null', () => {
    expect(tickerHref({ key: 'crypto:btc', coinId: 'bitcoin' })).toBe('/market/crypto/bitcoin');
    expect(tickerHref({ key: 'crypto:btc' })).toBeNull();
  });

  it('bist → /market/indices/{KOD}', () => {
    expect(tickerHref({ key: 'bist:xu100' })).toBe('/market/indices/XU100');
  });

  it('gold → /market/gold', () => {
    expect(tickerHref({ key: 'gold:gram' })).toBe('/market/gold');
  });

  it('eco → /market/economy', () => {
    expect(tickerHref({ key: 'eco:tufe' })).toBe('/market/economy');
  });

  it('custom STOCK → /market/stocks/{SYMBOL}', () => {
    expect(tickerHref({ key: 'custom:STOCK:THYAO' })).toBe('/market/stocks/THYAO');
  });

  it('custom GOLD: ons sekmesi query eklemez, diğerleri ?tab=', () => {
    expect(tickerHref({ key: 'custom:GOLD:ONS' })).toBe('/market/gold');
    expect(tickerHref({ key: 'custom:GOLD:GRAM' })).toBe('/market/gold?tab=gram');
    expect(tickerHref({ key: 'custom:GOLD:CEYREK' })).toBe('/market/gold?tab=ceyrek');
  });

  it('custom bilinmeyen asset tipi → null', () => {
    expect(tickerHref({ key: 'custom:UNKNOWN:FOO' })).toBeNull();
  });

  it('tanınmayan kind / boş key → null', () => {
    expect(tickerHref({ key: 'whatever:x' })).toBeNull();
    expect(tickerHref({ key: '' })).toBeNull();
    expect(tickerHref({})).toBeNull();
  });
});

describe('dirFromChangePct', () => {
  it('pozitif → up, negatif → down', () => {
    expect(dirFromChangePct(1.5)).toBe('up');
    expect(dirFromChangePct(-2)).toBe('down');
  });

  it('sıfıra çok yakın / null / NaN → null', () => {
    expect(dirFromChangePct(0)).toBeNull();
    expect(dirFromChangePct(0.00001)).toBeNull();
    expect(dirFromChangePct(null)).toBeNull();
    expect(dirFromChangePct(NaN)).toBeNull();
  });
});

describe('trendSparkline', () => {
  it('geçersiz endValue → boş dizi', () => {
    expect(trendSparkline(null, 5)).toEqual([]);
    expect(trendSparkline(0, 5)).toEqual([]);
    expect(trendSparkline(-10, 5)).toEqual([]);
    expect(trendSparkline(NaN, 5)).toEqual([]);
  });

  it('değişim ~0 → düz çizgi (hepsi endValue)', () => {
    const s = trendSparkline(100, 0, 5);
    expect(s).toHaveLength(5);
    expect(s.every((v) => v === 100)).toBe(true);
  });

  it('monoton artan seri üretir, son nokta endValue olur', () => {
    const s = trendSparkline(110, 10, 5); // başlangıç 100 → bitiş 110
    expect(s).toHaveLength(5);
    expect(s[0]).toBeCloseTo(100);
    expect(s[s.length - 1]).toBeCloseTo(110);
    for (let i = 1; i < s.length; i++) expect(s[i]).toBeGreaterThan(s[i - 1]);
  });

  it('en az 2 nokta garanti eder', () => {
    expect(trendSparkline(100, 5, 1)).toHaveLength(2);
  });
});

describe('dailyChangeFromSeries', () => {
  it('2den az nokta → null', () => {
    expect(dailyChangeFromSeries([100])).toBeNull();
    expect(dailyChangeFromSeries(null)).toBeNull();
  });

  it('timestamp yokken son 2 noktanın % farkı', () => {
    expect(dailyChangeFromSeries([100, 110])).toBeCloseTo(10);
  });

  it('saatlik seride aynı günün noktalarını atlar, dünkü kapanışı baz alır', () => {
    const day = 86400;
    // gün1: 100, gün2: üç saatlik nokta 105/108/110 → değişim gün1(100)→son(110) = %10
    const closes = [100, 105, 108, 110];
    const ts = [1 * day, 2 * day + 0, 2 * day + 3600, 2 * day + 7200];
    expect(dailyChangeFromSeries(closes, ts)).toBeCloseTo(10);
  });

  it('hepsi aynı gün → son 2 nokta farkına düşer', () => {
    const day = 86400;
    const closes = [100, 102, 105];
    const ts = [2 * day, 2 * day + 3600, 2 * day + 7200];
    expect(dailyChangeFromSeries(closes, ts)).toBeCloseTo((105 - 102) / 102 * 100);
  });

  it('son değer pozitif değilse null', () => {
    expect(dailyChangeFromSeries([100, 0])).toBeNull();
  });
});

describe('downsample', () => {
  it('boş/null → boş dizi', () => {
    expect(downsample([], 5)).toEqual([]);
    expect(downsample(null, 5)).toEqual([]);
  });

  it('uzunluk <= maxPoints → kopya döner', () => {
    const s = [1, 2, 3];
    const out = downsample(s, 5);
    expect(out).toEqual([1, 2, 3]);
    expect(out).not.toBe(s); // yeni dizi (kopya)
  });

  it('maxPoints kadar nokta seçer, ilk ve son korunur', () => {
    const s = Array.from({ length: 100 }, (_, i) => i);
    const out = downsample(s, 10);
    expect(out).toHaveLength(10);
    expect(out[0]).toBe(0);
    expect(out[out.length - 1]).toBe(99);
  });
});

describe('scaleSparkToEnd', () => {
  it('son nokta endValue olacak şekilde ölçekler (şekil korunur)', () => {
    const out = scaleSparkToEnd([1, 2, 4], 8); // son 4 → 8, k=2
    expect(out).toEqual([2, 4, 8]);
  });

  it('boş/geçersiz endValue → girdi olduğu gibi', () => {
    expect(scaleSparkToEnd([], 10)).toEqual([]);
    expect(scaleSparkToEnd([1, 2], 0)).toEqual([1, 2]);
    expect(scaleSparkToEnd([1, 2], null)).toEqual([1, 2]);
  });

  it('son nokta pozitif değilse girdiyi döndürür', () => {
    expect(scaleSparkToEnd([1, 0], 10)).toEqual([1, 0]);
  });
});

describe('changeDirFromSeries', () => {
  it('artan seri → pozitif % ve up', () => {
    const r = changeDirFromSeries([100, 110]);
    expect(r.changePct).toBeCloseTo(10);
    expect(r.dir).toBe('up');
  });

  it('azalan seri → negatif % ve down', () => {
    const r = changeDirFromSeries([100, 90]);
    expect(r.changePct).toBeCloseTo(-10);
    expect(r.dir).toBe('down');
  });

  it('2den az nokta → null/null', () => {
    expect(changeDirFromSeries([100])).toEqual({ changePct: null, dir: null });
    expect(changeDirFromSeries([])).toEqual({ changePct: null, dir: null });
  });

  it('ilk nokta pozitif değil → null/null', () => {
    expect(changeDirFromSeries([0, 50])).toEqual({ changePct: null, dir: null });
  });
});

describe('sparkFromFxHistory', () => {
  it('yetersiz veri → boş sonuç', () => {
    expect(sparkFromFxHistory(null)).toEqual({ spark: [], changePct: null, dir: null });
    expect(sparkFromFxHistory({ points: [{ close: '10' }] })).toEqual({ spark: [], changePct: null, dir: null });
  });

  it('tarihli noktalardan gün-bazlı değişim hesaplar', () => {
    const hist = {
      points: [
        { date: '2026-01-01', close: '30' },
        { date: '2026-01-02', close: '33' },
      ],
    };
    const r = sparkFromFxHistory(hist);
    expect(r.spark).toEqual([30, 33]);
    expect(r.changePct).toBeCloseTo(10);
    expect(r.dir).toBe('up');
  });

  it('maxPoints ile son N noktaya kısar', () => {
    const points = Array.from({ length: 20 }, (_, i) => ({ date: `2026-01-${String(i + 1).padStart(2, '0')}`, close: String(100 + i) }));
    const r = sparkFromFxHistory({ points }, 5);
    expect(r.spark).toHaveLength(5);
    expect(r.spark[r.spark.length - 1]).toBe(119);
  });
});

describe('sparkFromGeckoChart', () => {
  it('prices [ts, value] dizisinden seri + yön üretir', () => {
    const chart = { prices: [[1, 100], [2, 105], [3, 110]] };
    const r = sparkFromGeckoChart(chart, 36);
    expect(r.spark).toEqual([100, 105, 110]);
    expect(r.dir).toBe('up');
    expect(r.changePct).toBeCloseTo(10);
  });

  it('yetersiz/geçersiz → boş sonuç', () => {
    expect(sparkFromGeckoChart(null)).toEqual({ spark: [], changePct: null, dir: null });
    expect(sparkFromGeckoChart({ prices: [[1, 0], [2, -1]] })).toEqual({ spark: [], changePct: null, dir: null });
  });
});

describe('sparkFromGoldHist', () => {
  it('points.close dizisinden seri + yön üretir', () => {
    const hist = { points: [{ close: '2000' }, { close: '1900' }] };
    const r = sparkFromGoldHist(hist);
    expect(r.spark).toEqual([2000, 1900]);
    expect(r.dir).toBe('down');
    expect(r.changePct).toBeCloseTo(-5);
  });

  it('yetersiz veri → boş sonuç', () => {
    expect(sparkFromGoldHist({ points: [] })).toEqual({ spark: [], changePct: null, dir: null });
    expect(sparkFromGoldHist(undefined)).toEqual({ spark: [], changePct: null, dir: null });
  });
});
