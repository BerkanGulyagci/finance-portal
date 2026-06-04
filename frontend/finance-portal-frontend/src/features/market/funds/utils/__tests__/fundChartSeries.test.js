import { describe, it, expect } from 'vitest';
import { FUND_CHART_RANGES, buildFundChartSeries } from '../fundChartSeries';

// Yardımcılar: cutoff filtresi "bugüne" göre çalıştığı için tarihleri
// bugünden ofset üreterek deterministik test ederiz.
function isoDaysAgo(days) {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

const pt = (date, price) => ({ date, price });

describe('FUND_CHART_RANGES', () => {
  it('6 aralık tanımlı ve gün sayıları artan sırada', () => {
    expect(FUND_CHART_RANGES).toHaveLength(6);
    const days = FUND_CHART_RANGES.map((r) => r.days);
    expect(days).toEqual([7, 30, 90, 180, 365, 1825]);
  });

  it('her aralıkta key/label/days alanları mevcut ve key benzersiz', () => {
    const keys = FUND_CHART_RANGES.map((r) => r.key);
    expect(keys).toEqual(['1W', '1M', '3M', '6M', '1Y', '5Y']);
    expect(new Set(keys).size).toBe(keys.length);
    FUND_CHART_RANGES.forEach((r) => {
      expect(typeof r.key).toBe('string');
      expect(typeof r.label).toBe('string');
      expect(typeof r.days).toBe('number');
    });
  });
});

describe('buildFundChartSeries — günlük (5Y dışı) aralıklar', () => {
  it('boş/null/undefined priceHistory → boş points, usesMonthlyExtension false', () => {
    expect(buildFundChartSeries(null, null, '1Y')).toEqual({
      points: [],
      usesMonthlyExtension: false,
    });
    expect(buildFundChartSeries(undefined, undefined, '1M')).toEqual({
      points: [],
      usesMonthlyExtension: false,
    });
    expect(buildFundChartSeries([], [], '3M')).toEqual({
      points: [],
      usesMonthlyExtension: false,
    });
  });

  it('cutoff içindeki noktaları döner, cutoff öncesini eler (1M = 30 gün)', () => {
    const recent = isoDaysAgo(5);
    const old = isoDaysAgo(400);
    const res = buildFundChartSeries(
      [pt(old, 10), pt(recent, 12)],
      null,
      '1M',
    );
    expect(res.usesMonthlyExtension).toBe(false);
    expect(res.points).toHaveLength(1);
    expect(res.points[0].date).toBe(recent);
    expect(res.points[0].price).toBe(12);
  });

  it('cutoff penceresi içindeki nokta (6 gün önce, 1W=7) DAHİL edilir', () => {
    // 1W = 7 gün; pencere içindeki nokta >= cutoff olduğundan kalır.
    const inside = isoDaysAgo(6);
    const res = buildFundChartSeries([pt(inside, 9)], null, '1W');
    expect(res.points).toHaveLength(1);
    expect(res.points[0].date).toBe(inside);
  });

  it('noktaları tarihe göre artan sıraya koyar', () => {
    const d1 = isoDaysAgo(3);
    const d2 = isoDaysAgo(2);
    const d3 = isoDaysAgo(1);
    const res = buildFundChartSeries(
      [pt(d3, 30), pt(d1, 10), pt(d2, 20)],
      null,
      '1Y',
    );
    expect(res.points.map((p) => p.date)).toEqual([d1, d2, d3]);
  });

  it('geçersiz fiyatları (null/NaN/0/negatif) ve tarihsiz noktaları eler', () => {
    const d = isoDaysAgo(1);
    const res = buildFundChartSeries(
      [
        pt(d, 15),
        pt(isoDaysAgo(2), null),   // null fiyat → düşer
        pt(isoDaysAgo(2), 'abc'),  // NaN → düşer
        pt(isoDaysAgo(2), 0),      // 0 → düşer (>0 şartı)
        pt(isoDaysAgo(2), -5),     // negatif → düşer
        pt(null, 99),              // tarihsiz → düşer
      ],
      null,
      '1Y',
    );
    expect(res.points).toHaveLength(1);
    expect(res.points[0].date).toBe(d);
    expect(res.points[0].price).toBe(15);
  });

  it('string fiyatları sayıya çevirir (parseFloat)', () => {
    const d = isoDaysAgo(1);
    const res = buildFundChartSeries([pt(d, '42.5')], null, '6M');
    expect(res.points[0].price).toBeCloseTo(42.5);
  });

  it('bilinmeyen rangeKey → 1Y (365 gün) fallback aralığı kullanılır', () => {
    // 1Y içine düşen (200 gün) tutulur, dışındaki (500 gün) elenir.
    const inside = isoDaysAgo(200);
    const outside = isoDaysAgo(500);
    const res = buildFundChartSeries(
      [pt(outside, 5), pt(inside, 8)],
      null,
      'BILINMEYEN',
    );
    expect(res.usesMonthlyExtension).toBe(false);
    expect(res.points).toHaveLength(1);
    expect(res.points[0].date).toBe(inside);
  });

  it('rangeKey undefined → yine 1Y fallback (günlük dal, 5Y değil)', () => {
    const inside = isoDaysAgo(10);
    const res = buildFundChartSeries([pt(inside, 7)], null, undefined);
    expect(res.usesMonthlyExtension).toBe(false);
    expect(res.points).toHaveLength(1);
  });
});

describe('buildFundChartSeries — 5Y aylık genişletme dalı', () => {
  it('monthlyReturns yoksa sadece günlük noktalar, usesMonthlyExtension false', () => {
    const d = isoDaysAgo(10);
    const res = buildFundChartSeries([pt(d, 100)], null, '5Y');
    expect(res.usesMonthlyExtension).toBe(false);
    expect(res.points).toHaveLength(1);
    expect(res.points[0].date).toBe(d);
  });

  it('günlük seri boşsa aylık genişletme de boştur (usesMonthlyExtension false)', () => {
    // monthlyReturns dolu ama günlük boş → buildMonthlyExtension erken döner.
    const res = buildFundChartSeries(
      [],
      [{ year: 2024, month: 1, value: 5 }],
      '5Y',
    );
    expect(res.usesMonthlyExtension).toBe(false);
    expect(res.points).toEqual([]);
  });

  it('anchor öncesi ayları aylık getiriyle GERİYE tahmin eder ve birleştirir', () => {
    // Anchor: 60 gün önce, fiyat 110. Bundan önceki 2 ay için getiri verelim.
    // Geriye yürürken price = price / (1 + ret) uygulanır.
    const anchorDate = isoDaysAgo(60);
    const anchor = new Date(anchorDate);
    const y = anchor.getFullYear();
    const mo = anchor.getMonth() + 1; // 1-based anchor ayı

    // Anchor ayından önceki iki ay (mk < anchorMk) — yıl/ay devrini hesaba kat.
    const prev = (year, month, back) => {
      const idx = year * 12 + (month - 1) - back; // 0-based ay indeksi
      return { year: Math.floor(idx / 12), month: (idx % 12) + 1 };
    };
    const m1 = prev(y, mo, 1); // anchor - 1 ay
    const m2 = prev(y, mo, 2); // anchor - 2 ay

    const res = buildFundChartSeries(
      [pt(anchorDate, 110)],
      [
        { year: m2.year, month: m2.month, value: 10 }, // +%10
        { year: m1.year, month: m1.month, value: 10 }, // +%10
      ],
      '5Y',
    );

    expect(res.usesMonthlyExtension).toBe(true);
    // Aylık-only noktalar + anchor günlük noktası → toplam 3 nokta.
    expect(res.points).toHaveLength(3);

    // Son nokta anchor (günlük) olmalı, fiyat 110.
    const last = res.points[res.points.length - 1];
    expect(last.date).toBe(anchorDate);
    expect(last.price).toBe(110);

    // Geriye doğru: m1 fiyatı = 110 / 1.10 = 100, m2 = 100 / 1.10 ≈ 90.909
    const sortedByDate = [...res.points].sort(
      (a, b) => new Date(a.date) - new Date(b.date),
    );
    expect(sortedByDate[0].price).toBeCloseTo(110 / 1.1 / 1.1, 4); // en eski = m2
    expect(sortedByDate[1].price).toBeCloseTo(100, 4);             // m1
    // Tarihler ay başına (YYYY-MM-01) sabitlenir.
    expect(sortedByDate[0].date.endsWith('-01')).toBe(true);
    expect(sortedByDate[1].date.endsWith('-01')).toBe(true);
  });

  it('ret = -1 (value=-100) özel durumunda fiyat değişmez (sıfıra bölme korunur)', () => {
    const anchorDate = isoDaysAgo(45);
    const anchor = new Date(anchorDate);
    const y = anchor.getFullYear();
    const mo = anchor.getMonth() + 1;
    const idx = y * 12 + (mo - 1) - 1; // anchor - 1 ay
    const m1 = { year: Math.floor(idx / 12), month: (idx % 12) + 1 };

    const res = buildFundChartSeries(
      [pt(anchorDate, 200)],
      [{ year: m1.year, month: m1.month, value: -100 }], // ret = -1
      '5Y',
    );
    expect(res.usesMonthlyExtension).toBe(true);
    const earliest = [...res.points].sort(
      (a, b) => new Date(a.date) - new Date(b.date),
    )[0];
    // ret === -1 → price korunur (anchor fiyatı 200).
    expect(earliest.price).toBe(200);
  });

  it('cutoff öncesi aylar genişletmeye DAHİL edilmez', () => {
    // Anchor 5 gün önce; aylık getiri 5Y cutoff (1825 gün) öncesine ait → elenir.
    const anchorDate = isoDaysAgo(5);
    const res = buildFundChartSeries(
      [pt(anchorDate, 100)],
      [{ year: 2000, month: 1, value: 50 }], // çok eski → cutoff dışı
      '5Y',
    );
    expect(res.usesMonthlyExtension).toBe(false);
    expect(res.points).toHaveLength(1);
    expect(res.points[0].date).toBe(anchorDate);
  });

  it('anchor ayına/sonrasına ait aylık getiriler genişletmeye girmez (mk >= anchorMk)', () => {
    // Aylık getiri tam anchor ayına ait → mk >= anchorMk → beforeAnchor boş.
    const anchorDate = isoDaysAgo(20);
    const anchor = new Date(anchorDate);
    const res = buildFundChartSeries(
      [pt(anchorDate, 100)],
      [{ year: anchor.getFullYear(), month: anchor.getMonth() + 1, value: 25 }],
      '5Y',
    );
    expect(res.usesMonthlyExtension).toBe(false);
    expect(res.points).toHaveLength(1);
  });

  it('geçersiz aylık kayıtlar (year/month/value parse edilemeyen) elenir', () => {
    const anchorDate = isoDaysAgo(40);
    const anchor = new Date(anchorDate);
    const y = anchor.getFullYear();
    const mo = anchor.getMonth() + 1;
    const idx = y * 12 + (mo - 1) - 1;
    const m1 = { year: Math.floor(idx / 12), month: (idx % 12) + 1 };

    const res = buildFundChartSeries(
      [pt(anchorDate, 100)],
      [
        { year: 'xx', month: m1.month, value: 10 },     // year NaN → düşer
        { year: m1.year, month: 'yy', value: 10 },      // month NaN → düşer
        { year: m1.year, month: m1.month, value: null }, // value null → düşer
        { year: m1.year, month: m1.month, value: 10 },   // geçerli → kalır
      ],
      '5Y',
    );
    expect(res.usesMonthlyExtension).toBe(true);
    // Geçerli olan tek aylık nokta + anchor = 2 nokta.
    expect(res.points).toHaveLength(2);
  });

  it('aynı aya ait günlük nokta varsa o ay aylık-only listeden çıkarılır (mergeMonthlyAndDaily)', () => {
    // İki günlük nokta aynı ayda; aylık getiri o ayı kapsasa bile günlük öncelik kazanır.
    const dEarly = isoDaysAgo(40);
    const dLate = isoDaysAgo(35); // aynı ay olabilir; aynı değilse de test mantığı korunur
    const early = new Date(dEarly);
    const y = early.getFullYear();
    const mo = early.getMonth() + 1;
    const idxPrev = y * 12 + (mo - 1) - 1;
    const mPrev = { year: Math.floor(idxPrev / 12), month: (idxPrev % 12) + 1 };

    const res = buildFundChartSeries(
      [pt(dEarly, 100), pt(dLate, 105)],
      [{ year: mPrev.year, month: mPrev.month, value: 5 }],
      '5Y',
    );
    expect(res.usesMonthlyExtension).toBe(true);
    // Günlük noktalar korunur; aylık-only nokta ay başında eklenir.
    const dates = res.points.map((p) => p.date);
    expect(dates).toContain(dEarly);
    expect(dates).toContain(dLate);
    // Sonuç tarihe göre sıralı olmalı.
    const ts = res.points.map((p) => new Date(p.date).getTime());
    expect(ts).toEqual([...ts].sort((a, b) => a - b));
  });
});
