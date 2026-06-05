import { describe, it, expect } from 'vitest';
import { ANALYTICS_CHARTS, ANALYTICS_BY_KEY } from '../analyticsRegistry';

// Bu dosya saf bir kayıt (registry) — export fonksiyon yok, yalnızca veri yapıları var.
// Bu yüzden sabit-bütünlüğü/şekil testleri yazıyoruz: anahtar varlığı, alan tipleri,
// türetilmiş haritanın (ANALYTICS_BY_KEY) kaynak diziyle tutarlılığı.

describe('ANALYTICS_CHARTS', () => {
  it('boş olmayan bir dizidir', () => {
    expect(Array.isArray(ANALYTICS_CHARTS)).toBe(true);
    expect(ANALYTICS_CHARTS.length).toBeGreaterThan(0);
  });

  it('beklenen 13 kart tanımını içerir', () => {
    // 9 grafik kartı + 4 AI kartı
    expect(ANALYTICS_CHARTS).toHaveLength(13);
  });

  it('anahtar (key) listesi beklenen sırada ve eksiksizdir', () => {
    expect(ANALYTICS_CHARTS.map((c) => c.key)).toEqual([
      'allocation',
      'assetDist',
      'costValue',
      'profitLoss',
      'typePL',
      'dailyStatus',
      'categoryChange',
      'dailyContribution',
      'aiRisk',
      'aiHealth',
      'gainersLosers',
      'aiIdentity',
      'aiMonteCarlo',
    ]);
  });

  it('her kart zorunlu alanlara (key/label/Comp/w/h) sahip ve tipleri doğrudur', () => {
    for (const card of ANALYTICS_CHARTS) {
      expect(typeof card.key).toBe('string');
      expect(card.key.length).toBeGreaterThan(0);

      expect(typeof card.label).toBe('string');
      expect(card.label.length).toBeGreaterThan(0);

      // Comp bir React bileşeni (fonksiyon/obje) — tanımlı olmalı
      expect(card.Comp).toBeDefined();
      expect(['function', 'object']).toContain(typeof card.Comp);

      // GridBoard başlangıç boyutları pozitif tam sayı olmalı
      expect(Number.isInteger(card.w)).toBe(true);
      expect(card.w).toBeGreaterThan(0);
      expect(Number.isInteger(card.h)).toBe(true);
      expect(card.h).toBeGreaterThan(0);
    }
  });

  it('anahtarlar (key) benzersizdir', () => {
    const keys = ANALYTICS_CHARTS.map((c) => c.key);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it('etiketler (label) benzersizdir', () => {
    const labels = ANALYTICS_CHARTS.map((c) => c.label);
    expect(new Set(labels).size).toBe(labels.length);
  });

  it('AI kartları (ai* öneki) tam olarak 4 tanedir ve beklenen anahtarları taşır', () => {
    const aiKeys = ANALYTICS_CHARTS.map((c) => c.key).filter((k) => k.startsWith('ai'));
    expect(aiKeys).toEqual(['aiRisk', 'aiHealth', 'aiIdentity', 'aiMonteCarlo']);
  });

  it('AI kartı olmayan grafik kartları tam olarak 9 tanedir', () => {
    const nonAi = ANALYTICS_CHARTS.filter((c) => !c.key.startsWith('ai'));
    expect(nonAi).toHaveLength(9);
  });

  it('AI kartlarının etiketleri "(AI)" ibaresi içerir', () => {
    const aiCards = ANALYTICS_CHARTS.filter((c) => c.key.startsWith('ai'));
    for (const card of aiCards) {
      expect(card.label).toContain('(AI)');
    }
  });

  it('belirli kartların boyut (w/h) değerleri kaynaktaki tanımla eşleşir', () => {
    const allocation = ANALYTICS_CHARTS.find((c) => c.key === 'allocation');
    expect(allocation).toMatchObject({ label: 'Varlık Türü Dağılımı', w: 4, h: 15 });

    const gainersLosers = ANALYTICS_CHARTS.find((c) => c.key === 'gainersLosers');
    expect(gainersLosers).toMatchObject({
      label: 'En Çok Kazandıran / Kaybettiren',
      w: 8,
      h: 10,
    });

    const aiMonteCarlo = ANALYTICS_CHARTS.find((c) => c.key === 'aiMonteCarlo');
    expect(aiMonteCarlo).toMatchObject({ label: 'Monte Carlo Projeksiyon (AI)', w: 4, h: 13 });
  });
});

describe('ANALYTICS_BY_KEY', () => {
  it('bir nesnedir ve dizi DEĞİLDİR', () => {
    expect(typeof ANALYTICS_BY_KEY).toBe('object');
    expect(ANALYTICS_BY_KEY).not.toBeNull();
    expect(Array.isArray(ANALYTICS_BY_KEY)).toBe(false);
  });

  it('ANALYTICS_CHARTS ile aynı sayıda girdiye sahiptir', () => {
    expect(Object.keys(ANALYTICS_BY_KEY)).toHaveLength(ANALYTICS_CHARTS.length);
  });

  it('her kartın key alanını anahtar olarak kullanır', () => {
    expect(Object.keys(ANALYTICS_BY_KEY).sort()).toEqual(
      ANALYTICS_CHARTS.map((c) => c.key).sort(),
    );
  });

  it('her anahtar, kaynaktaki ilgili kart nesnesinin AYNI referansına işaret eder', () => {
    for (const card of ANALYTICS_CHARTS) {
      expect(ANALYTICS_BY_KEY[card.key]).toBe(card);
    }
  });

  it('bilinen bir anahtar için doğru kartı döndürür', () => {
    expect(ANALYTICS_BY_KEY.allocation).toBeDefined();
    expect(ANALYTICS_BY_KEY.allocation.label).toBe('Varlık Türü Dağılımı');
    expect(ANALYTICS_BY_KEY.aiRisk.label).toBe('Risk Skoru (AI)');
  });

  it('bilinmeyen bir anahtar için undefined döndürür', () => {
    expect(ANALYTICS_BY_KEY.bilinmeyenAnahtar).toBeUndefined();
    expect(ANALYTICS_BY_KEY['']).toBeUndefined();
  });
});
