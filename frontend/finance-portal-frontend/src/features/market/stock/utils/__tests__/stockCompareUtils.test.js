import { describe, it, expect } from 'vitest';
import {
  ASSET_LABELS,
  BENCHMARK_SHORTCUTS,
  INDEX_SHORTCUTS,
  COLORS,
  MAX_STOCKS,
  rebaseToCommonStart,
  interpolateSeries,
  stepFillSeries,
  toGenericRange,
  calcMetrics,
} from '../stockCompareUtils';

// Yardımcı: __price_<key> alanlı satır kurar.
const priceRow = (obj) => {
  const r = {};
  Object.entries(obj).forEach(([k, v]) => { r[`__price_${k}`] = v; });
  return r;
};

describe('stockCompareUtils sabitleri', () => {
  it('ASSET_LABELS bilinen varlık türlerini Türkçe etiketlere eşler', () => {
    expect(ASSET_LABELS.STOCK).toBe('Hisse');
    expect(ASSET_LABELS.CRYPTO).toBe('Kripto');
    expect(ASSET_LABELS.FX).toBe('Döviz');
    expect(ASSET_LABELS.BOND).toBe('Tahvil');
    expect(ASSET_LABELS.INDICATOR).toBe('Gösterge');
    // Bilinmeyen anahtar undefined döner (map'te yok).
    expect(ASSET_LABELS.UNKNOWN).toBeUndefined();
  });

  it('BENCHMARK_SHORTCUTS 3 INDICATOR benchmark içerir ve key alanı assetType|symbol formatında', () => {
    expect(BENCHMARK_SHORTCUTS).toHaveLength(3);
    BENCHMARK_SHORTCUTS.forEach((b) => {
      expect(b.assetType).toBe('INDICATOR');
      expect(b.key).toBe(`${b.assetType}|${b.symbol}`);
      expect(typeof b.label).toBe('string');
    });
    expect(BENCHMARK_SHORTCUTS.map((b) => b.symbol)).toEqual(['TUFE', 'USCPI_TRY', 'DEPOSIT']);
  });

  it('INDEX_SHORTCUTS BIST 100 ve BIST 30 endekslerini içerir', () => {
    expect(INDEX_SHORTCUTS).toHaveLength(2);
    expect(INDEX_SHORTCUTS[0]).toEqual({ symbol: 'XU100.IS', label: 'BIST 100' });
    expect(INDEX_SHORTCUTS[1]).toEqual({ symbol: 'XU030.IS', label: 'BIST 30' });
  });

  it('COLORS 4 renkli paleti ve MAX_STOCKS 4 limitini sağlar', () => {
    expect(COLORS).toHaveLength(4);
    expect(COLORS[0]).toBe('#093eaa');
    expect(MAX_STOCKS).toBe(4);
  });
});

describe('toGenericRange', () => {
  it('Yahoo aralıklarını genel price-history aralıklarına çevirir', () => {
    // 1d/5d/1mo hepsi 1M'e katlanır.
    expect(toGenericRange('1d')).toBe('1M');
    expect(toGenericRange('5d')).toBe('1M');
    expect(toGenericRange('1mo')).toBe('1M');
    expect(toGenericRange('3mo')).toBe('3M');
    expect(toGenericRange('6mo')).toBe('6M');
    expect(toGenericRange('1y')).toBe('1Y');
    expect(toGenericRange('5y')).toBe('5Y');
    expect(toGenericRange('max')).toBe('ALL');
  });

  it('bilinmeyen / boş aralık için varsayılan 1Y döner', () => {
    expect(toGenericRange('2y')).toBe('1Y');
    expect(toGenericRange(undefined)).toBe('1Y');
    expect(toGenericRange('')).toBe('1Y');
    expect(toGenericRange(null)).toBe('1Y');
  });
});

describe('rebaseToCommonStart', () => {
  it('boş satır veya boş key dizisinde girdiyi olduğu gibi döner', () => {
    const rows = [priceRow({ A: 100 })];
    expect(rebaseToCommonStart([], ['A'])).toEqual([]);
    expect(rebaseToCommonStart(rows, [])).toBe(rows); // aynı referansı döner
  });

  it('hiçbir key için __price verisi yoksa satırları değiştirmeden döner', () => {
    const rows = [{ date: 1 }, { date: 2 }];
    // A anahtarına ait __price_A hiçbir satırda yok → present boş → erken return.
    expect(rebaseToCommonStart(rows, ['A'])).toBe(rows);
  });

  it('tüm seriler zaten aynı tarihten (idx 0) başlıyorsa kırpmadan döner', () => {
    const rows = [
      priceRow({ A: 100, B: 200 }),
      priceRow({ A: 110, B: 220 }),
    ];
    // firstIdx A=0, B=0 → startIdx=0 → startIdx<=0 erken return (aynı referans).
    expect(rebaseToCommonStart(rows, ['A', 'B'])).toBe(rows);
  });

  it('ortak başlangıcı en geç ilk-veri tarihine sabitler, öncesini kırpar ve %0\'dan başlatır', () => {
    const rows = [
      priceRow({ A: 100 }),              // idx0: yalnız A
      priceRow({ A: 110, B: 200 }),      // idx1: B ilk burada → ortak başlangıç
      priceRow({ A: 121, B: 220 }),      // idx2
    ];
    const out = rebaseToCommonStart(rows, ['A', 'B']);
    // startIdx=1 → ilk satır (idx0) kırpılır, 2 satır kalır.
    expect(out).toHaveLength(2);
    // Ortak başlangıçta her iki seri de %0.
    expect(out[0].A).toBe(0);
    expect(out[0].B).toBe(0);
    // A: (121-110)/110*100 = 10, B: (220-200)/200*100 = 10.
    expect(out[1].A).toBeCloseTo(10, 3);
    expect(out[1].B).toBeCloseTo(10, 3);
    // Orijinal rows mutasyona uğramamalı (slice + map ile yeni nesneler).
    expect(rows[1].A).toBeUndefined();
  });

  it('B ilk gerçek değeri startIdx’i belirler (null satır firstIdx’e sayılmaz)', () => {
    // B'nin firstIdx'i null OLMAYAN ilk satır = idx2. A firstIdx=0 → startIdx=max(0,2)=2.
    // slice(2) → TEK satır döner (öncesi kırpılır; ortak başlangıç öncesi atılır).
    const rows = [
      priceRow({ A: 100 }),                       // idx0: yalnız A
      { __price_A: 110, __price_B: null },        // idx1: B hâlâ null → firstIdx'e sayılmaz
      priceRow({ A: 121, B: 200 }),               // idx2: B ilk gerçek değer → ortak başlangıç
    ];
    const out = rebaseToCommonStart(rows, ['A', 'B']);
    expect(out).toHaveLength(1);
    // Tek satır = ortak başlangıç: her iki seri de kendi baseline'ına göre %0.
    // A baseline = idx2'deki 121 (startIdx'ten geriye floor), B baseline = 200.
    expect(out[0].A).toBeCloseTo(0, 3);
    expect(out[0].B).toBeCloseTo(0, 3);
  });

  it('baseline alınamayan ve ilk satır olmayan noktada null\'a düşer (son else dalı)', () => {
    // B için startIdx (idx2) öncesinde/üstünde değer yok, ama ileri-aramada idx3'te değer var.
    // Böylece baseline=idx3 değeri; idx2'de (sliced idx0) B null → idx===0 dalı %0 seed eder.
    // idx3 sonrası başka B-null satırı eklersek o satır son else dalına (null) düşer.
    const rows = [
      priceRow({ A: 100 }),                 // idx0
      priceRow({ A: 110 }),                 // idx1
      { __price_A: 120, __price_B: null },  // idx2: ortak başlangıç (A en geç burada? hayır A idx0)
      priceRow({ A: 130, B: 200 }),         // idx3: B ilk değer
      { __price_A: 140, __price_B: null },  // idx4: B yine null
    ];
    // A firstIdx=0, B firstIdx=3 → startIdx=3.
    const out = rebaseToCommonStart(rows, ['A', 'B']);
    // slice(3) → 2 satır.
    expect(out).toHaveLength(2);
    // sliced idx0 = eski idx3: B=200, baseline floor (idx3..0) = 200 → 0.
    expect(out[0].B).toBeCloseTo(0, 3);
    // sliced idx1 = eski idx4: B null, idx!==0 → son else → null.
    expect(out[1].B).toBeNull();
  });
});

describe('interpolateSeries', () => {
  it('bilinen noktalar arasını doğrusal bağlar ve rows\'u yerinde günceller', () => {
    const rows = [
      { K: 0, __price_K: 100 },
      { K: null, __price_K: null },
      { K: 10, __price_K: 120 },
    ];
    const out = interpolateSeries(rows, ['K']);
    // Aynı referansı döner (mutasyon).
    expect(out).toBe(rows);
    // Aradaki nokta t=0.5 → değer (0+10)*0.5 = 5; fiyat 100+(120-100)*0.5 = 110.
    expect(rows[1].K).toBeCloseTo(5, 3);
    expect(rows[1].__price_K).toBeCloseTo(110, 3);
  });

  it('son bilinen noktadan SONRASINI ileri-doldurur (değer + fiyat kopyalanır)', () => {
    const rows = [
      { K: 0, __price_K: 100 },
      { K: 8, __price_K: 116 },
      { K: null, __price_K: null },   // son bilinenden sonra
      { K: null, __price_K: null },
    ];
    interpolateSeries(rows, ['K']);
    // Son bilinen idx1 (K=8) → idx2, idx3 aynısıyla doldurulur.
    expect(rows[2].K).toBe(8);
    expect(rows[2].__price_K).toBe(116);
    expect(rows[3].K).toBe(8);
    expect(rows[3].__price_K).toBe(116);
  });

  it('ilk bilinen noktadan ÖNCESİ null kalır (geriye doldurma yok)', () => {
    const rows = [
      { K: null, __price_K: null },   // ilk bilinenden önce
      { K: 5, __price_K: 105 },
      { K: 7, __price_K: 107 },
    ];
    interpolateSeries(rows, ['K']);
    // İlk satır dokunulmadan null kalmalı.
    expect(rows[0].K).toBeNull();
    expect(rows[0].__price_K).toBeNull();
  });

  it('hiç bilinen nokta yoksa seriye dokunmaz', () => {
    const rows = [
      { K: null, __price_K: null },
      { K: null, __price_K: null },
    ];
    interpolateSeries(rows, ['K']);
    expect(rows[0].K).toBeNull();
    expect(rows[1].K).toBeNull();
  });

  it('__price eksikse yalnız değeri interpole eder, fiyatı atlamaz biçimde bırakır', () => {
    // prA/prB null ise __price interpolasyonu yapılmaz, sadece K interpole edilir.
    const rows = [
      { K: 0, __price_K: null },
      { K: null, __price_K: undefined },
      { K: 4, __price_K: null },
    ];
    interpolateSeries(rows, ['K']);
    expect(rows[1].K).toBeCloseTo(2, 3); // (0+4)*0.5
    // __price_K interpolasyonu atlandı (prA null) → undefined kalır.
    expect(rows[1].__price_K).toBeUndefined();
  });
});

describe('stepFillSeries', () => {
  it('bilinen iki nokta arasını ÖNCEKİ değerle SABİT doldurur (doğrusal DEĞİL — basamak)', () => {
    const rows = [
      { K: 0, __price_K: 100 },
      { K: null, __price_K: null },
      { K: null, __price_K: null },
      { K: 10, __price_K: 120 },
    ];
    const out = stepFillSeries(rows, ['K']);
    expect(out).toBe(rows); // yerinde mutasyon
    // Aradaki noktalar ÖNCEKİ ayın değerinde sabit kalır (doğrusal olsaydı ~3.3, ~6.6 olurdu).
    expect(rows[1].K).toBe(0);
    expect(rows[1].__price_K).toBe(100);
    expect(rows[2].K).toBe(0);
    expect(rows[2].__price_K).toBe(100);
    // Sonraki gerçek noktada (ay sonu) zıplar.
    expect(rows[3].K).toBe(10);
    expect(rows[3].__price_K).toBe(120);
  });

  it('son bilinen noktadan SONRASINI ileri-doldurur', () => {
    const rows = [
      { K: 0, __price_K: 100 },
      { K: 8, __price_K: 116 },
      { K: null, __price_K: null },
      { K: null, __price_K: null },
    ];
    stepFillSeries(rows, ['K']);
    expect(rows[2].K).toBe(8);
    expect(rows[2].__price_K).toBe(116);
    expect(rows[3].K).toBe(8);
    expect(rows[3].__price_K).toBe(116);
  });

  it('ilk bilinen noktadan ÖNCESİ null kalır (geriye doldurma yok)', () => {
    const rows = [
      { K: null, __price_K: null },
      { K: 5, __price_K: 105 },
      { K: 7, __price_K: 107 },
    ];
    stepFillSeries(rows, ['K']);
    expect(rows[0].K).toBeNull();
    expect(rows[0].__price_K).toBeNull();
  });

  it('hiç bilinen nokta yoksa seriye dokunmaz', () => {
    const rows = [
      { K: null, __price_K: null },
      { K: null, __price_K: null },
    ];
    stepFillSeries(rows, ['K']);
    expect(rows[0].K).toBeNull();
    expect(rows[1].K).toBeNull();
  });
});

describe('calcMetrics', () => {
  it('null/boş veya 2\'den az geçerli fiyatta null döner', () => {
    expect(calcMetrics(null)).toBeNull();
    expect(calcMetrics([])).toBeNull();
    expect(calcMetrics([100])).toBeNull();
    // İki eleman var ama biri NaN, diğeri <=0 → filtreden sonra <2 → null.
    expect(calcMetrics(['abc', -5])).toBeNull();
    expect(calcMetrics([0, 0])).toBeNull();
  });

  it('temel metrikleri (start/end/max/min/getiri) doğru hesaplar', () => {
    const m = calcMetrics([100, 120, 90, 110]);
    expect(m).not.toBeNull();
    expect(m.startPrice).toBe(100);
    expect(m.endPrice).toBe(110);
    expect(m.lastPrice).toBe(110);
    expect(m.maxPrice).toBe(120);
    expect(m.minPrice).toBe(90);
    // periodReturn = (110-100)/100*100 = 10.
    expect(m.periodReturn).toBeCloseTo(10, 6);
  });

  it('string fiyatları parse eder ve sıfır/negatif/NaN değerleri eler', () => {
    // '0' ve -10 ve 'xx' elenir; kalan [100,150,200].
    const m = calcMetrics(['100', '0', -10, 'xx', '150', '200']);
    expect(m.startPrice).toBe(100);
    expect(m.endPrice).toBe(200);
    expect(m.maxPrice).toBe(200);
    expect(m.minPrice).toBe(100);
  });

  it('max drawdown negatif yüzde olarak hesaplanır (tepe sonrası en derin düşüş)', () => {
    // 100→120 tepe, sonra 60 → dd = (60-120)/120 = -0.5 → -50%.
    const m = calcMetrics([100, 120, 60, 90]);
    expect(m.drawdown).toBeCloseTo(-50, 6);
  });

  it('sürekli yükselen seride drawdown 0\'dır', () => {
    const m = calcMetrics([100, 110, 120, 130]);
    expect(m.drawdown).toBe(0);
  });

  it('düz (değişmeyen) seride volatilite 0 ve riskAdjusted null olur', () => {
    // Tüm fiyatlar eşit → getiriler hep 0 → varyans 0 → volatilite 0.
    const m = calcMetrics([100, 100, 100, 100]);
    expect(m.volatility).toBeCloseTo(0, 9);
    expect(m.periodReturn).toBe(0);
    // volatility > 0 değil → riskAdjusted null.
    expect(m.riskAdjusted).toBeNull();
  });

  it('volatilite > 0 olduğunda riskAdjusted = getiri / volatilite döner', () => {
    const m = calcMetrics([100, 110, 105, 130]);
    expect(m.volatility).toBeGreaterThan(0);
    expect(m.riskAdjusted).toBeCloseTo(m.periodReturn / m.volatility, 6);
  });

  it('MA değerleri yeterli veri yoksa null, yeterliyse hesaplanır', () => {
    // 4 nokta: ma7/ma25/ma99 hepsi null (uzunluk < n).
    const mShort = calcMetrics([100, 101, 102, 103]);
    expect(mShort.ma7).toBeNull();
    expect(mShort.ma25).toBeNull();
    expect(mShort.ma99).toBeNull();

    // 7 nokta: ma7 hesaplanır (son 7'nin ortalaması), ma25/ma99 hala null.
    const seven = [10, 20, 30, 40, 50, 60, 70];
    const m7 = calcMetrics(seven);
    expect(m7.ma7).toBeCloseTo(40, 6); // (10+..+70)/7
    expect(m7.ma25).toBeNull();
    expect(m7.ma99).toBeNull();
  });

  it('RSI sürekli artışta 100 olur (avgLoss 0) ve aşırı alım uyarısı verir', () => {
    // Hep artan → kayıp yok → avgLoss=0 → rsi=100 → >=70 uyarı.
    const m = calcMetrics([10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20]);
    expect(m.rsi).toBe(100);
    expect(m.rsiWarning).toBe('Aşırı Alım ⚠️');
  });

  it('RSI sürekli düşüşte 0\'a yakın olur ve aşırı satım uyarısı verir', () => {
    const m = calcMetrics([100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 5]);
    expect(m.rsi).toBeLessThanOrEqual(30);
    expect(m.rsiWarning).toBe('Aşırı Satım ⚠️');
  });

  it('RSI nötr bölgedeyken (30<rsi<70) uyarı null olur', () => {
    // Dalgalı seri → RSI orta bölgede kalır.
    const m = calcMetrics([100, 101, 100, 101, 100, 101, 100, 101]);
    expect(m.rsi).toBeGreaterThan(30);
    expect(m.rsi).toBeLessThan(70);
    expect(m.rsiWarning).toBeNull();
  });

  it('güçlü yükseliş trendini tespit eder (getiri>5 + ma7>ma25 + fiyat>ma7)', () => {
    // 30 noktalık güçlü monoton artış → ma7>ma25, fiyat>ma7, getiri çok yüksek.
    const rising = Array.from({ length: 30 }, (_, i) => 100 + i * 5);
    const m = calcMetrics(rising);
    expect(m.trend).toBe('Güçlü Yükselen');
  });

  it('güçlü düşüş trendini tespit eder (getiri<-5 + ma7<ma25 + fiyat<ma7)', () => {
    const falling = Array.from({ length: 30 }, (_, i) => 300 - i * 5);
    const m = calcMetrics(falling);
    expect(m.trend).toBe('Güçlü Düşen');
  });

  it('orta seviye Yükselen trendini getiri>2 ile yakalar (MA verisi yokken)', () => {
    // 2 nokta → ma7/ma25 null, periodReturn=3 → Güçlü değil ama Yükselen dalı (periodReturn>2).
    const m = calcMetrics([100, 103]);
    expect(m.periodReturn).toBeCloseTo(3, 6);
    expect(m.trend).toBe('Yükselen');
  });

  it('orta seviye Düşen trendini getiri<-2 ile yakalar (MA verisi yokken)', () => {
    const m = calcMetrics([100, 97]);
    expect(m.periodReturn).toBeCloseTo(-3, 6);
    expect(m.trend).toBe('Düşen');
  });

  it('tamamen düz uzun seride Yatay döner (getiri 0, ma7==ma25, maGap 0)', () => {
    // 30 sabit nokta → periodReturn 0, ma7==ma25==fiyat → maGapPercent 0 → açık Yatay dalı.
    const flat = Array.from({ length: 30 }, () => 100);
    const m = calcMetrics(flat);
    expect(m.trend).toBe('Yatay');
  });

  it('döndürülen nesne beklenen tüm alanları içerir', () => {
    const m = calcMetrics([100, 110, 105, 115, 120, 118, 130]);
    expect(m).toEqual(
      expect.objectContaining({
        startPrice: expect.any(Number),
        endPrice: expect.any(Number),
        maxPrice: expect.any(Number),
        minPrice: expect.any(Number),
        periodReturn: expect.any(Number),
        drawdown: expect.any(Number),
        volatility: expect.any(Number),
        rsi: expect.any(Number),
        trend: expect.any(String),
        lastPrice: expect.any(Number),
      }),
    );
    // rsiWarning ve riskAdjusted alanları her zaman mevcut (değer null olabilir).
    expect('rsiWarning' in m).toBe(true);
    expect('riskAdjusted' in m).toBe(true);
    expect('ma7' in m).toBe(true);
  });
});
