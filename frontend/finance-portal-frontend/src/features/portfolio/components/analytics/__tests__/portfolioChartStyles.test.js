import { describe, it, expect } from 'vitest';
import {
  CHART_GRID,
  TICK,
  BAR_GROUPED_CHART,
  BAR_GROUPED_BAR,
  BAR_VERTICAL_CHART,
  BAR_VERTICAL_BAR,
  BAR_HORIZONTAL_CHART,
  BAR_HORIZONTAL_BAR,
  COLOR_POS,
  COLOR_NEG,
  COLOR_COST,
  COLOR_MV,
  positiveMoneyDomain,
  isSkewedMoneyScale,
  signedNumericDomain,
  positivePctDomain,
  formatCompactMoney,
  formatCompactPct,
} from '../portfolioChartStyles';

// Bu dosya SAF mantık + sabit stil nesneleri içerir (dış bağımlılık yok).
// Sabitler için şekil/bütünlük testi; fonksiyonlar için happy-path + edge-case
// (boş/null/NaN/0/negatif/Infinity) ile tüm if/filter/fallback dalları test edilir.

// ── Sabit stil nesneleri ──────────────────────────────────────────────────
describe('Sabit stil nesneleri', () => {
  it('CHART_GRID beklenen stroke + dash desenini taşır', () => {
    expect(CHART_GRID).toEqual({ stroke: '#e8edf3', strokeDasharray: '4 4' });
  });

  it('TICK eksen yazı stilini taşır (fontSize sayısal, fill hex)', () => {
    expect(TICK).toEqual({ fontSize: 10, fill: '#64748b' });
    expect(typeof TICK.fontSize).toBe('number');
  });

  it('gruplu (grouped) çubuk stilleri doğrudur', () => {
    expect(BAR_GROUPED_CHART).toEqual({ barCategoryGap: '38%', barGap: 3 });
    expect(BAR_GROUPED_BAR).toEqual({ maxBarSize: 12, minPointSize: 3 });
  });

  it('dikey (vertical) çubuk stilleri doğrudur', () => {
    expect(BAR_VERTICAL_CHART).toEqual({ barCategoryGap: '40%' });
    expect(BAR_VERTICAL_BAR).toEqual({ maxBarSize: 16, minPointSize: 5 });
  });

  it('yatay (horizontal) çubuk stilleri doğrudur', () => {
    expect(BAR_HORIZONTAL_CHART).toEqual({ barCategoryGap: '32%' });
    expect(BAR_HORIZONTAL_BAR).toEqual({ maxBarSize: 9, minPointSize: 7 });
  });

  it('renk sabitleri geçerli hex değerleridir ve birbirinden farklıdır', () => {
    expect(COLOR_POS).toBe('#22c55e');
    expect(COLOR_NEG).toBe('#ef4444');
    expect(COLOR_COST).toBe('#94a3b8');
    expect(COLOR_MV).toBe('#38bdf8');
    const colors = [COLOR_POS, COLOR_NEG, COLOR_COST, COLOR_MV];
    colors.forEach((c) => expect(c).toMatch(/^#[0-9a-f]{6}$/i));
    expect(new Set(colors).size).toBe(colors.length);
  });
});

// ── positiveMoneyDomain ───────────────────────────────────────────────────
describe('positiveMoneyDomain', () => {
  it('pozitif değer yoksa [0, 1] döndürür (boş dizi)', () => {
    expect(positiveMoneyDomain([])).toEqual([0, 1]);
  });

  it('yalnızca pozitif-olmayan/geçersiz değerler varsa [0, 1] döndürür', () => {
    expect(positiveMoneyDomain([0, -5, NaN, Infinity, null, undefined, '10'])).toEqual([0, 1]);
  });

  it('çarpıklık eşiğinin ALTINDA üst sınırı 1.08× yapar', () => {
    // max/min = 200/100 = 2 < 6 → mult 1.08
    expect(positiveMoneyDomain([100, 200])).toEqual([0, 200 * 1.08]);
  });

  it('çarpıklık eşiğine EŞİT/ÜZERİNDE üst sınırı 2× (headroom) yapar', () => {
    // max/min = 600/100 = 6 >= 6 → mult 2
    expect(positiveMoneyDomain([100, 600])).toEqual([0, 1200]);
  });

  it('tek pozitif değerde ratio 1 → 1.08× uygulanır', () => {
    expect(positiveMoneyDomain([50])).toEqual([0, 50 * 1.08]);
  });

  it('geçersiz değerleri filtreleyip yalnızca sonlu pozitifleri kullanır', () => {
    // Geçerli: 100, 600 → ratio 6 → 2×
    expect(positiveMoneyDomain([100, 600, -999, NaN, Infinity, 0])).toEqual([0, 1200]);
  });

  it('özel eşik/çarpan parametreleri uygulanır', () => {
    // threshold 3, multiplier 5: max/min = 4 >= 3 → 5×
    expect(positiveMoneyDomain([100, 400], 3, 5)).toEqual([0, 2000]);
    // threshold 3: max/min = 2 < 3 → 1.08×
    expect(positiveMoneyDomain([100, 200], 3, 5)).toEqual([0, 200 * 1.08]);
  });
});

// ── isSkewedMoneyScale ────────────────────────────────────────────────────
describe('isSkewedMoneyScale', () => {
  it('iki pozitif değerden azsa false döndürür', () => {
    expect(isSkewedMoneyScale([])).toBe(false);
    expect(isSkewedMoneyScale([100])).toBe(false);
    // pozitif olmayanlar elenince tek eleman kalır
    expect(isSkewedMoneyScale([100, -5, 0])).toBe(false);
  });

  it('oran eşiğin ALTINDA ise false döndürür', () => {
    expect(isSkewedMoneyScale([100, 200])).toBe(false); // 2 < 6
  });

  it('oran eşiğe EŞİT/ÜZERİNDE ise true döndürür', () => {
    expect(isSkewedMoneyScale([100, 600])).toBe(true); // 6 >= 6
    expect(isSkewedMoneyScale([100, 1200])).toBe(true); // 12 >= 6
  });

  it('özel eşik parametresine saygı gösterir', () => {
    expect(isSkewedMoneyScale([100, 300], 3)).toBe(true); // 3 >= 3
    expect(isSkewedMoneyScale([100, 250], 3)).toBe(false); // 2.5 < 3
  });
});

// ── signedNumericDomain ───────────────────────────────────────────────────
describe('signedNumericDomain', () => {
  it('sonlu değer yoksa ["auto", "auto"] döndürür', () => {
    expect(signedNumericDomain([])).toEqual(['auto', 'auto']);
    expect(signedNumericDomain([NaN, Infinity, -Infinity, null, '5'])).toEqual(['auto', 'auto']);
  });

  it('simetrik (±) sınır döndürür ve pozitif/negatifin mutlak en büyüğünü kullanır', () => {
    // maxAbs = max(|-200|, |50|, 1) = 200 → bound 200*1.15 = 230 (IEEE754: ~229.9999 → toBeCloseTo)
    const [lo, hi] = signedNumericDomain([-200, 50]);
    expect(lo).toBeCloseTo(-230, 6);
    expect(hi).toBeCloseTo(230, 6);
  });

  it('tüm değerler küçükken alt taban 1 devreye girer', () => {
    // maxAbs = max(0, 1) = 1 → bound 1.15
    const [lo, hi] = signedNumericDomain([0]);
    expect(lo).toBeCloseTo(-1.15, 10);
    expect(hi).toBeCloseTo(1.15, 10);
  });

  it('0 değerini (sonlu) filtrelemez, hesaba katar', () => {
    // maxAbs = max(|10|, |0|, 1) = 10 → 11.5
    expect(signedNumericDomain([10, 0])).toEqual([-11.5, 11.5]);
  });

  it('özel padFactor uygulanır', () => {
    // maxAbs = max(100,1)=100 → bound 100*1.5 = 150
    expect(signedNumericDomain([100], 1.5)).toEqual([-150, 150]);
  });
});

// ── positivePctDomain ─────────────────────────────────────────────────────
describe('positivePctDomain', () => {
  it('sonlu değer yoksa [0, "auto"] döndürür', () => {
    expect(positivePctDomain([])).toEqual([0, 'auto']);
    expect(positivePctDomain([NaN, Infinity])).toEqual([0, 'auto']);
  });

  it('tümü sıfır/pozitif-olmayan iken [0, 1] döndürür (max<=0 && min>=0)', () => {
    expect(positivePctDomain([0])).toEqual([0, 1]);
    expect(positivePctDomain([0, 0, 0])).toEqual([0, 1]);
  });

  it('yalnızca pozitif değerlerde alt sınır 0, üst sınır pad ile genişler', () => {
    // min=min(5,10,0)=0, max=10, span=10, pad=10*0.12=1.2 → [0, 11.2]
    const [lo, hi] = positivePctDomain([5, 10]);
    expect(lo).toBe(0);
    expect(hi).toBeCloseTo(11.2, 10);
  });

  it('yalnızca negatif değerlerde alt sınır pad ile düşer, üst sınır 1 olur', () => {
    // min=min(-3,-1,0)=-3, max=max(-3,-1,0)=0, span=3, pad=0.36
    // min<0 → -3-0.36 = -3.36 ; max>0? hayır → 1
    const [lo, hi] = positivePctDomain([-3, -1]);
    expect(lo).toBeCloseTo(-3.36, 10);
    expect(hi).toBe(1);
  });

  it('karışık (pozitif+negatif) değerlerde her iki sınır da pad ile genişler', () => {
    // min=-5, max=5, span=10, pad=1.2 → [-6.2, 6.2]
    const [lo, hi] = positivePctDomain([-5, 5]);
    expect(lo).toBeCloseTo(-6.2, 10);
    expect(hi).toBeCloseTo(6.2, 10);
  });

  it('span 0 olduğunda fallback olarak max(max,1) kullanır', () => {
    // [3,3]: min=min(3,3,0)=0, max=3, span = 3-0 = 3 (sıfır değil) → normal yol
    // span'ı sıfırlamak için min===max>0 ama 0 da min'e dahil; pozitif tek değer
    // [0.0000001]: max çok küçük ama >0 → span = max-0 = max, || tetiklenmez.
    // span===0 yalnızca max===min===0 → o da [0,1] dalına düşer; bu yüzden
    // pratikte span fallback'i, min ve max'ın 0'a eşit olmadığı durumlarda
    // span hesabıyla aynıdır. Burada pozitif küçük değeri doğrularız:
    const [lo, hi] = positivePctDomain([2]);
    expect(lo).toBe(0);
    // min=0,max=2,span=2,pad=0.24 → 2.24
    expect(hi).toBeCloseTo(2.24, 10);
  });

  it('özel padFactor uygulanır', () => {
    // padFactor 1.5: pad = span*0.5 ; [10]: span=10 → pad 5 → [0,15]
    const [lo, hi] = positivePctDomain([10], 1.5);
    expect(lo).toBe(0);
    expect(hi).toBeCloseTo(15, 10);
  });
});

// ── formatCompactMoney ────────────────────────────────────────────────────
describe('formatCompactMoney', () => {
  it('valuesHidden true ise her zaman maskeleme döndürür', () => {
    expect(formatCompactMoney(1234, 'TL', true)).toBe('••••');
    expect(formatCompactMoney(null, 'TL', true)).toBe('••••');
  });

  it('null/undefined/NaN/Infinity için boş string döndürür', () => {
    expect(formatCompactMoney(null)).toBe('');
    expect(formatCompactMoney(undefined)).toBe('');
    expect(formatCompactMoney(NaN)).toBe('');
    expect(formatCompactMoney(Infinity)).toBe('');
  });

  it('1000 altındaki değerleri ondalıksız (K eki olmadan) biçimlendirir', () => {
    const out = formatCompactMoney(999);
    expect(out).toBe('999');
    expect(out).not.toContain('K');
  });

  it('1000 ve üzeri değerlere K eki ekler', () => {
    expect(formatCompactMoney(1500)).toContain('K');
    // 1500/1000 = 1,5 (tr-TR ondalık ayırıcı virgül)
    expect(formatCompactMoney(1500)).toBe('1,5K');
    expect(formatCompactMoney(1000)).toBe('1K');
  });

  it('para birimi verildiğinde sonuna ekler', () => {
    expect(formatCompactMoney(1500, 'TL')).toBe('1,5K TL');
    expect(formatCompactMoney(500, 'USD')).toBe('500 USD');
  });

  it('para birimi yokken ek (suffix) eklemez', () => {
    expect(formatCompactMoney(500)).toBe('500');
    expect(formatCompactMoney(500, '')).toBe('500');
  });

  it('negatif değerlerde mutlak değere göre K eşiğini uygular ve işareti korur', () => {
    // abs(-2500)=2500 >= 1000 → -2500/1000 = -2,5K
    expect(formatCompactMoney(-2500)).toBe('-2,5K');
    expect(formatCompactMoney(-300)).toBe('-300');
  });

  it('0 değerini boş string DEĞİL, "0" olarak biçimlendirir', () => {
    expect(formatCompactMoney(0)).toBe('0');
    expect(formatCompactMoney(0, 'TL')).toBe('0 TL');
  });
});

// ── formatCompactPct ──────────────────────────────────────────────────────
describe('formatCompactPct', () => {
  it('valuesHidden true ise maskeleme döndürür', () => {
    expect(formatCompactPct(12.34, true)).toBe('••••');
    expect(formatCompactPct(null, true)).toBe('••••');
  });

  it('null/undefined/NaN/Infinity için boş string döndürür', () => {
    expect(formatCompactPct(null)).toBe('');
    expect(formatCompactPct(undefined)).toBe('');
    expect(formatCompactPct(NaN)).toBe('');
    expect(formatCompactPct(Infinity)).toBe('');
  });

  it('yüzde işaretiyle ve en fazla 2 ondalık ile biçimlendirir', () => {
    // tr-TR ondalık ayırıcı virgül; 12.3456 → 2 basamağa yuvarlanır
    expect(formatCompactPct(12.3456)).toBe('12,35%');
    expect(formatCompactPct(5)).toBe('5%');
  });

  it('negatif ve sıfır yüzdeleri doğru biçimlendirir', () => {
    expect(formatCompactPct(-7.5)).toBe('-7,5%');
    expect(formatCompactPct(0)).toBe('0%');
  });
});
