import { describe, it, expect } from 'vitest';
import {
  mapUiToBackendTransactionType,
  isFutureClosingTrade,
  parseGroupedInput,
  formatGroupedInput,
  currencySymbol,
  isEurobondInstrument,
  isBondAssetType,
  isFutureAssetType,
  sanitizeFutureContractQtyInput,
} from './transactionFormUtils';

describe('mapUiToBackendTransactionType', () => {
  it('FUTURE olmayan varlıkta UI tipini olduğu gibi döner', () => {
    expect(mapUiToBackendTransactionType('BUY', 'LONG', 'STOCK')).toBe('BUY');
    expect(mapUiToBackendTransactionType('SELL', 'SHORT', 'CRYPTO')).toBe('SELL');
  });

  it('FUTURE + LONG: tip değişmez', () => {
    expect(mapUiToBackendTransactionType('BUY', 'LONG', 'FUTURE')).toBe('BUY');
    expect(mapUiToBackendTransactionType('SELL', 'LONG', 'FUTURE')).toBe('SELL');
  });

  it('FUTURE + SHORT: BUY<->SELL ters çevrilir', () => {
    expect(mapUiToBackendTransactionType('BUY', 'SHORT', 'FUTURE')).toBe('SELL');
    expect(mapUiToBackendTransactionType('SELL', 'SHORT', 'FUTURE')).toBe('BUY');
  });

  it('direction null ise LONG varsayılır (ters çevirme yok)', () => {
    expect(mapUiToBackendTransactionType('BUY', null, 'FUTURE')).toBe('BUY');
  });

  it('assetType küçük harf de olsa FUTURE tanınır', () => {
    expect(mapUiToBackendTransactionType('BUY', 'short', 'future')).toBe('SELL');
  });
});

describe('isFutureClosingTrade', () => {
  it('Satış + LONG → kapatma (true)', () => {
    expect(isFutureClosingTrade('SELL', 'LONG')).toBe(true);
  });

  it('Alış + SHORT → kapatma (true)', () => {
    expect(isFutureClosingTrade('BUY', 'SHORT')).toBe(true);
  });

  it('Alış + LONG → açma (false)', () => {
    expect(isFutureClosingTrade('BUY', 'LONG')).toBe(false);
  });

  it('Satış + SHORT → açma (false)', () => {
    expect(isFutureClosingTrade('SELL', 'SHORT')).toBe(false);
  });

  it('direction null ise LONG varsayılır', () => {
    expect(isFutureClosingTrade('SELL', null)).toBe(true);
    expect(isFutureClosingTrade('BUY', null)).toBe(false);
  });
});

describe('formatGroupedInput', () => {
  it('null/boş → boş string', () => {
    expect(formatGroupedInput(null)).toBe('');
    expect(formatGroupedInput('')).toBe('');
  });

  it('tam sayıya binlik nokta ekler', () => {
    expect(formatGroupedInput('1234')).toBe('1.234');
    expect(formatGroupedInput('1000000')).toBe('1.000.000');
  });

  it('ondalık virgülle gösterir', () => {
    expect(formatGroupedInput('3285.5')).toBe('3.285,5');
  });

  it('yazım sürerken sondaki noktayı korur (virgüle çevirir)', () => {
    expect(formatGroupedInput('3285.')).toBe('3.285,');
  });

  it('negatif işareti korur', () => {
    expect(formatGroupedInput('-1234.5')).toBe('-1.234,5');
  });

  it('tam kısım boşken ondalık varsa 0, prefiks ekler', () => {
    expect(formatGroupedInput('.5')).toBe('0,5');
  });
});

describe('parseGroupedInput', () => {
  it('null → boş string, boş → boş', () => {
    expect(parseGroupedInput(null)).toBe('');
    expect(parseGroupedInput('')).toBe('');
    expect(parseGroupedInput('   ')).toBe('');
  });

  it('gruplu görünümü ham sayıya çevirir', () => {
    expect(parseGroupedInput('1.234')).toBe('1234');
    expect(parseGroupedInput('3.285,5')).toBe('3285.5');
    expect(parseGroupedInput('1.000.000')).toBe('1000000');
  });

  it('negatif işareti korur', () => {
    expect(parseGroupedInput('-1.234,5')).toBe('-1234.5');
  });

  it('birden fazla ondalık ayracını tek noktaya indirir', () => {
    // virgül→nokta sonrası ilk noktadan sonraki noktalar silinir
    expect(parseGroupedInput('1,2,3')).toBe('1.23');
  });

  it('format→parse round-trip ham değeri korur', () => {
    expect(parseGroupedInput(formatGroupedInput('3285.5'))).toBe('3285.5');
  });
});

describe('currencySymbol', () => {
  it('bilinen para birimleri için sembol döner', () => {
    expect(currencySymbol('TRY')).toBe('₺');
    expect(currencySymbol('USD')).toBe('$');
    expect(currencySymbol('EUR')).toBe('€');
    expect(currencySymbol('GBP')).toBe('£');
  });

  it('küçük harf de tanınır', () => {
    expect(currencySymbol('usd')).toBe('$');
  });

  it('bilinmeyen kod kendisini döner', () => {
    expect(currencySymbol('JPY')).toBe('JPY');
  });

  it('null/boş → boş string', () => {
    expect(currencySymbol(null)).toBe('');
    expect(currencySymbol('')).toBe('');
  });
});

describe('isBondAssetType / isFutureAssetType', () => {
  it('isBondAssetType BOND için true (case-insensitive)', () => {
    expect(isBondAssetType('BOND')).toBe(true);
    expect(isBondAssetType('bond')).toBe(true);
    expect(isBondAssetType('STOCK')).toBe(false);
    expect(isBondAssetType(null)).toBe(false);
  });

  it('isFutureAssetType FUTURE için true (case-insensitive)', () => {
    expect(isFutureAssetType('FUTURE')).toBe(true);
    expect(isFutureAssetType('future')).toBe(true);
    expect(isFutureAssetType('BOND')).toBe(false);
    expect(isFutureAssetType(undefined)).toBe(false);
  });
});

describe('isEurobondInstrument', () => {
  it('null/BOND olmayan için false', () => {
    expect(isEurobondInstrument(null)).toBe(false);
    expect(isEurobondInstrument({ assetType: 'STOCK', symbol: 'US1234567890' })).toBe(false);
  });

  it('açık işaret (subType/isEurobond) varsa true', () => {
    expect(isEurobondInstrument({ assetType: 'BOND', subType: 'EUROBOND' })).toBe(true);
    expect(isEurobondInstrument({ assetType: 'BOND', isEurobond: true })).toBe(true);
  });

  it('ISIN sezgisi: TR ile başlamayan tam ISIN → eurobond', () => {
    expect(isEurobondInstrument({ assetType: 'BOND', symbol: 'US900123AL40' })).toBe(true);
    expect(isEurobondInstrument({ assetType: 'BOND', symbol: 'XS1629778643' })).toBe(true);
  });

  it('TR ile başlayan ISIN (yurt içi DİBS) → false', () => {
    expect(isEurobondInstrument({ assetType: 'BOND', symbol: 'TRT123456789' })).toBe(false);
  });

  it('ISIN biçiminde olmayan tahvil sembolü → false', () => {
    expect(isEurobondInstrument({ assetType: 'BOND', symbol: 'SOMEBOND' })).toBe(false);
    expect(isEurobondInstrument({ assetType: 'BOND' })).toBe(false);
  });
});

describe('sanitizeFutureContractQtyInput', () => {
  it('null/boş → boş string', () => {
    expect(sanitizeFutureContractQtyInput(null)).toBe('');
    expect(sanitizeFutureContractQtyInput('')).toBe('');
    expect(sanitizeFutureContractQtyInput(undefined)).toBe('');
  });

  it('ondalık kısmı atar, tam sayı kalır', () => {
    expect(sanitizeFutureContractQtyInput('12.7')).toBe('12');
    expect(sanitizeFutureContractQtyInput('12,7')).toBe('12');
  });

  it('sayı olmayan karakterleri temizler', () => {
    expect(sanitizeFutureContractQtyInput('1a2b3')).toBe('123');
  });

  it('baştaki sıfırları parseInt ile normalize eder', () => {
    expect(sanitizeFutureContractQtyInput('007')).toBe('7');
  });

  it('sadece ondalık/sayısız girdi → boş', () => {
    expect(sanitizeFutureContractQtyInput('.5')).toBe('');
    expect(sanitizeFutureContractQtyInput('abc')).toBe('');
  });
});
