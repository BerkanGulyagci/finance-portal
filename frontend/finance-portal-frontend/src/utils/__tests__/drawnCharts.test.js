import { describe, it, expect, beforeEach, vi } from 'vitest';
import { listDrawnCharts, removeDrawnChart } from '../drawnCharts';

// Bir çizim kaydı: en az 1 elemanlı dizi (boş diziler "çizim yok" sayılır).
const draw = (n = 1) => JSON.stringify(Array.from({ length: n }, (_, i) => ({ id: i })));

beforeEach(() => {
  localStorage.clear();
});

describe('listDrawnCharts — anahtar ayrıştırma & route', () => {
  it('hiç çizim yoksa boş dizi', () => {
    expect(listDrawnCharts()).toEqual([]);
  });

  it('chart-overlays: öneki olmayan anahtarları yok sayar', () => {
    localStorage.setItem('some-other-key', draw());
    localStorage.setItem('chart-overlays:crypto:bitcoin', draw());
    const list = listDrawnCharts();
    expect(list).toHaveLength(1);
    expect(list[0].type).toBe('crypto');
  });

  it('boş diziye sahip anahtarı (çizim silinmiş, anahtar kalmış) atlar', () => {
    localStorage.setItem('chart-overlays:crypto:bitcoin', '[]');
    expect(listDrawnCharts()).toEqual([]);
  });

  it('bozuk JSON içeren anahtarı atlar', () => {
    localStorage.setItem('chart-overlays:crypto:bitcoin', '{bozuk');
    expect(listDrawnCharts()).toEqual([]);
  });

  it('kripto: route /market/crypto/{id}, sembol büyük harf gösterilir', () => {
    localStorage.setItem('chart-overlays:crypto:bitcoin', draw());
    const [it0] = listDrawnCharts();
    expect(it0.type).toBe('crypto');
    expect(it0.typeLabel).toBe('Kripto');
    expect(it0.route).toBe('/market/crypto/bitcoin');
    expect(it0.display).toBe('BITCOIN');
  });

  it('döviz: route /market/fx/{symbol}', () => {
    localStorage.setItem('chart-overlays:fx:USDTRY', draw());
    const [it0] = listDrawnCharts();
    expect(it0.route).toBe('/market/fx/USDTRY');
    expect(it0.typeLabel).toBe('Döviz');
  });

  it('fon: seg "tefas" route', () => {
    localStorage.setItem('chart-overlays:fund:TGE', draw());
    const [it0] = listDrawnCharts();
    expect(it0.route).toBe('/market/tefas/TGE');
  });

  it('öneksiz anahtar → hisse, .IS soneki etikette atılır', () => {
    localStorage.setItem('chart-overlays:THYAO.IS', draw());
    const [it0] = listDrawnCharts();
    expect(it0.type).toBe('stock');
    expect(it0.route).toBe('/market/stocks/THYAO.IS');
    expect(it0.display).toBe('THYAO');
  });

  it('öneksiz BIST endeksi → endeks rotasına gider (hisse değil)', () => {
    localStorage.setItem('chart-overlays:XU100.IS', draw());
    const [it0] = listDrawnCharts();
    expect(it0.type).toBe('index');
    expect(it0.route).toBe('/market/indices/XU100');
  });

  it('metal: gold:gram → /market/gold?tab=gram, etiket "Altın · gram"', () => {
    localStorage.setItem('chart-overlays:gold:gram', draw());
    const [it0] = listDrawnCharts();
    expect(it0.route).toBe('/market/gold?tab=gram');
    expect(it0.display).toBe('Altın · gram');
  });

  it('metal: ons sekmesi query eklemez', () => {
    localStorage.setItem('chart-overlays:gold:ons', draw());
    const [it0] = listDrawnCharts();
    expect(it0.route).toBe('/market/gold');
  });

  it('eski bug: sembole bulaşmış ":3A3mo" range ekini soyar', () => {
    localStorage.setItem('chart-overlays:crypto:bitcoin:3A3mo', draw());
    const [it0] = listDrawnCharts();
    expect(it0.symbol).toBe('bitcoin');
    expect(it0.route).toBe('/market/crypto/bitcoin');
  });
});

describe('listDrawnCharts — birleştirme & sıralama', () => {
  it('aynı route\'a düşen mükerrer anahtarları tek satırda toplar, sayıları ekler', () => {
    localStorage.setItem('chart-overlays:XU100.IS', draw(2));
    localStorage.setItem('chart-overlays:XU100.IS:3A3mo', draw(3));
    const list = listDrawnCharts();
    expect(list).toHaveLength(1);
    expect(list[0].count).toBe(5);
    expect(list[0].allKeys).toHaveLength(2);
  });

  it('çizim sayısı çok olan üstte sıralanır', () => {
    localStorage.setItem('chart-overlays:fx:USDTRY', draw(1));
    localStorage.setItem('chart-overlays:fx:EURTRY', draw(5));
    const list = listDrawnCharts();
    expect(list[0].symbol).toBe('EURTRY');
    expect(list[1].symbol).toBe('USDTRY');
  });

  it('count alanı çizim eleman sayısını yansıtır', () => {
    localStorage.setItem('chart-overlays:crypto:bitcoin', draw(4));
    expect(listDrawnCharts()[0].count).toBe(4);
  });
});

describe('removeDrawnChart', () => {
  it('prefSet yokken localStorage anahtarını siler', () => {
    localStorage.setItem('chart-overlays:crypto:bitcoin', draw());
    removeDrawnChart('chart-overlays:crypto:bitcoin');
    expect(localStorage.getItem('chart-overlays:crypto:bitcoin')).toBeNull();
  });

  it('anahtar dizisi verilince hepsini siler', () => {
    localStorage.setItem('chart-overlays:a', draw());
    localStorage.setItem('chart-overlays:b', draw());
    removeDrawnChart(['chart-overlays:a', 'chart-overlays:b']);
    expect(localStorage.getItem('chart-overlays:a')).toBeNull();
    expect(localStorage.getItem('chart-overlays:b')).toBeNull();
  });

  it('prefSet fonksiyonu verilince onu boş liste ile çağırır (localStorage\'a dokunmaz)', () => {
    const prefSet = vi.fn();
    localStorage.setItem('chart-overlays:crypto:bitcoin', draw());
    removeDrawnChart('chart-overlays:crypto:bitcoin', prefSet);
    expect(prefSet).toHaveBeenCalledWith('chart-overlays:crypto:bitcoin', []);
    // prefSet yolunda localStorage doğrudan silinmez (senkron sunucu üzerinden)
    expect(localStorage.getItem('chart-overlays:crypto:bitcoin')).not.toBeNull();
  });

  it('boş/null anahtarları güvenle atlar', () => {
    expect(() => removeDrawnChart([null, '', undefined])).not.toThrow();
  });
});
