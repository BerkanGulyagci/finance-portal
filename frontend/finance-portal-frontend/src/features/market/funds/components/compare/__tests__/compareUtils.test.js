import { describe, it, expect } from 'vitest';
import {
  COMPARE_COLORS,
  toNum,
  fmtPct,
  fmtPctColor,
  fmtPrice,
  fmtBig,
  filterByRange,
  buildNormalizedSeries,
  buildChartData,
  calcPeriodReturn,
} from '../compareUtils';

// Yardımcı: { date, price } noktası üretir.
const pt = (date, price) => ({ date, price });

// Yardımcı: bugünden gün-offset ile ISO tarih (YYYY-MM-DD) üretir.
const daysAgo = (n) => {
  const d = new Date(Date.now() - n * 86400_000);
  return d.toISOString().slice(0, 10);
};

describe('COMPARE_COLORS', () => {
  it('5 renk içeren hex paleti', () => {
    expect(COMPARE_COLORS).toHaveLength(5);
    // Hepsi # ile başlayan hex kodları olmalı.
    COMPARE_COLORS.forEach((c) => expect(c).toMatch(/^#[0-9a-f]{6}$/i));
  });
});

describe('toNum', () => {
  it('null/undefined → null', () => {
    expect(toNum(null)).toBeNull();
    expect(toNum(undefined)).toBeNull();
  });

  it('sayısal string ve number parse eder', () => {
    expect(toNum('12.5')).toBeCloseTo(12.5);
    expect(toNum(7)).toBe(7);
    // parseFloat baştaki sayıyı alır, sondaki birimleri yok sayar.
    expect(toNum('3.2abc')).toBeCloseTo(3.2);
  });

  it('parse edilemeyen (NaN) → null', () => {
    expect(toNum('abc')).toBeNull();
    expect(toNum('')).toBeNull();
  });

  it('0 değerini korur (null değil)', () => {
    expect(toNum(0)).toBe(0);
    expect(toNum('0')).toBe(0);
  });
});

describe('fmtPct', () => {
  it('null/parse-edilemez → "-"', () => {
    expect(fmtPct(null)).toBe('-');
    expect(fmtPct('abc')).toBe('-');
  });

  it('pozitif değere + öneki ekler, varsayılan 2 ondalık', () => {
    expect(fmtPct(3.2)).toBe('+3.20%');
    expect(fmtPct(0)).toBe('+0.00%'); // 0 >= 0 → + öneki alır
  });

  it('negatif değer + öneki almaz', () => {
    expect(fmtPct(-4.5)).toBe('-4.50%');
  });

  it('ondalık basamak parametresine uyar', () => {
    expect(fmtPct(1.23456, 3)).toBe('+1.235%');
    expect(fmtPct(-1.2, 0)).toBe('-1%');
  });
});

describe('fmtPctColor', () => {
  it('null → gri', () => {
    expect(fmtPctColor(null)).toBe('text-gray-400');
    expect(fmtPctColor('xx')).toBe('text-gray-400');
  });

  it('pozitif/sıfır → yeşil (emerald)', () => {
    expect(fmtPctColor(5)).toBe('text-emerald-600');
    expect(fmtPctColor(0)).toBe('text-emerald-600'); // 0 >= 0
  });

  it('negatif → kırmızı (rose)', () => {
    expect(fmtPctColor(-1)).toBe('text-rose-600');
  });
});

describe('fmtPrice', () => {
  it('null/parse-edilemez → "-"', () => {
    expect(fmtPrice(null)).toBe('-');
    expect(fmtPrice('abc')).toBe('-');
  });

  // tr-TR: ondalık ayıracı virgül, binlik ayıracı nokta.
  it('>=100 → 2 ondalık', () => {
    expect(fmtPrice(1234.5)).toBe('1.234,50');
    expect(fmtPrice(123.456)).toBe('123,46');
  });

  it('>=10 ve <100 → 3 ondalık', () => {
    expect(fmtPrice(12.5)).toBe('12,500');
  });

  it('>=1 ve <10 → 4 ondalık', () => {
    expect(fmtPrice(5.12345)).toBe('5,1235');
  });

  it('>=0.1 ve <1 → 5 ondalık', () => {
    expect(fmtPrice(0.5)).toBe('0,50000');
  });

  it('<0.1 → 6 ondalık', () => {
    expect(fmtPrice(0.05)).toBe('0,050000');
  });

  it('negatif değerde mutlak değere göre ondalık seçer', () => {
    // abs(-1234.5)=1234.5 >= 100 → 2 ondalık, işaret korunur.
    expect(fmtPrice(-1234.5)).toBe('-1.234,50');
  });
});

describe('fmtBig', () => {
  it('null/parse-edilemez → "-"', () => {
    expect(fmtBig(null)).toBe('-');
    expect(fmtBig('abc')).toBe('-');
  });

  it('milyar (>=1e9) → B ₺', () => {
    expect(fmtBig(2.5e9)).toBe('2.50B ₺');
  });

  it('milyon (>=1e6) → M ₺', () => {
    expect(fmtBig(3.4e6)).toBe('3.40M ₺');
  });

  it('bin (>=1e3) → K ₺', () => {
    expect(fmtBig(1500)).toBe('1.50K ₺');
  });

  it('<1000 → düz ₺', () => {
    expect(fmtBig(750)).toBe('750.00 ₺');
    expect(fmtBig(0)).toBe('0.00 ₺');
  });
});

describe('filterByRange', () => {
  it('boş/null priceHistory → boş dizi', () => {
    expect(filterByRange(null, '1M')).toEqual([]);
    expect(filterByRange([], '1M')).toEqual([]);
    expect(filterByRange(undefined, '1Y')).toEqual([]);
  });

  it('cutoff içindeki noktaları tutar, dışındakileri eler', () => {
    const recent = daysAgo(5);
    const old = daysAgo(400);
    const out = filterByRange([pt(old, 1), pt(recent, 2)], '1M'); // 30 gün
    expect(out).toHaveLength(1);
    expect(out[0].date).toBe(recent);
  });

  it('bilinmeyen range → 365 gün (varsayılan) kullanır', () => {
    const within = daysAgo(100); // 365 içinde
    const out = filterByRange([pt(within, 9)], 'BILINMEYEN');
    expect(out).toHaveLength(1);
  });

  // Tuhaf davranış: filtre boş kalırsa son `days` adet noktayı ham döndürür.
  it('tüm noktalar cutoff öncesi ise son `days` adedi fallback olarak döner', () => {
    const veryOld = [pt('1990-01-01', 1), pt('1990-01-02', 2)];
    const out = filterByRange(veryOld, '1M'); // days=30, slice(-30) → 2 nokta
    expect(out).toHaveLength(2);
    expect(out).toEqual(veryOld);
  });
});

describe('buildNormalizedSeries', () => {
  it('2 noktadan az → boş dizi', () => {
    expect(buildNormalizedSeries([pt(daysAgo(1), 10)], '1M')).toEqual([]);
    expect(buildNormalizedSeries([], '1M')).toEqual([]);
  });

  it('ilk fiyata göre yüzde getiriyi hesaplar (ret)', () => {
    const out = buildNormalizedSeries(
      [pt(daysAgo(20), 100), pt(daysAgo(10), 110), pt(daysAgo(5), 90)],
      '1M',
    );
    expect(out).toHaveLength(3);
    expect(out[0].ret).toBeCloseTo(0);   // (100-100)/100*100
    expect(out[1].ret).toBeCloseTo(10);  // (110-100)/100*100
    expect(out[2].ret).toBeCloseTo(-10); // (90-100)/100*100
    expect(out[0].date).toBe(daysAgo(20));
  });

  it('sırasız girdiyi tarihe göre sıralayıp ilk fiyatı baz alır', () => {
    // Kronolojik ilk nokta 200 olmalı → baz 200.
    const out = buildNormalizedSeries(
      [pt(daysAgo(5), 220), pt(daysAgo(20), 200)],
      '1M',
    );
    expect(out[0].date).toBe(daysAgo(20));
    expect(out[0].ret).toBeCloseTo(0);
    expect(out[1].ret).toBeCloseTo(10); // (220-200)/200*100
  });

  it('ilk fiyat 0/parse-edilemez ise → boş dizi', () => {
    expect(
      buildNormalizedSeries([pt(daysAgo(20), 0), pt(daysAgo(5), 50)], '1M'),
    ).toEqual([]);
    expect(
      buildNormalizedSeries([pt(daysAgo(20), 'xx'), pt(daysAgo(5), 50)], '1M'),
    ).toEqual([]);
  });

  it('aradaki parse-edilemez fiyat noktasını atar', () => {
    const out = buildNormalizedSeries(
      [pt(daysAgo(20), 100), pt(daysAgo(10), 'bad'), pt(daysAgo(5), 120)],
      '1M',
    );
    // 3 noktadan ortadaki düşer → 2 nokta kalır.
    expect(out).toHaveLength(2);
    expect(out.map((p) => p.date)).toEqual([daysAgo(20), daysAgo(5)]);
  });
});

describe('buildChartData', () => {
  it('boş selectedCodes → boş dizi', () => {
    expect(buildChartData({}, [], '1M')).toEqual([]);
  });

  it('eksik koda ait priceHistory yokken patlamaz (boş dizi fallback)', () => {
    // detailMap'te kod yok → ?? [] devreye girer.
    expect(buildChartData({}, ['YOK'], '1M')).toEqual([]);
  });

  it('birden çok fonu tarih birleşimi üzerinde normalize eder', () => {
    const detailMap = {
      AAA: {
        priceHistory: [pt(daysAgo(20), 100), pt(daysAgo(10), 110)],
      },
      BBB: {
        priceHistory: [pt(daysAgo(20), 200), pt(daysAgo(10), 180)],
      },
    };
    const rows = buildChartData(detailMap, ['AAA', 'BBB'], '1M');
    expect(rows).toHaveLength(2);
    // Tarihler kronolojik sırada.
    expect(rows[0].date).toBe(daysAgo(20));
    expect(rows[1].date).toBe(daysAgo(10));
    // İlk satır her iki fon için baz 0.
    expect(rows[0].AAA).toBeCloseTo(0);
    expect(rows[0].BBB).toBeCloseTo(0);
    // İkinci satır getiriler.
    expect(rows[1].AAA).toBeCloseTo(10);  // 110/100
    expect(rows[1].BBB).toBeCloseTo(-10); // 180/200
  });

  it('bir tarihte değeri olmayan fon için o anahtar satıra eklenmez', () => {
    const detailMap = {
      AAA: { priceHistory: [pt(daysAgo(20), 100), pt(daysAgo(10), 110)] },
      // BBB sadece bir tarihte var → 2 noktadan az → normalize boş → hiç anahtar yok.
      BBB: { priceHistory: [pt(daysAgo(15), 50)] },
    };
    const rows = buildChartData(detailMap, ['AAA', 'BBB'], '1M');
    // BBB hiçbir satırda bulunmamalı (normalize edilemedi).
    rows.forEach((r) => expect(r).not.toHaveProperty('BBB'));
    // AAA değerleri mevcut.
    expect(rows.find((r) => r.date === daysAgo(20)).AAA).toBeCloseTo(0);
  });
});

describe('calcPeriodReturn', () => {
  it('2 noktadan az → null', () => {
    expect(calcPeriodReturn([pt(daysAgo(5), 10)], '1M')).toBeNull();
    expect(calcPeriodReturn([], '1M')).toBeNull();
  });

  it('ilk→son fiyat yüzde değişimini döner', () => {
    const out = calcPeriodReturn(
      [pt(daysAgo(20), 100), pt(daysAgo(10), 105), pt(daysAgo(5), 120)],
      '1M',
    );
    expect(out).toBeCloseTo(20); // (120-100)/100*100
  });

  it('negatif dönem getirisi', () => {
    const out = calcPeriodReturn(
      [pt(daysAgo(20), 200), pt(daysAgo(5), 180)],
      '1M',
    );
    expect(out).toBeCloseTo(-10);
  });

  it('sırasız girdiyi tarihe göre sıralar (ilk=en eski, son=en yeni)', () => {
    const out = calcPeriodReturn(
      [pt(daysAgo(5), 120), pt(daysAgo(20), 100)],
      '1M',
    );
    expect(out).toBeCloseTo(20);
  });

  it('ilk fiyat 0 / son fiyat parse-edilemez → null', () => {
    expect(
      calcPeriodReturn([pt(daysAgo(20), 0), pt(daysAgo(5), 50)], '1M'),
    ).toBeNull();
    expect(
      calcPeriodReturn([pt(daysAgo(20), 100), pt(daysAgo(5), 'bad')], '1M'),
    ).toBeNull();
  });
});
