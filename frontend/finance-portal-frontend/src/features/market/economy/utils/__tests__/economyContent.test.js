import { describe, it, expect } from 'vitest';
import { ECONOMY_TOPICS } from '../economyContent';
import { NAVY, ROSE, EMERALD, AMBER } from '../../components/EconomyChart';

/**
 * economyContent.js SAF bir veri modülüdür: tek export'u ECONOMY_TOPICS sabit dizisidir
 * (fonksiyon içermez). Bu nedenle testler sabit-bütünlüğü/şekil doğrulamasıdır:
 * dizinin varlığı, beklenen anahtarlar, her konunun yapısal geçerliliği, benzersizlik,
 * ve EconomyChart'tan re-export edilen GERÇEK renk sabitleriyle uyum.
 */

// EconomyChart'ın gerçekten render edebildiği grafik tipleri.
const ALLOWED_CHART_TYPES = ['line', 'area', 'bar'];
// EconomyChart'tan re-export edilen gerçek renk sabitleri (mock DEĞİL).
const ALLOWED_COLORS = [NAVY, ROSE, EMERALD, AMBER];

describe('ECONOMY_TOPICS — temel şekil', () => {
  it('boş olmayan bir dizidir', () => {
    expect(Array.isArray(ECONOMY_TOPICS)).toBe(true);
    expect(ECONOMY_TOPICS.length).toBeGreaterThan(0);
  });

  it('beklenen sayıda konu içerir (20)', () => {
    expect(ECONOMY_TOPICS).toHaveLength(20);
  });

  it('beklenen anahtar kümesini doğru sırada listeler', () => {
    expect(ECONOMY_TOPICS.map((t) => t.key)).toEqual([
      'tufe',
      'ufe',
      'cekirdek',
      'abdCpi',
      'politikaFaizi',
      'mevduatFaizi',
      'ihtiyacKredisiFaizi',
      'gsyihBuyume',
      'kisiBasiGelir',
      'issizlik',
      'usdTry',
      'eurTry',
      'bist100',
      'gramAltin',
      'cariDenge',
      'rezervler',
      'reelEfektifKur',
      'butceDengesi',
      'kapasiteKullanim',
      'tuketiciGuven',
    ]);
  });
});

describe('ECONOMY_TOPICS — her konunun yapısı', () => {
  it('her konu zorunlu string alanları (key/navLabel/title/chartTitle) taşır ve boş değildir', () => {
    for (const topic of ECONOMY_TOPICS) {
      for (const field of ['key', 'navLabel', 'title', 'chartTitle', 'chartType', 'color']) {
        expect(topic, `eksik alan: ${field}`).toHaveProperty(field);
        expect(typeof topic[field], `${topic.key}.${field} string olmalı`).toBe('string');
        expect(topic[field].length, `${topic.key}.${field} boş olmamalı`).toBeGreaterThan(0);
      }
    }
  });

  it('her konunun chartType değeri EconomyChart\'ın render edebildiği bir tiptir', () => {
    for (const topic of ECONOMY_TOPICS) {
      expect(ALLOWED_CHART_TYPES, `${topic.key} geçersiz chartType: ${topic.chartType}`)
        .toContain(topic.chartType);
    }
  });

  it('her konunun color değeri EconomyChart\'tan gelen gerçek renk sabitlerinden biridir', () => {
    for (const topic of ECONOMY_TOPICS) {
      expect(ALLOWED_COLORS, `${topic.key} geçersiz color: ${topic.color}`)
        .toContain(topic.color);
    }
  });

  it('her konu en az bir paragraf içeren bir paragraphs dizisi taşır', () => {
    for (const topic of ECONOMY_TOPICS) {
      expect(Array.isArray(topic.paragraphs), `${topic.key}.paragraphs dizi olmalı`).toBe(true);
      expect(topic.paragraphs.length, `${topic.key} en az bir paragraf`).toBeGreaterThan(0);
      for (const p of topic.paragraphs) {
        expect(typeof p).toBe('string');
        expect(p.trim().length).toBeGreaterThan(0);
      }
    }
  });
});

describe('ECONOMY_TOPICS — benzersizlik', () => {
  it('konu anahtarları (key) benzersizdir', () => {
    const keys = ECONOMY_TOPICS.map((t) => t.key);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it('navLabel etiketleri benzersizdir (sticky menüde çakışmasın)', () => {
    const labels = ECONOMY_TOPICS.map((t) => t.navLabel);
    expect(new Set(labels).size).toBe(labels.length);
  });
});

describe('ECONOMY_TOPICS — renk sabitleri (re-export ile uyum)', () => {
  it('EconomyChart renk sabitleri beklenen hex değerlerindedir', () => {
    // economyContent bu sabitleri doğrudan kullanır; değerleri kaynaktan teyit edilir.
    expect(NAVY).toBe('#093eaa');
    expect(ROSE).toBe('#e11d48');
    expect(EMERALD).toBe('#059669');
    expect(AMBER).toBe('#d97706');
  });

  it('belirli konular doğru renge eşlenir', () => {
    const byKey = Object.fromEntries(ECONOMY_TOPICS.map((t) => [t.key, t]));
    // Enflasyon kalemleri ROSE.
    expect(byKey.tufe.color).toBe(ROSE);
    // Faiz/makro kalemleri NAVY.
    expect(byKey.politikaFaizi.color).toBe(NAVY);
    // Borsa EMERALD, altın AMBER.
    expect(byKey.bist100.color).toBe(EMERALD);
    expect(byKey.gramAltin.color).toBe(AMBER);
  });

  it('belirli konular doğru chartType ile tanımlanır', () => {
    const byKey = Object.fromEntries(ECONOMY_TOPICS.map((t) => [t.key, t]));
    expect(byKey.tufe.chartType).toBe('line');
    expect(byKey.politikaFaizi.chartType).toBe('area');
    expect(byKey.gsyihBuyume.chartType).toBe('bar');
  });
});
