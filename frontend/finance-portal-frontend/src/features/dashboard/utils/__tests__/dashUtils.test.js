import { describe, it, expect } from 'vitest';
import { num, fmtMoney, fmtPct, pctClass, ASSET_LABEL } from '../dashUtils';

// Not: dashUtils SAF (HTTP/modül bağımlılığı yok), bu yüzden mock kullanılmadı.
// toLocaleString('tr-TR') jsdom/Node ortamında tam ICU ile çalışır:
// binlik=nokta, ondalık=virgül.

describe('num', () => {
  it('number girdiyi olduğu gibi döner', () => {
    expect(num(5)).toBe(5);
    expect(num(0)).toBe(0);
    expect(num(-12.5)).toBe(-12.5);
  });

  it('sayısal string i parse eder', () => {
    expect(num('42')).toBe(42);
    expect(num('3.14')).toBeCloseTo(3.14);
    // parseFloat baştan parse eder: "12abc" → 12
    expect(num('12abc')).toBe(12);
  });

  it('null/undefined/boş/geçersiz için 0 döner (fallback)', () => {
    expect(num(null)).toBe(0);
    expect(num(undefined)).toBe(0);
    expect(num('')).toBe(0);
    expect(num('abc')).toBe(0);
  });

  it('sonsuz/NaN sayılar Number.isFinite false → 0 döner', () => {
    expect(num(NaN)).toBe(0);
    expect(num(Infinity)).toBe(0);
    expect(num(-Infinity)).toBe(0);
  });

  it('typeof number olmayan ama parse edilebilen tipte parseFloat yolunu kullanır', () => {
    // boolean number değil → parseFloat(true)=NaN → 0
    expect(num(true)).toBe(0);
  });
});

describe('fmtMoney', () => {
  it('varsayılan 2 ondalık ile TR formatlar', () => {
    expect(fmtMoney(1234.5)).toBe('1.234,50');
    expect(fmtMoney(1234567.89)).toBe('1.234.567,89');
    expect(fmtMoney(0)).toBe('0,00');
  });

  it('geçersiz/null değer num üzerinden 0 a düşer', () => {
    expect(fmtMoney(null)).toBe('0,00');
    expect(fmtMoney(undefined)).toBe('0,00');
    expect(fmtMoney('abc')).toBe('0,00');
    expect(fmtMoney(NaN)).toBe('0,00');
  });

  it('min/max ondalık seçenekleri uygulanır', () => {
    // min:0,max:0 → tam sayı, ondalık yok
    expect(fmtMoney(5, { min: 0, max: 0 })).toBe('5');
    // -50,256 → 2 basamağa yuvarlanır (yarıya yuvarlama)
    expect(fmtMoney(-50.256, { min: 2, max: 2 })).toBe('-50,26');
    // min:0,max:4 → gereksiz sondaki sıfırlar eklenmez
    expect(fmtMoney(3.5, { min: 0, max: 4 })).toBe('3,5');
  });

  it('sadece min verilince max varsayılan 2 olarak kalır (kısmi options)', () => {
    // {min:0} → max=2 (destructuring varsayılanı), 5 → "5" (max=2 ama min=0 trailing yok)
    expect(fmtMoney(5, { min: 0 })).toBe('5');
    // {max:4} → min=2, 1 → "1,00"
    expect(fmtMoney(1, { max: 4 })).toBe('1,00');
  });

  it('sayısal string i de formatlar', () => {
    expect(fmtMoney('1234.5')).toBe('1.234,50');
  });
});

describe('fmtPct', () => {
  it('pozitif değere + öneki ekler', () => {
    expect(fmtPct(2.5)).toBe('+2.50%');
  });

  it('toFixed kullandığı için ondalık ayracı NOKTA dır (virgül DEĞİL)', () => {
    // Dikkat: fmtMoney toLocaleString(virgül) kullanırken fmtPct toFixed(nokta) kullanır.
    expect(fmtPct(2.5)).toBe('+2.50%');
    expect(fmtPct(12.3456)).toBe('+12.35%');
  });

  it('sıfır >= 0 olduğu için + öneki alır', () => {
    // koşul n >= 0 → 0 da "+" alır
    expect(fmtPct(0)).toBe('+0.00%');
  });

  it('negatif değerde + eklenmez (eksi işareti toFixed ten gelir)', () => {
    expect(fmtPct(-2.5)).toBe('-2.50%');
    expect(fmtPct(-0.01)).toBe('-0.01%');
  });

  it('digits parametresi ondalık sayısını değiştirir', () => {
    expect(fmtPct(1.23456, 4)).toBe('+1.2346%');
    expect(fmtPct(1.23456, 0)).toBe('+1%');
    expect(fmtPct(-1.23456, 3)).toBe('-1.235%');
  });

  it('geçersiz/null değer num üzerinden 0 a düşer ve + alır', () => {
    expect(fmtPct(null)).toBe('+0.00%');
    expect(fmtPct(undefined)).toBe('+0.00%');
    expect(fmtPct('abc')).toBe('+0.00%');
    expect(fmtPct(NaN)).toBe('+0.00%');
  });
});

describe('pctClass', () => {
  it('pozitif değer için emerald (yeşil) sınıfı', () => {
    expect(pctClass(1)).toBe('text-emerald-600');
    expect(pctClass(0.0001)).toBe('text-emerald-600');
    expect(pctClass('5')).toBe('text-emerald-600');
  });

  it('negatif değer için rose (kırmızı) sınıfı', () => {
    expect(pctClass(-1)).toBe('text-rose-600');
    expect(pctClass(-0.0001)).toBe('text-rose-600');
  });

  it('sıfır için nötr gray sınıfı', () => {
    // n > 0 değil, n < 0 değil → gray
    expect(pctClass(0)).toBe('text-gray-500');
  });

  it('null/undefined/geçersiz num üzerinden 0 → gray döner', () => {
    expect(pctClass(null)).toBe('text-gray-500');
    expect(pctClass(undefined)).toBe('text-gray-500');
    expect(pctClass('abc')).toBe('text-gray-500');
    expect(pctClass(NaN)).toBe('text-gray-500');
  });
});

describe('ASSET_LABEL', () => {
  it('bilinen varlık türlerini Türkçe etikete eşler', () => {
    expect(ASSET_LABEL.STOCK).toBe('Hisse');
    expect(ASSET_LABEL.FUND).toBe('Fon');
    expect(ASSET_LABEL.FX).toBe('Döviz');
    expect(ASSET_LABEL.FUTURE).toBe('Vadeli');
    expect(ASSET_LABEL.CRYPTO).toBe('Kripto');
    expect(ASSET_LABEL.GOLD).toBe('Altın');
    expect(ASSET_LABEL.COMMODITY).toBe('Emtia');
    expect(ASSET_LABEL.BOND).toBe('Tahvil');
  });

  it('tam olarak 8 varlık türü içerir', () => {
    expect(Object.keys(ASSET_LABEL)).toHaveLength(8);
  });

  it('bilinmeyen anahtar için undefined döner (fallback yok)', () => {
    expect(ASSET_LABEL.UNKNOWN).toBeUndefined();
    expect(ASSET_LABEL['']).toBeUndefined();
  });
});
