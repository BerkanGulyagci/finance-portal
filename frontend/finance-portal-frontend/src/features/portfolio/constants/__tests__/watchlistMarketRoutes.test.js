import { describe, it, expect } from 'vitest';
import {
  getPreciousMetalMarketPath,
  getWatchlistDetailPath,
} from '../watchlistMarketRoutes';

describe('getPreciousMetalMarketPath', () => {
  it('SILVER:GRAM_TRY → gümüş sayfası, try_gram sekmesi', () => {
    expect(getPreciousMetalMarketPath('SILVER:GRAM_TRY')).toEqual({
      path: '/market/silver',
      tab: 'try_gram',
    });
  });

  it('SILVER:USD_ONS → gümüş sayfası, usd_ons sekmesi', () => {
    expect(getPreciousMetalMarketPath('SILVER:USD_ONS')).toEqual({
      path: '/market/silver',
      tab: 'usd_ons',
    });
  });

  it('SILVER:EUR_ONS → gümüşte EUR sekmesi yok, usd_ons’a düşer', () => {
    expect(getPreciousMetalMarketPath('SILVER:EUR_ONS')).toEqual({
      path: '/market/silver',
      tab: 'usd_ons',
    });
  });

  it('PLATINUM:KG_TRY → platin sayfası, sekme korunur', () => {
    expect(getPreciousMetalMarketPath('PLATINUM:KG_TRY')).toEqual({
      path: '/market/platinum',
      tab: 'try_kg',
    });
  });

  it('PLATINUM:EUR_ONS → platinde EUR sekmesi korunur (yalnız gümüş düşürür)', () => {
    expect(getPreciousMetalMarketPath('PLATINUM:EUR_ONS')).toEqual({
      path: '/market/platinum',
      tab: 'eur_ons',
    });
  });

  it('PALLADIUM:USD_ONS → paladyum sayfası', () => {
    expect(getPreciousMetalMarketPath('PALLADIUM:USD_ONS')).toEqual({
      path: '/market/palladium',
      tab: 'usd_ons',
    });
  });

  it('küçük harf metal/kategori büyük harfe normalize edilir', () => {
    expect(getPreciousMetalMarketPath('silver:gram_try')).toEqual({
      path: '/market/silver',
      tab: 'try_gram',
    });
  });

  it('etrafındaki boşluklar trim edilir', () => {
    expect(getPreciousMetalMarketPath('  PLATINUM:KG_TRY  ')).toEqual({
      path: '/market/platinum',
      tab: 'try_kg',
    });
  });

  it('iki nokta (:) yoksa null (erken return)', () => {
    expect(getPreciousMetalMarketPath('SILVER')).toBeNull();
  });

  it('bilinmeyen kategori → null', () => {
    expect(getPreciousMetalMarketPath('SILVER:BILINMEYEN')).toBeNull();
  });

  it('bilinen kategori ama bilinmeyen metal (ör. GOLD) → null', () => {
    expect(getPreciousMetalMarketPath('GOLD:GRAM_TRY')).toBeNull();
  });
});

describe('getWatchlistDetailPath — boş/erken return', () => {
  it('symbol boş string → null', () => {
    expect(getWatchlistDetailPath('STOCK', '')).toBeNull();
  });

  it('symbol null → null', () => {
    expect(getWatchlistDetailPath('STOCK', null)).toBeNull();
  });

  it('symbol undefined → null', () => {
    expect(getWatchlistDetailPath('STOCK', undefined)).toBeNull();
  });
});

describe('getWatchlistDetailPath — BIST endeks önceliği (her tipte)', () => {
  it('STOCK + XU100 (sonek yok) → endeks detayına', () => {
    expect(getWatchlistDetailPath('STOCK', 'XU100')).toBe('/market/indices/XU100');
  });

  it('STOCK + XU030.IS (.IS soneki) → endeks detayına (sonek silinir)', () => {
    expect(getWatchlistDetailPath('STOCK', 'XU030.IS')).toBe('/market/indices/XU030');
  });

  it('INDICATOR + XBANK → endeks detayına (assetType önemli değil)', () => {
    expect(getWatchlistDetailPath('INDICATOR', 'XBANK')).toBe('/market/indices/XBANK');
  });

  it('küçük harf endeks kodu büyük harfe normalize edilip tanınır', () => {
    expect(getWatchlistDetailPath('STOCK', 'xu050')).toBe('/market/indices/XU050');
  });

  it('endeks kodu trim edilir', () => {
    expect(getWatchlistDetailPath('STOCK', '  XUTUM  ')).toBe('/market/indices/XUTUM');
  });
});

describe('getWatchlistDetailPath — STOCK', () => {
  it('normal hisse sembolü → hisse detayına', () => {
    expect(getWatchlistDetailPath('STOCK', 'THYAO')).toBe('/market/stocks/THYAO');
  });

  it('boşluk trim edilir', () => {
    expect(getWatchlistDetailPath('STOCK', '  ASELS  ')).toBe('/market/stocks/ASELS');
  });

  it('endeks olmayan .IS sembolü hisse olarak encodlanır', () => {
    // GARAN.IS endeks değil → encodeURIComponent ile nokta korunur
    expect(getWatchlistDetailPath('STOCK', 'GARAN.IS')).toBe('/market/stocks/GARAN.IS');
  });
});

describe('getWatchlistDetailPath — CRYPTO', () => {
  it('bilinen sembol (BTC) → coin id (bitcoin) yoluna', () => {
    expect(getWatchlistDetailPath('CRYPTO', 'BTC')).toBe('/market/crypto/bitcoin');
  });

  it('bilinen sembol küçük harf (eth) → ethereum', () => {
    expect(getWatchlistDetailPath('CRYPTO', 'eth')).toBe('/market/crypto/ethereum');
  });

  it('id içinde sayı/çizgi olan sembol (AVAX → avalanche-2) encodlanır', () => {
    expect(getWatchlistDetailPath('CRYPTO', 'AVAX')).toBe('/market/crypto/avalanche-2');
  });

  it('bilinmeyen sembol → genel kripto liste sayfasına düşer', () => {
    expect(getWatchlistDetailPath('CRYPTO', 'BILINMEYENCOIN')).toBe('/market/crypto');
  });

  it('boşluklu bilinen sembol trim edilip eşlenir', () => {
    expect(getWatchlistDetailPath('CRYPTO', '  XRP  ')).toBe('/market/crypto/ripple');
  });
});

describe('getWatchlistDetailPath — FUND', () => {
  it('fon kodu → TEFAS detayına', () => {
    expect(getWatchlistDetailPath('FUND', 'AFA')).toBe('/market/tefas/AFA');
  });
});

describe('getWatchlistDetailPath — COMMODITY', () => {
  it('BIST kıymetli maden (SILVER:GRAM_TRY) → gümüş sayfası + tab query', () => {
    expect(getWatchlistDetailPath('COMMODITY', 'SILVER:GRAM_TRY')).toBe(
      '/market/silver?tab=try_gram',
    );
  });

  it('PLATINUM:EUR_ONS → platin sayfası + eur_ons query', () => {
    expect(getWatchlistDetailPath('COMMODITY', 'PLATINUM:EUR_ONS')).toBe(
      '/market/platinum?tab=eur_ons',
    );
  });

  it('kıymetli maden olmayan emtia → genel emtia detayına (sembol encodlanır)', () => {
    // İki nokta içermeyen sembol preciousBistSymbolToPath’ten null döner → commodities dalı
    expect(getWatchlistDetailPath('COMMODITY', 'CRUDEOIL')).toBe(
      '/market/commodities/CRUDEOIL',
    );
  });

  it('bilinmeyen kategorili metal (precious null) → emtia detayına, : encodlanır', () => {
    // SILVER:BILINMEYEN → precious null → /market/commodities/ + encodeURIComponent(:)→%3A
    expect(getWatchlistDetailPath('COMMODITY', 'SILVER:BILINMEYEN')).toBe(
      '/market/commodities/SILVER%3ABILINMEYEN',
    );
  });
});

describe('getWatchlistDetailPath — GOLD', () => {
  it('bilinen statik kod (GOLD) → ons sekmesine', () => {
    expect(getWatchlistDetailPath('GOLD', 'GOLD')).toBe('/market/gold?tab=ons');
  });

  it('CEYREK → ceyrek sekmesi', () => {
    expect(getWatchlistDetailPath('GOLD', 'CEYREK')).toBe('/market/gold?tab=ceyrek');
  });

  it('küçük harf kod büyük harfe normalize edilip eşlenir (cumhur → cumhuriyet)', () => {
    expect(getWatchlistDetailPath('GOLD', 'cumhur')).toBe('/market/gold?tab=cumhuriyet');
  });

  it('bilinmeyen altın kodu → genel altın sayfasına düşer', () => {
    expect(getWatchlistDetailPath('GOLD', 'BILINMEYEN')).toBe('/market/gold');
  });
});

describe('getWatchlistDetailPath — FUTURE', () => {
  it('vadeli kontrat sembolü → futures detayına', () => {
    expect(getWatchlistDetailPath('FUTURE', 'F_XU0300425')).toBe(
      '/market/futures/F_XU0300425',
    );
  });
});

describe('getWatchlistDetailPath — FX', () => {
  it('döviz sembolü → fx detayına', () => {
    expect(getWatchlistDetailPath('FX', 'USDTRY')).toBe('/market/fx/USDTRY');
  });
});

describe('getWatchlistDetailPath — BOND', () => {
  it('Eurobond ISIN (TR ile başlamayan, 12 hane) → global tahvil detayına', () => {
    // XS + 9 alfanümerik + 1 rakam = 12 hane, TR ile başlamıyor
    expect(getWatchlistDetailPath('BOND', 'XS0123456789')).toBe(
      '/market/bonds/global/XS0123456789',
    );
  });

  it('US ISIN da Eurobond sayılır (TR değil)', () => {
    expect(getWatchlistDetailPath('BOND', 'US1234567890')).toBe(
      '/market/bonds/global/US1234567890',
    );
  });

  it('TR ile başlayan ISIN (yurt içi DİBS) → EVDS tahvil detayına', () => {
    // TR önekiyle ISIN şeklinde olsa bile Eurobond değil
    expect(getWatchlistDetailPath('BOND', 'TRT123456789')).toBe(
      '/market/bonds/TRT123456789',
    );
  });

  it('ISIN şeklinde olmayan kısa DİBS kodu → yurt içi tahvil detayına', () => {
    // 12 hane regex’ine uymaz → Eurobond değil
    expect(getWatchlistDetailPath('BOND', 'TRT080126T16')).toBe(
      '/market/bonds/TRT080126T16',
    );
  });

  it('küçük harf Eurobond ISIN büyük harfe normalize edilip global sayılır', () => {
    // toUpperCase() ile regex ve TR kontrolü yapılır; yol orijinal sembolle kurulur
    expect(getWatchlistDetailPath('BOND', 'xs0123456789')).toBe(
      '/market/bonds/global/xs0123456789',
    );
  });
});

describe('getWatchlistDetailPath — bilinmeyen tip', () => {
  it('tanımsız assetType → null (default dalı)', () => {
    expect(getWatchlistDetailPath('UNKNOWN_TYPE', 'XYZ')).toBeNull();
  });

  it('assetType undefined → null', () => {
    expect(getWatchlistDetailPath(undefined, 'XYZ')).toBeNull();
  });
});
