import { describe, it, expect } from 'vitest';
import { COMPARE_COLORS, POPULAR_COINS, MA_OPTIONS } from '../cryptoChartConfig';

// Bu modül SAF sabitlerden oluşur (fonksiyon yok) — şekil/bütünlük testleri yazılır.

const HEX_COLOR = /^#[0-9a-fA-F]{6}$/;

describe('COMPARE_COLORS', () => {
  it('beş renkli bir dizidir', () => {
    expect(Array.isArray(COMPARE_COLORS)).toBe(true);
    expect(COMPARE_COLORS).toHaveLength(5);
  });

  it('ilk renk ana coin için lacivert (#093eaa)', () => {
    // Yorumda belirtildiği gibi ilk renk ana coin'e ayrılmıştır.
    expect(COMPARE_COLORS[0]).toBe('#093eaa');
  });

  it('tüm değerler geçerli 6 haneli hex renk kodudur', () => {
    for (const c of COMPARE_COLORS) {
      expect(typeof c).toBe('string');
      expect(c).toMatch(HEX_COLOR);
    }
  });

  it('renkler benzersizdir (karşılaştırmada ayırt edilebilsin)', () => {
    expect(new Set(COMPARE_COLORS).size).toBe(COMPARE_COLORS.length);
  });

  it('beklenen renk listesini birebir taşır', () => {
    expect(COMPARE_COLORS).toEqual([
      '#093eaa', '#f97316', '#8b5cf6', '#10b981', '#ef4444',
    ]);
  });
});

describe('POPULAR_COINS', () => {
  it('sekiz popüler coin içeren bir dizidir', () => {
    expect(Array.isArray(POPULAR_COINS)).toBe(true);
    expect(POPULAR_COINS).toHaveLength(8);
  });

  it('her coin id/symbol/name alanlarına sahip ve hepsi string', () => {
    for (const coin of POPULAR_COINS) {
      expect(coin).toHaveProperty('id');
      expect(coin).toHaveProperty('symbol');
      expect(coin).toHaveProperty('name');
      expect(typeof coin.id).toBe('string');
      expect(typeof coin.symbol).toBe('string');
      expect(typeof coin.name).toBe('string');
      // boş olmamalı
      expect(coin.id.length).toBeGreaterThan(0);
      expect(coin.symbol.length).toBeGreaterThan(0);
      expect(coin.name.length).toBeGreaterThan(0);
    }
  });

  it('id değerleri benzersizdir', () => {
    const ids = POPULAR_COINS.map((c) => c.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('symbol değerleri benzersizdir', () => {
    const symbols = POPULAR_COINS.map((c) => c.symbol);
    expect(new Set(symbols).size).toBe(symbols.length);
  });

  it('symbol değerleri büyük harftir', () => {
    for (const coin of POPULAR_COINS) {
      expect(coin.symbol).toBe(coin.symbol.toUpperCase());
    }
  });

  it('id değerleri CoinGecko stilinde küçük harftir', () => {
    // CoinGecko id'leri küçük harf slug biçimindedir (örn. "bitcoin", "binancecoin").
    for (const coin of POPULAR_COINS) {
      expect(coin.id).toBe(coin.id.toLowerCase());
    }
  });

  it('ilk sırada Bitcoin (BTC) bulunur', () => {
    expect(POPULAR_COINS[0]).toEqual({ id: 'bitcoin', symbol: 'BTC', name: 'Bitcoin' });
  });

  it('beklenen sembol listesini doğru sırada taşır', () => {
    expect(POPULAR_COINS.map((c) => c.symbol)).toEqual([
      'BTC', 'ETH', 'USDT', 'BNB', 'SOL', 'XRP', 'DOGE', 'ADA',
    ]);
  });

  it('id ile arama yapıldığında ilgili coin bulunabilir', () => {
    const eth = POPULAR_COINS.find((c) => c.id === 'ethereum');
    expect(eth).toBeDefined();
    expect(eth.symbol).toBe('ETH');
    expect(eth.name).toBe('Ethereum');
  });
});

describe('MA_OPTIONS', () => {
  it('üç hareketli ortalama seçeneği içeren bir dizidir', () => {
    expect(Array.isArray(MA_OPTIONS)).toBe(true);
    expect(MA_OPTIONS).toHaveLength(3);
  });

  it('her seçenek period (sayı) / label (string) / color (hex) alanlarına sahip', () => {
    for (const opt of MA_OPTIONS) {
      expect(typeof opt.period).toBe('number');
      expect(typeof opt.label).toBe('string');
      expect(typeof opt.color).toBe('string');
      expect(opt.color).toMatch(HEX_COLOR);
    }
  });

  it('period değerleri pozitif ve artan sıradadır (20, 50, 200)', () => {
    const periods = MA_OPTIONS.map((o) => o.period);
    expect(periods).toEqual([20, 50, 200]);
    for (const p of periods) {
      expect(p).toBeGreaterThan(0);
    }
  });

  it('label değerleri MA + period kalıbına uyar', () => {
    for (const opt of MA_OPTIONS) {
      expect(opt.label).toBe(`MA${opt.period}`);
    }
  });

  it('period değerleri benzersizdir', () => {
    const periods = MA_OPTIONS.map((o) => o.period);
    expect(new Set(periods).size).toBe(periods.length);
  });

  it('renkler benzersizdir', () => {
    const colors = MA_OPTIONS.map((o) => o.color);
    expect(new Set(colors).size).toBe(colors.length);
  });

  it('beklenen seçenek listesini birebir taşır', () => {
    expect(MA_OPTIONS).toEqual([
      { period: 20, label: 'MA20', color: '#f59e0b' },
      { period: 50, label: 'MA50', color: '#8b5cf6' },
      { period: 200, label: 'MA200', color: '#ef4444' },
    ]);
  });
});
