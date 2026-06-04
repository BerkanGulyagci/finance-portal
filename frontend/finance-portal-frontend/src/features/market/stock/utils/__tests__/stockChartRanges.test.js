import { describe, it, expect } from 'vitest';
import {
  STOCK_CHART_RANGES,
  formatStockChartTimeLabel,
} from '../stockChartRanges';

// Sabit bir referans zaman damgası (saniye). 2023-11-14T22:13:20Z.
// Branch ASSERT'leri yerel saat dilimine bağlı değil: çıktının BİÇİMİNE
// (HH:MM / DD/MM / MM.YY vb.) bakıyoruz, böylece TZ farkı testi kırmaz.
const TS = 1700000000;

describe('STOCK_CHART_RANGES', () => {
  it('tam 8 aralık tanımlı', () => {
    expect(STOCK_CHART_RANGES).toHaveLength(8);
  });

  it('etiketler beklenen sırada ve içerikte', () => {
    expect(STOCK_CHART_RANGES.map((r) => r.label)).toEqual([
      '1G', '1H', '1A', '3A', '6A', '1Y', '5Y', 'Tüm',
    ]);
  });

  it('her öğe label/range/interval string alanlarını içerir', () => {
    STOCK_CHART_RANGES.forEach((r) => {
      expect(typeof r.label).toBe('string');
      expect(typeof r.range).toBe('string');
      expect(typeof r.interval).toBe('string');
      expect(Object.keys(r).sort()).toEqual(['interval', 'label', 'range']);
    });
  });

  it('range→interval eşlemeleri dokümandaki kurallara uyar', () => {
    const byLabel = Object.fromEntries(
      STOCK_CHART_RANGES.map((r) => [r.label, r]),
    );
    // 1G → 5dk sabit
    expect(byLabel['1G']).toEqual({ label: '1G', range: '1d', interval: '5m' });
    // 1H/1A/3A/6A → saatlik
    expect(byLabel['1H']).toEqual({ label: '1H', range: '5d', interval: '1h' });
    expect(byLabel['1A']).toEqual({ label: '1A', range: '1mo', interval: '1h' });
    expect(byLabel['3A']).toEqual({ label: '3A', range: '3mo', interval: '1h' });
    expect(byLabel['6A']).toEqual({ label: '6A', range: '6mo', interval: '1h' });
    // 1Y → günlük, 5Y → haftalık, Tüm → aylık
    expect(byLabel['1Y']).toEqual({ label: '1Y', range: '1y', interval: '1d' });
    expect(byLabel['5Y']).toEqual({ label: '5Y', range: '5y', interval: '1wk' });
    expect(byLabel['Tüm']).toEqual({ label: 'Tüm', range: 'max', interval: '1mo' });
  });

  it("yalnızca '1H' (5d) saatlik aralık özel haftagün biçimini tetikler", () => {
    // 5d olan tek aralık 1H — formatStockChartTimeLabel haftagün dalını yalnız bunda kullanır.
    const fiveDay = STOCK_CHART_RANGES.filter((r) => r.range === '5d');
    expect(fiveDay).toHaveLength(1);
    expect(fiveDay[0].label).toBe('1H');
  });
});

describe('formatStockChartTimeLabel', () => {
  // --- Dal 1: range === '1d' → saat:dakika ---
  it("range '1d' ise saat:dakika (HH:MM) biçimi döner", () => {
    // interval ne olursa olsun range '1d' önce yakalanır.
    const out = formatStockChartTimeLabel(TS, '1d', '5m');
    expect(out).toMatch(/^\d{2}:\d{2}$/);
  });

  it("range '1d' ile interval '1h' verilse bile saat dalı kazanır", () => {
    // '1d' kontrolü '1h' kontrolünden önce → saat biçimi.
    const out = formatStockChartTimeLabel(TS, '1d', '1h');
    expect(out).toMatch(/^\d{2}:\d{2}$/);
  });

  // --- Dal 2: interval 5m/15m/30m (range !== '1d') → saat:dakika ---
  it.each(['5m', '15m', '30m'])(
    "intraday interval '%s' (range '1d' değil) saat:dakika döner",
    (interval) => {
      const out = formatStockChartTimeLabel(TS, '5d', interval);
      expect(out).toMatch(/^\d{2}:\d{2}$/);
    },
  );

  // --- Dal 3: interval 1h/90m + range '5d' → haftagün + saat ---
  it("interval '1h' + range '5d' → haftagün kısaltması + saat içerir", () => {
    const out = formatStockChartTimeLabel(TS, '5d', '1h');
    // Çıktı bir saat (HH:MM) içerir ...
    expect(out).toMatch(/\d{2}:\d{2}/);
    // ... ve harf içeren bir haftagün kısaltmasıyla başlar (gün/ay sayı dalından farkı bu).
    expect(out).toMatch(/[A-Za-zÇĞİÖŞÜçğıöşü]/);
    // Gün/ay saatlik dalı "DD <ay> HH" biçimindedir (saat:dakika YOK) — bu daldan ayırt et.
    const dayMonthHour = formatStockChartTimeLabel(TS, '1mo', '1h');
    expect(out).not.toBe(dayMonthHour);
  });

  it("interval '90m' + range '5d' → '1h' ile aynı haftagün dalını kullanır", () => {
    const a = formatStockChartTimeLabel(TS, '5d', '90m');
    const b = formatStockChartTimeLabel(TS, '5d', '1h');
    expect(a).toBe(b);
  });

  // --- Dal 4: interval 1h/90m + range !== '5d' → gün ay saat ---
  it("interval '1h' + range '5d' DEĞİL → 'DD <ay> HH' (haftagünsüz) biçimi", () => {
    const out = formatStockChartTimeLabel(TS, '1mo', '1h');
    // 2 haneli gün ile başlar.
    expect(out).toMatch(/^\d{2}\s/);
    // İçinde ay adı (harf) ve sondaki saat (2 hane) bulunur, saat:dakika yok.
    expect(out).toMatch(/[A-Za-zÇĞİÖŞÜçğıöşü]/);
    expect(out).not.toMatch(/\d{2}:\d{2}/);
  });

  it("interval '90m' + range '6mo' de gün/ay/saat dalına düşer", () => {
    const out = formatStockChartTimeLabel(TS, '6mo', '90m');
    expect(out).toMatch(/^\d{2}\s/);
    expect(out).not.toMatch(/\d{2}:\d{2}/);
  });

  // --- Dal 5: interval 1d → gün/ay (DD/MM) ---
  it("interval '1d' → gün/ay (DD/MM) biçimi döner", () => {
    const out = formatStockChartTimeLabel(TS, '1y', '1d');
    expect(out).toMatch(/^\d{2}\/\d{2}$/);
  });

  // --- Dal 6 (fallback): diğer interval → ay.yıl (MM.YY) ---
  it("haftalık interval '1wk' → ay.yıl (MM.YY) fallback biçimi", () => {
    const out = formatStockChartTimeLabel(TS, '5y', '1wk');
    expect(out).toMatch(/^\d{2}\.\d{2}$/);
  });

  it("aylık interval '1mo' → ay.yıl (MM.YY) fallback biçimi", () => {
    const out = formatStockChartTimeLabel(TS, 'max', '1mo');
    expect(out).toMatch(/^\d{2}\.\d{2}$/);
  });

  it('tanınmayan interval/range fallback (ay.yıl) dalına düşer', () => {
    const out = formatStockChartTimeLabel(TS, 'bilinmeyen', 'bilinmeyen');
    expect(out).toMatch(/^\d{2}\.\d{2}$/);
  });

  // --- Girdi türü / kenar durumlar ---
  it('timestamp string olarak verilse de Number ile parse edilir', () => {
    // Number(timestampSec) çağrısı string'i sayıya çevirir → aynı çıktı.
    const asNum = formatStockChartTimeLabel(TS, '1y', '1d');
    const asStr = formatStockChartTimeLabel(String(TS), '1y', '1d');
    expect(asStr).toBe(asNum);
  });

  it('timestamp 0 (epoch) geçerli bir biçim üretir', () => {
    const out = formatStockChartTimeLabel(0, '1y', '1d');
    expect(out).toMatch(/^\d{2}\/\d{2}$/);
  });

  it('STOCK_CHART_RANGES içindeki her aralık için boş olmayan etiket üretir', () => {
    // Gerçek konfig satırlarıyla uçtan uca: her kombinasyon bir string döndürür.
    STOCK_CHART_RANGES.forEach(({ range, interval }) => {
      const out = formatStockChartTimeLabel(TS, range, interval);
      expect(typeof out).toBe('string');
      expect(out.length).toBeGreaterThan(0);
    });
  });
});
