import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  DRAWING_TOOLS,
  MA_PERIODS,
  maLineStyles,
  STOCK_WARMUP_RANGE,
  RANGE_WINDOW_MS,
  fitVisibleToWindow,
  SUB_INDICATORS,
} from '../stockChartConfig';

describe('DRAWING_TOOLS', () => {
  it('beklenen 4 grubu sırayla içerir', () => {
    expect(DRAWING_TOOLS).toHaveLength(4);
    expect(DRAWING_TOOLS.map((g) => g.group)).toEqual([
      'Çizgiler', 'Kanallar', 'Fibonacci', 'Şekiller',
    ]);
  });

  it('her grup bir başlık ve araç dizisi taşır', () => {
    for (const grp of DRAWING_TOOLS) {
      expect(typeof grp.group).toBe('string');
      expect(Array.isArray(grp.tools)).toBe(true);
      expect(grp.tools.length).toBeGreaterThan(0);
    }
  });

  it('her aracın id/label/icon alanları tanımlı ve string', () => {
    for (const grp of DRAWING_TOOLS) {
      for (const t of grp.tools) {
        expect(typeof t.id).toBe('string');
        expect(t.id.length).toBeGreaterThan(0);
        expect(typeof t.label).toBe('string');
        expect(typeof t.icon).toBe('string');
      }
    }
  });

  it('tüm araç id\'leri benzersizdir', () => {
    const ids = DRAWING_TOOLS.flatMap((g) => g.tools.map((t) => t.id));
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('bilinen kilit araçları (segment, fibonacciLine, customRect) içerir', () => {
    const ids = DRAWING_TOOLS.flatMap((g) => g.tools.map((t) => t.id));
    expect(ids).toContain('segment');
    expect(ids).toContain('fibonacciLine');
    expect(ids).toContain('customRect');
    expect(ids).toContain('customCircle');
  });
});

describe('MA_PERIODS', () => {
  it('20/50/200 periyotlarını sırasıyla tanımlar', () => {
    expect(MA_PERIODS.map((m) => m.period)).toEqual([20, 50, 200]);
  });

  it('her periyodun renk ve etiketi vardır; etiket MA<period> formatında', () => {
    for (const m of MA_PERIODS) {
      expect(typeof m.color).toBe('string');
      expect(m.color).toMatch(/^#[0-9a-fA-F]{6}$/);
      expect(m.label).toBe(`MA${m.period}`);
    }
  });
});

describe('maLineStyles', () => {
  it('verilen periyotların rengini MA_PERIODS\'tan eşler, kalınlık 1.5', () => {
    const out = maLineStyles([20, 50, 200]);
    expect(out).toEqual({
      lines: [
        { color: '#f59e0b', size: 1.5 },
        { color: '#8b5cf6', size: 1.5 },
        { color: '#ef4444', size: 1.5 },
      ],
    });
  });

  it('tek periyot için tek satır döner', () => {
    expect(maLineStyles([50])).toEqual({ lines: [{ color: '#8b5cf6', size: 1.5 }] });
  });

  it('MA_PERIODS\'ta olmayan periyot için varsayılan #888 rengini kullanır', () => {
    const out = maLineStyles([999]);
    expect(out.lines).toHaveLength(1);
    expect(out.lines[0]).toEqual({ color: '#888', size: 1.5 });
  });

  it('bilinen ve bilinmeyen periyotlar karışıkken her birini ayrı çözer', () => {
    const out = maLineStyles([20, 7]);
    expect(out.lines[0].color).toBe('#f59e0b'); // bilinen
    expect(out.lines[1].color).toBe('#888');    // bilinmeyen → fallback
  });

  it('boş periyot dizisinde boş lines döner', () => {
    expect(maLineStyles([])).toEqual({ lines: [] });
  });

  it('periyot sırasını korur', () => {
    const out = maLineStyles([200, 20]);
    expect(out.lines.map((l) => l.color)).toEqual(['#ef4444', '#f59e0b']);
  });
});

describe('STOCK_WARMUP_RANGE', () => {
  it('bilinen aralıkları daha uzun ısınma aralığına eşler', () => {
    expect(STOCK_WARMUP_RANGE['1d']).toBe('5d');
    expect(STOCK_WARMUP_RANGE['5d']).toBe('1mo');
    expect(STOCK_WARMUP_RANGE['1mo']).toBe('1y');
    expect(STOCK_WARMUP_RANGE['3mo']).toBe('2y');
    expect(STOCK_WARMUP_RANGE['6mo']).toBe('2y');
    expect(STOCK_WARMUP_RANGE['1y']).toBe('2y');
    expect(STOCK_WARMUP_RANGE['5y']).toBe('10y');
    expect(STOCK_WARMUP_RANGE['10y']).toBe('10y');
  });

  it('bilinmeyen aralık için undefined döner', () => {
    expect(STOCK_WARMUP_RANGE['2y']).toBeUndefined();
  });
});

describe('RANGE_WINDOW_MS', () => {
  it('aralık başına milisaniye penceresi tanımlar (864e5 = 1 gün)', () => {
    expect(RANGE_WINDOW_MS['1d']).toBe(1 * 864e5);
    expect(RANGE_WINDOW_MS['5d']).toBe(5 * 864e5);
    expect(RANGE_WINDOW_MS['1mo']).toBe(31 * 864e5);
    expect(RANGE_WINDOW_MS['3mo']).toBe(93 * 864e5);
    expect(RANGE_WINDOW_MS['6mo']).toBe(186 * 864e5);
    expect(RANGE_WINDOW_MS['1y']).toBe(366 * 864e5);
    expect(RANGE_WINDOW_MS['5y']).toBe(1827 * 864e5);
  });

  it('10y için sınırsız (Infinity) pencere kullanır', () => {
    expect(RANGE_WINDOW_MS['10y']).toBe(Infinity);
  });

  it('STOCK_WARMUP_RANGE ile aynı aralık anahtarlarını paylaşır', () => {
    expect(Object.keys(RANGE_WINDOW_MS).sort()).toEqual(Object.keys(STOCK_WARMUP_RANGE).sort());
  });
});

describe('SUB_INDICATORS', () => {
  it('5 alt indikatör seçeneğini doğru isimlerle listeler', () => {
    expect(SUB_INDICATORS.map((s) => s.name)).toEqual(['VOL', 'RSI', 'MACD', 'KDJ', 'BOLL']);
  });

  it('her seçenekte name/label/color alanları string olarak bulunur', () => {
    for (const s of SUB_INDICATORS) {
      expect(typeof s.name).toBe('string');
      expect(typeof s.label).toBe('string');
      expect(s.color).toMatch(/^#[0-9a-fA-F]{6}$/);
    }
  });

  it('Hacim seçeneği VOL adıyla eşlenir', () => {
    const vol = SUB_INDICATORS.find((s) => s.name === 'VOL');
    expect(vol.label).toBe('Hacim');
  });
});

describe('fitVisibleToWindow', () => {
  let chart;
  let rafSpy;
  let getByIdSpy;

  beforeEach(() => {
    chart = {
      setOffsetRightDistance: vi.fn(),
      setBarSpace: vi.fn(),
    };
    // requestAnimationFrame'i senkron çalıştır ki yan etkileri hemen doğrulayalım.
    rafSpy = vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => {
      cb();
      return 1;
    });
  });

  afterEach(() => {
    rafSpy.mockRestore();
    if (getByIdSpy) { getByIdSpy.mockRestore(); getByIdSpy = undefined; }
  });

  // Verilen genişlikte bir element döndüren getElementById stub'ı kurar.
  const stubElementWidth = (width) => {
    getByIdSpy = vi.spyOn(document, 'getElementById').mockReturnValue(
      width === null ? null : { clientWidth: width },
    );
  };

  it('chart yoksa erken döner ve rAF planlamaz', () => {
    fitVisibleToWindow(null, 'c1', [{ timestamp: 1 }], 0);
    expect(rafSpy).not.toHaveBeenCalled();
  });

  it('allData boş/null ise erken döner ve rAF planlamaz', () => {
    fitVisibleToWindow(chart, 'c1', [], 0);
    fitVisibleToWindow(chart, 'c1', null, 0);
    fitVisibleToWindow(chart, 'c1', undefined, 0);
    expect(rafSpy).not.toHaveBeenCalled();
  });

  it('element bulunamazsa (null) chart metodlarını çağırmaz', () => {
    stubElementWidth(null);
    fitVisibleToWindow(chart, 'c1', [{ timestamp: 1 }], 0);
    expect(rafSpy).toHaveBeenCalledTimes(1);
    expect(chart.setBarSpace).not.toHaveBeenCalled();
    expect(chart.setOffsetRightDistance).not.toHaveBeenCalled();
  });

  it('genişlik 0 ise (width <= 0) chart metodlarını çağırmaz', () => {
    stubElementWidth(0);
    fitVisibleToWindow(chart, 'c1', [{ timestamp: 1 }], 0);
    expect(chart.setBarSpace).not.toHaveBeenCalled();
  });

  it('windowStartTs=0 iken tüm veri sayısını kullanır ve bar aralığını hesaplar', () => {
    stubElementWidth(216); // (216-16)/count
    const data = [{ timestamp: 1 }, { timestamp: 2 }, { timestamp: 3 }, { timestamp: 4 }];
    fitVisibleToWindow(chart, 'c1', data, 0);
    expect(chart.setOffsetRightDistance).toHaveBeenCalledWith(8);
    // count = 4 (tüm veri) → barSpace = (216-16)/4 = 50.
    expect(chart.setBarSpace).toHaveBeenCalledWith(50);
  });

  it('windowStartTs>0 iken yalnız pencere içindeki noktaları sayar', () => {
    stubElementWidth(116);
    const data = [
      { timestamp: 10 }, { timestamp: 20 }, // pencere öncesi
      { timestamp: 30 }, { timestamp: 40 }, // pencere içi (>= 30)
    ];
    fitVisibleToWindow(chart, 'c1', data, 30);
    // windowCount = 2 → barSpace = (116-16)/2 = 50.
    expect(chart.setBarSpace).toHaveBeenCalledWith(50);
  });

  it('pencere içi nokta yoksa count en az 1 olur (sıfıra bölme yok)', () => {
    stubElementWidth(116);
    const data = [{ timestamp: 10 }, { timestamp: 20 }];
    // windowStartTs çok ileride → eşleşen yok → windowCount 0 → Math.max(1,0)=1.
    fitVisibleToWindow(chart, 'c1', data, 9999);
    // barSpace = (116-16)/1 = 100.
    expect(chart.setBarSpace).toHaveBeenCalledWith(100);
  });

  it('hesaplanan bar aralığı her zaman en az 2 olur (dar genişlik)', () => {
    stubElementWidth(18); // (18-16)/count = küçük → Math.max(2, ...) devreye girer
    const data = Array.from({ length: 50 }, (_, i) => ({ timestamp: i + 1 }));
    fitVisibleToWindow(chart, 'c1', data, 0);
    // (18-16)/50 = 0.04 → Math.max(2, 0.04) = 2.
    expect(chart.setBarSpace).toHaveBeenCalledWith(2);
  });

  it('chart metodu fırlatırsa hata yutulur (try/catch)', () => {
    stubElementWidth(200);
    chart.setBarSpace = vi.fn(() => { throw new Error('boom'); });
    // Atmamalı; catch bloğu yutar.
    expect(() => fitVisibleToWindow(chart, 'c1', [{ timestamp: 1 }], 0)).not.toThrow();
  });
});
