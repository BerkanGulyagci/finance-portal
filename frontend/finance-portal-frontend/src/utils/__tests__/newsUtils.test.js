import { describe, it, expect } from 'vitest';
import {
  CATEGORY_BADGE,
  categoryBadgeClass,
  formatNewsTime,
  groupSources,
  formatNewsDate,
} from '../newsUtils';

describe('CATEGORY_BADGE sabiti', () => {
  it('bilinen tüm kategoriler için Tailwind sınıfı içerir', () => {
    expect(CATEGORY_BADGE.ECONOMY).toBe('bg-slate-100 text-slate-700');
    expect(CATEGORY_BADGE.STOCKS).toBe('bg-emerald-100 text-emerald-700');
    expect(CATEGORY_BADGE.FX).toBe('bg-sky-100 text-sky-700');
    expect(CATEGORY_BADGE.BONDS).toBe('bg-amber-100 text-amber-800');
    expect(CATEGORY_BADGE.CRYPTO).toBe('bg-violet-100 text-violet-700');
    expect(CATEGORY_BADGE.COMMODITIES).toBe('bg-yellow-100 text-yellow-800');
    expect(CATEGORY_BADGE.WORLD).toBe('bg-rose-100 text-rose-700');
  });

  it('tam olarak 7 kategori tanımlar', () => {
    expect(Object.keys(CATEGORY_BADGE)).toHaveLength(7);
  });
});

describe('categoryBadgeClass', () => {
  it('bilinen kategori için eşleşen sınıfı döndürür', () => {
    expect(categoryBadgeClass('CRYPTO')).toBe('bg-violet-100 text-violet-700');
    expect(categoryBadgeClass('ECONOMY')).toBe('bg-slate-100 text-slate-700');
  });

  it('bilinmeyen kategori için varsayılan gri sınıfı döndürür', () => {
    expect(categoryBadgeClass('UNKNOWN')).toBe('bg-gray-100 text-gray-600');
  });

  // null/undefined nesnede bulunmaz → ?? fallback devreye girer.
  it('null/undefined anahtarda varsayılana düşer', () => {
    expect(categoryBadgeClass(null)).toBe('bg-gray-100 text-gray-600');
    expect(categoryBadgeClass(undefined)).toBe('bg-gray-100 text-gray-600');
  });

  // Boş string da haritada yok → fallback. (?? sadece null/undefined için değil, eşleşmeyen her anahtar için.)
  it('boş string anahtarda varsayılana düşer', () => {
    expect(categoryBadgeClass('')).toBe('bg-gray-100 text-gray-600');
  });
});

describe('formatNewsTime', () => {
  it('raw boş/null/undefined ise boş string döndürür', () => {
    expect(formatNewsTime('')).toBe('');
    expect(formatNewsTime(null)).toBe('');
    expect(formatNewsTime(undefined)).toBe('');
  });

  it('1 dakikadan yeni ise "az önce" döndürür', () => {
    const now = new Date().toISOString();
    expect(formatNewsTime(now)).toBe('az önce');
  });

  it('60 dakikadan az ise "<dk> dk önce" döndürür', () => {
    const fiveMinAgo = new Date(Date.now() - 5 * 60000).toISOString();
    expect(formatNewsTime(fiveMinAgo)).toBe('5 dk önce');
  });

  it('60 dk ile 1440 dk arası ise "<saat> saat önce" döndürür', () => {
    const threeHoursAgo = new Date(Date.now() - 3 * 60 * 60000).toISOString();
    expect(formatNewsTime(threeHoursAgo)).toBe('3 saat önce');
  });

  it('1440 dakikadan (1 gün) eski ise tam tarih döndürür', () => {
    // 2 gün önce → göreli değil, tr-TR uzun tarih biçimi.
    const twoDaysAgo = new Date(Date.now() - 2 * 1440 * 60000).toISOString();
    const out = formatNewsTime(twoDaysAgo);
    expect(out).not.toMatch(/önce/);
    // "3 Haziran 2026" gibi: yıl içermeli.
    expect(out).toMatch(/\d{4}/);
  });

  // t çeviri fonksiyonu verilebilir; etiketleri dönüştürür ama "5" gibi sayısal interpolasyon t'den geçmez.
  it('t çeviri fonksiyonunu etiketlere uygular', () => {
    const t = (x) => `<<${x}>>`;
    const now = new Date().toISOString();
    expect(formatNewsTime(now, t)).toBe('<<az önce>>');

    const tenMinAgo = new Date(Date.now() - 10 * 60000).toISOString();
    expect(formatNewsTime(tenMinAgo, t)).toBe('10 <<dk önce>>');

    const twoHoursAgo = new Date(Date.now() - 2 * 60 * 60000).toISOString();
    expect(formatNewsTime(twoHoursAgo, t)).toBe('2 <<saat önce>>');
  });

  it('t verilmezse kimlik fonksiyonu varsayılanıyla etiketi olduğu gibi bırakır', () => {
    const tenMinAgo = new Date(Date.now() - 10 * 60000).toISOString();
    expect(formatNewsTime(tenMinAgo)).toBe('10 dk önce');
  });

  // Geçersiz tarih THROW etmez: new Date('xx').getTime() → NaN, tüm diff<.. karşılaştırmaları false →
  // son satırdaki toLocaleDateString çalışır ve "Invalid Date" döndürür (catch'e DÜŞMEZ).
  it('geçersiz tarih girişinde "Invalid Date" döndürür (hata fırlatmaz)', () => {
    expect(formatNewsTime('not-a-real-date')).toBe('Invalid Date');
  });
});

describe('groupSources', () => {
  it('null/undefined girişte boş dizi döndürür', () => {
    expect(groupSources(null)).toEqual([]);
    expect(groupSources(undefined)).toEqual([]);
  });

  it('boş dizide boş dizi döndürür', () => {
    expect(groupSources([])).toEqual([]);
  });

  it('tek kaynaklı grubu olduğu gibi bırakır', () => {
    expect(groupSources(['Bloomberg'])).toEqual(['Bloomberg']);
  });

  // Aynı ilk kelimeyi paylaşan ≥2 kaynak ortak kelime önekinde toplanır.
  it('aynı ilk kelimeli birden çok kaynağı ortak önekte gruplar', () => {
    const out = groupSources(['Kriptofoni', 'Kriptofoni Altcoin', 'Kriptofoni Bitcoin']);
    expect(out).toEqual(['Kriptofoni']);
  });

  // Ortak önek ilk kelimeden uzun olabilir.
  it('birden fazla ortak kelimeyi önek olarak korur', () => {
    const out = groupSources(['Anadolu Ajansı Spor', 'Anadolu Ajansı Ekonomi']);
    expect(out).toEqual(['Anadolu Ajansı']);
  });

  it('farklı ilk kelimeli kaynakları ayrı tutar ve tr-locale sıralar', () => {
    const out = groupSources(['Zaman', 'Çukurova', 'Anadolu']);
    // tr sıralama: A < Ç < Z
    expect(out).toEqual(['Anadolu', 'Çukurova', 'Zaman']);
  });

  it('grupları ve tekleri birlikte doğru üretir', () => {
    const out = groupSources(['Reuters', 'Kriptofoni', 'Kriptofoni Altcoin']);
    expect(out).toEqual(['Kriptofoni', 'Reuters']);
  });

  // longestCommonWordPrefix: ilk kelimeyi paylaşıp sonrası AYRIK olduğunda en azından ilk kelime döner.
  it('sadece ilk kelime ortakken ilk kelimeye iner', () => {
    const out = groupSources(['Haber Global', 'Haber Türk']);
    expect(out).toEqual(['Haber']);
  });
});

describe('formatNewsDate', () => {
  it('raw boş/null/undefined ise boş string döndürür', () => {
    expect(formatNewsDate('')).toBe('');
    expect(formatNewsDate(null)).toBe('');
    expect(formatNewsDate(undefined)).toBe('');
  });

  it('geçerli ISO tarihinde gün/ay/yıl ve saat içeren tr-TR biçimi döndürür', () => {
    const out = formatNewsDate('2026-01-15T08:30:00Z');
    // Yıl ve saat:dakika kalıbı bulunmalı.
    expect(out).toMatch(/2026/);
    expect(out).toMatch(/\d{2}:\d{2}/);
  });

  // toLocaleString geçersiz tarihte de THROW etmez → "Invalid Date" döner (catch tetiklenmez).
  it('geçersiz tarih girişinde "Invalid Date" döndürür', () => {
    expect(formatNewsDate('garbage')).toBe('Invalid Date');
  });
});
