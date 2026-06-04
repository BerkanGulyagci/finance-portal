import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  WATCHLIST_CSV_HEADERS,
  watchlistItemToCsvValues,
  buildWatchlistCsvContent,
  downloadWatchlistCsv,
} from '../exportWatchlistCsv';

// Not: Bu dosya SAF mantık (string üretimi) + tek bir yan-etkili indirme fonksiyonu içerir.
// fmtNum, toLocaleString('tr-TR') kullanır; test ortamı tam ICU içerdiği için
// gerçek TR formatlama (binlik=nokta, ondalık=virgül) baz alınarak assert edildi.
// downloadWatchlistCsv için Blob/URL/document yan-etkileri vi.fn ile stub'landı,
// saf kısım (dosya adı damgası + CSV gövdesi) doğrulandı.

describe('WATCHLIST_CSV_HEADERS', () => {
  it('27 kolonluk başlık dizisidir', () => {
    expect(Array.isArray(WATCHLIST_CSV_HEADERS)).toBe(true);
    expect(WATCHLIST_CSV_HEADERS).toHaveLength(27);
  });

  it('ilk ve son başlıklar ile bilinen anahtar kolonları doğru sıradadır', () => {
    expect(WATCHLIST_CSV_HEADERS[0]).toBe('Kategori');
    expect(WATCHLIST_CSV_HEADERS[1]).toBe('Sembol');
    expect(WATCHLIST_CSV_HEADERS[2]).toBe('Ad');
    expect(WATCHLIST_CSV_HEADERS[3]).toBe('Tür');
    expect(WATCHLIST_CSV_HEADERS[26]).toBe('Eklenme Tarihi');
    // İndeks haritasının doğruluğunu sabitleyen birkaç kritik kolon
    expect(WATCHLIST_CSV_HEADERS[5]).toBe('Alış');
    expect(WATCHLIST_CSV_HEADERS[6]).toBe('Satış');
    expect(WATCHLIST_CSV_HEADERS[19]).toBe('Risk');
    expect(WATCHLIST_CSV_HEADERS[20]).toBe('Değer');
    expect(WATCHLIST_CSV_HEADERS[23]).toBe('Kalan Gün');
    expect(WATCHLIST_CSV_HEADERS[24]).toBe('Kupon %');
    expect(WATCHLIST_CSV_HEADERS[25]).toBe('Not');
  });

  it('benzersiz başlık değerlerine sahiptir (yinelenen yok)', () => {
    expect(new Set(WATCHLIST_CSV_HEADERS).size).toBe(WATCHLIST_CSV_HEADERS.length);
  });
});

describe('watchlistItemToCsvValues — ortak alanlar', () => {
  it('her zaman 27 öğelik bir dizi döner', () => {
    expect(watchlistItemToCsvValues({ assetType: 'STOCK', symbol: 'AAA' })).toHaveLength(27);
    // bilinmeyen tür de aynı uzunlukta döner (default dal)
    expect(watchlistItemToCsvValues({ assetType: 'XYZ', symbol: 'AAA' })).toHaveLength(27);
  });

  it('kategori etiketini CATEGORY_LABEL üzerinden çevirir, bilinmeyende boş bırakır', () => {
    expect(watchlistItemToCsvValues({ assetType: 'STOCK', symbol: 'X' })[0]).toBe('Hisse');
    expect(watchlistItemToCsvValues({ assetType: 'CRYPTO', symbol: 'X' })[0]).toBe('Kripto');
    expect(watchlistItemToCsvValues({ assetType: 'BOND', symbol: 'X' })[0]).toBe('DİBS');
    expect(watchlistItemToCsvValues({ assetType: 'FX', symbol: 'X' })[0]).toBe('Döviz');
    // bilinmeyen tür → '' (?? '')
    expect(watchlistItemToCsvValues({ assetType: 'NOPE', symbol: 'X' })[0]).toBe('');
  });

  it('symbol null/undefined ise boş string yazar', () => {
    expect(watchlistItemToCsvValues({ assetType: 'STOCK', symbol: null })[1]).toBe('');
    expect(watchlistItemToCsvValues({ assetType: 'STOCK' })[1]).toBe('');
  });

  it('notes ve addedAt alanlarını 25/26 indekslerine yazar; yoksa boş', () => {
    const row = watchlistItemToCsvValues({
      assetType: 'STOCK',
      symbol: 'X',
      notes: 'takip',
      addedAt: '2026-01-02T10:30:45.123',
    });
    expect(row[25]).toBe('takip');
    // fmtAddedAt: T→boşluk, ilk 16 karakter
    expect(row[26]).toBe('2026-01-02 10:30');

    const bare = watchlistItemToCsvValues({ assetType: 'STOCK', symbol: 'X' });
    expect(bare[25]).toBe('');
    expect(bare[26]).toBe('');
  });

  it('addedAt falsy (boş string) ise tarih kolonu boş kalır', () => {
    const row = watchlistItemToCsvValues({ assetType: 'STOCK', symbol: 'X', addedAt: '' });
    expect(row[26]).toBe('');
  });

  it('Tür kolonu yalnızca FUND + dolu fundType için yazılır', () => {
    expect(
      watchlistItemToCsvValues({ assetType: 'FUND', symbol: 'X', fundType: '  Hisse Fonu  ' })[3],
    ).toBe('Hisse Fonu'); // trim uygulanır
    // fundType boş/whitespace → ''
    expect(watchlistItemToCsvValues({ assetType: 'FUND', symbol: 'X', fundType: '   ' })[3]).toBe('');
    // FUND olmayan türde fundType olsa bile yazılmaz
    expect(
      watchlistItemToCsvValues({ assetType: 'STOCK', symbol: 'X', fundType: 'Hisse Fonu' })[3],
    ).toBe('');
  });
});

describe('watchlistItemToCsvValues — başlık (Ad) çözümleme', () => {
  it('COMMODITY: önce PRECIOUS_NAMES, sonra COMMODITY_NAMES, sonra sembol', () => {
    expect(
      watchlistItemToCsvValues({ assetType: 'COMMODITY', symbol: 'SILVER:GRAM_TRY' })[2],
    ).toBe('Gram Gümüş (₺)');
    expect(watchlistItemToCsvValues({ assetType: 'COMMODITY', symbol: 'CL=F' })[2]).toBe(
      'WTI Ham Petrol',
    );
    // her iki haritada da yoksa sembolün kendisi
    expect(watchlistItemToCsvValues({ assetType: 'COMMODITY', symbol: 'UNKNOWN=F' })[2]).toBe(
      'UNKNOWN=F',
    );
  });

  it('GOLD: GOLD_NAMES eşleşmesi, yoksa sembol', () => {
    expect(watchlistItemToCsvValues({ assetType: 'GOLD', symbol: 'CEYREK' })[2]).toBe('Çeyrek Altın');
    expect(watchlistItemToCsvValues({ assetType: 'GOLD', symbol: 'ZZZ' })[2]).toBe('ZZZ');
  });

  it('FUND: fundName trimlenip kullanılır, boş/whitespace ise sembole düşer', () => {
    expect(
      watchlistItemToCsvValues({ assetType: 'FUND', symbol: 'TTE', fundName: '  Tech Fonu ' })[2],
    ).toBe('Tech Fonu');
    expect(watchlistItemToCsvValues({ assetType: 'FUND', symbol: 'TTE', fundName: '   ' })[2]).toBe(
      'TTE',
    );
    expect(watchlistItemToCsvValues({ assetType: 'FUND', symbol: 'TTE' })[2]).toBe('TTE');
  });

  it('diğer türlerde (STOCK/CRYPTO/FX/BOND) Ad = sembol', () => {
    expect(watchlistItemToCsvValues({ assetType: 'STOCK', symbol: 'THYAO' })[2]).toBe('THYAO');
    expect(watchlistItemToCsvValues({ assetType: 'FX', symbol: 'USDTRY' })[2]).toBe('USDTRY');
  });

  it('symbol yoksa Ad boş string olur', () => {
    expect(watchlistItemToCsvValues({ assetType: 'STOCK' })[2]).toBe('');
  });
});

describe('watchlistItemToCsvValues — STOCK/CRYPTO/GOLD/COMMODITY/FUTURE dalı', () => {
  const base = {
    assetType: 'STOCK',
    symbol: 'AAA',
    lastPrice: 1234.5678,
    startPrice: 1000.25,
    open: 1100,
    high: 1200.5,
    low: 1050,
    change: 12.3456,
    changePercent: 1.5,
    volume: 1500000,
  };

  it('fiyat/değişim kolonlarını doğru indekslere TR formatıyla yazar', () => {
    const row = watchlistItemToCsvValues(base);
    expect(row[4]).toBe('1.234,5678'); // lastPrice, 6 ondalık
    expect(row[7]).toBe('1.000,25'); // startPrice, 4 ondalık
    expect(row[8]).toBe('1.100'); // open
    expect(row[9]).toBe('1.200,5'); // high
    expect(row[10]).toBe('1.050'); // low
    expect(row[11]).toBe('12,3456'); // change, 4 ondalık
    expect(row[12]).toBe('1,5'); // changePercent → Fark %
    expect(row[14]).toBe('1,5'); // changePercent → Günlük Getiri (aynı değer)
  });

  it('volume ham String() ile yazılır (fmtNum değil)', () => {
    expect(watchlistItemToCsvValues(base)[13]).toBe('1500000');
    // string volume de aynen string'e çevrilir
    expect(watchlistItemToCsvValues({ ...base, volume: '2_500' })[13]).toBe('2_500');
  });

  it('volume null/boş ise hacim kolonu boş kalır; 0 ise "0" yazılır', () => {
    expect(watchlistItemToCsvValues({ ...base, volume: null })[13]).toBe('');
    expect(watchlistItemToCsvValues({ ...base, volume: '' })[13]).toBe('');
    expect(watchlistItemToCsvValues({ ...base, volume: undefined })[13]).toBe('');
    // 0 != null && 0 !== '' → String(0)
    expect(watchlistItemToCsvValues({ ...base, volume: 0 })[13]).toBe('0');
  });

  it('eksik sayısal alanlar (undefined/null/NaN) boş string üretir; 0 sayı kalır', () => {
    const row = watchlistItemToCsvValues({ assetType: 'CRYPTO', symbol: 'BTC' });
    expect(row[4]).toBe(''); // lastPrice yok
    expect(row[7]).toBe('');
    expect(row[11]).toBe('');
    // 0 sonlu → "0" (boş değil)
    expect(watchlistItemToCsvValues({ assetType: 'CRYPTO', symbol: 'BTC', lastPrice: 0 })[4]).toBe(
      '0',
    );
    // NaN → '' (Number.isFinite false)
    expect(
      watchlistItemToCsvValues({ assetType: 'CRYPTO', symbol: 'BTC', lastPrice: NaN })[4],
    ).toBe('');
  });

  it('string sayısal değerleri parseFloat ile çözer', () => {
    // "1234.5" JS noktası → 1234.5 → TR "1.234,5"
    const row = watchlistItemToCsvValues({ assetType: 'GOLD', symbol: 'GRAM', lastPrice: '1234.5' });
    expect(row[4]).toBe('1.234,5');
  });

  it('negatif değişim değerini işaretiyle formatlar', () => {
    const row = watchlistItemToCsvValues({ ...base, change: -50.5, changePercent: -2.25 });
    expect(row[11]).toBe('-50,5');
    expect(row[12]).toBe('-2,25');
  });

  it('FUTURE türü de aynı (STOCK) dalı kullanır', () => {
    const row = watchlistItemToCsvValues({ assetType: 'FUTURE', symbol: 'XU030', lastPrice: 99.5 });
    expect(row[0]).toBe('Vadeli');
    expect(row[4]).toBe('99,5');
  });
});

describe('watchlistItemToCsvValues — FUND dalı', () => {
  const fund = {
    assetType: 'FUND',
    symbol: 'TTE',
    fundName: 'Teknoloji Fonu',
    fundType: 'Hisse',
    lastPrice: 12.345678,
    startPrice: 10.5,
    changePercent: 0.75,
    fundReturnOneMonth: 3.2,
    fundReturnThreeMonths: 8.1,
    fundReturnYtd: 15.4,
    fundReturnOneYear: 42.6,
    fundRiskLevel: 4,
  };

  it('fon getiri ve risk kolonlarını doğru indekslere yazar', () => {
    const row = watchlistItemToCsvValues(fund);
    expect(row[4]).toBe('12,345678'); // lastPrice 6 ondalık
    expect(row[7]).toBe('10,5'); // startPrice
    expect(row[14]).toBe('0,75'); // changePercent → Günlük Getiri
    expect(row[15]).toBe('3,2'); // 1 Ay
    expect(row[16]).toBe('8,1'); // 3 Ay
    expect(row[17]).toBe('15,4'); // YBG
    expect(row[18]).toBe('42,6'); // 1 Yıl
    expect(row[19]).toBe('4'); // risk → String
  });

  it('fundRiskLevel 0 ise "0" yazılır, null ise boş kalır', () => {
    expect(watchlistItemToCsvValues({ ...fund, fundRiskLevel: 0 })[19]).toBe('0');
    expect(watchlistItemToCsvValues({ ...fund, fundRiskLevel: null })[19]).toBe('');
    expect(watchlistItemToCsvValues({ ...fund, fundRiskLevel: undefined })[19]).toBe('');
  });

  it('FUND dalı STOCK-özel kolonları (open/high/low/volume) doldurmaz', () => {
    const row = watchlistItemToCsvValues(fund);
    expect(row[8]).toBe('');
    expect(row[9]).toBe('');
    expect(row[10]).toBe('');
    expect(row[13]).toBe('');
  });
});

describe('watchlistItemToCsvValues — FX dalı', () => {
  it('alış/satış/başlangıç kolonlarını yazar', () => {
    const row = watchlistItemToCsvValues({
      assetType: 'FX',
      symbol: 'USDTRY',
      buy: 32.1234,
      sell: 32.5678,
      startPrice: 30.1,
    });
    expect(row[5]).toBe('32,1234'); // Alış
    expect(row[6]).toBe('32,5678'); // Satış
    expect(row[7]).toBe('30,1'); // Eklendiği Fiyat
    // FX dalı lastPrice/değişim kolonlarını doldurmaz
    expect(row[4]).toBe('');
    expect(row[12]).toBe('');
  });

  it('eksik buy/sell boş string üretir', () => {
    const row = watchlistItemToCsvValues({ assetType: 'FX', symbol: 'EURTRY' });
    expect(row[5]).toBe('');
    expect(row[6]).toBe('');
  });
});

describe('watchlistItemToCsvValues — BOND dalı', () => {
  const bond = {
    assetType: 'BOND',
    symbol: 'TRT1234',
    lastPrice: 98.7654,
    startPrice: 95.5,
    change: -1.25,
    changePercent: -1.3,
    remainingDays: 365,
    couponRate: 12.5,
  };

  it('DİBS-özel kolonları (değer/fark/kalan gün/kupon) doğru indekslere yazar', () => {
    const row = watchlistItemToCsvValues(bond);
    expect(row[20]).toBe('98,7654'); // Değer (lastPrice 4 ondalık)
    expect(row[7]).toBe('95,5'); // Eklendiği Fiyat
    expect(row[21]).toBe('-1,25'); // Günlük Fark
    expect(row[22]).toBe('-1,3'); // Günlük Fark %
    expect(row[23]).toBe('365'); // Kalan Gün → String
    expect(row[24]).toBe('12,5'); // Kupon %
  });

  it('remainingDays 0 ise "0", null ise boş; couponRate yoksa boş', () => {
    expect(watchlistItemToCsvValues({ ...bond, remainingDays: 0 })[23]).toBe('0');
    expect(watchlistItemToCsvValues({ ...bond, remainingDays: null })[23]).toBe('');
    expect(watchlistItemToCsvValues({ ...bond, couponRate: undefined })[24]).toBe('');
  });

  it('BOND dalı row[4] (Son Fiyat) kolonunu kullanmaz, Değer kolonunu kullanır', () => {
    expect(watchlistItemToCsvValues(bond)[4]).toBe('');
  });
});

describe('watchlistItemToCsvValues — bilinmeyen tür (default)', () => {
  it('yalnızca ortak alanları yazar, sayısal kolonları boş bırakır', () => {
    const row = watchlistItemToCsvValues({
      assetType: 'WHAT',
      symbol: 'ZZZ',
      lastPrice: 100,
      notes: 'n',
    });
    expect(row[0]).toBe(''); // bilinmeyen kategori
    expect(row[1]).toBe('ZZZ');
    expect(row[2]).toBe('ZZZ');
    expect(row[25]).toBe('n');
    // hiçbir sayısal kolon doldurulmaz
    expect(row[4]).toBe('');
    expect(row[20]).toBe('');
  });
});

describe('buildWatchlistCsvContent', () => {
  it('boş/undefined/null liste için yalnızca başlık satırını döner', () => {
    const headerLine = WATCHLIST_CSV_HEADERS.map((h) => `"${h}"`).join(',');
    expect(buildWatchlistCsvContent([])).toBe(headerLine);
    expect(buildWatchlistCsvContent(undefined)).toBe(headerLine);
    expect(buildWatchlistCsvContent(null)).toBe(headerLine);
  });

  it('satırları CRLF ile birleştirir ve her alanı çift tırnakla sarar', () => {
    const out = buildWatchlistCsvContent([{ assetType: 'STOCK', symbol: 'AAA', lastPrice: 10 }]);
    const lines = out.split('\r\n');
    expect(lines).toHaveLength(2); // başlık + 1 kalem
    expect(lines[0].startsWith('"Kategori","Sembol"')).toBe(true);
    // her alan tırnak içinde: satır "..." ile başlar/biter
    expect(lines[1].startsWith('"Hisse","AAA"')).toBe(true);
    expect(lines[1].endsWith('""')).toBe(true); // son kolon (boş tarih) → ""
  });

  it('null/undefined alanları boş tırnak çifti olarak yazar', () => {
    const out = buildWatchlistCsvContent([{ assetType: 'STOCK', symbol: null }]);
    const dataLine = out.split('\r\n')[1];
    // symbol null → "" (quoteField null/undefined dalı)
    expect(dataLine.split(',')[1]).toBe('""');
  });

  it('içerikteki çift tırnakları ikiye katlayarak kaçışlar (CSV escaping)', () => {
    const out = buildWatchlistCsvContent([
      { assetType: 'STOCK', symbol: 'X', notes: 'a "quoted" note' },
    ]);
    const dataLine = out.split('\r\n')[1];
    // notes kolonu (indeks 25) içinde " → ""
    expect(dataLine).toContain('"a ""quoted"" note"');
  });

  it('birden çok kalemi sırayla yazar', () => {
    const out = buildWatchlistCsvContent([
      { assetType: 'STOCK', symbol: 'AAA' },
      { assetType: 'FX', symbol: 'USDTRY', buy: 32.1 },
    ]);
    const lines = out.split('\r\n');
    expect(lines).toHaveLength(3);
    expect(lines[1]).toContain('"AAA"');
    expect(lines[2]).toContain('"USDTRY"');
  });
});

describe('downloadWatchlistCsv (yan-etki stub ile)', () => {
  let createdAnchor;
  let appendSpy;
  let removeSpy;

  beforeEach(() => {
    vi.restoreAllMocks();
    createdAnchor = {
      href: '',
      download: '',
      rel: '',
      click: vi.fn(),
    };
    // Blob & URL yan-etkilerini stub'la
    vi.stubGlobal(
      'Blob',
      vi.fn(function Blob(parts, opts) {
        this.parts = parts;
        this.opts = opts;
      }),
    );
    if (!globalThis.URL) globalThis.URL = {};
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    vi.spyOn(document, 'createElement').mockReturnValue(createdAnchor);
    appendSpy = vi.spyOn(document.body, 'appendChild').mockImplementation(() => {});
    removeSpy = vi.spyOn(document.body, 'removeChild').mockImplementation(() => {});
  });

  it('CSV gövdesini üretip indirme akışını tetikler (click + append + remove)', () => {
    downloadWatchlistCsv('Benim Listem', [{ assetType: 'STOCK', symbol: 'AAA', lastPrice: 10 }]);
    expect(createdAnchor.click).toHaveBeenCalledTimes(1);
    expect(appendSpy).toHaveBeenCalledTimes(1);
    expect(removeSpy).toHaveBeenCalledTimes(1);
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
    expect(createdAnchor.href).toBe('blob:mock-url');
    expect(createdAnchor.rel).toBe('noopener');
  });

  it('dosya adı: watchlist-<temiz-ad>-<YYYY-AA-GG>.csv biçimindedir ve .csv ile biter', () => {
    downloadWatchlistCsv('Benim Listem', []);
    // boşluk → alt çizgi
    expect(createdAnchor.download).toMatch(/^watchlist-Benim_Listem-\d{4}-\d{2}-\d{2}\.csv$/);
    expect(createdAnchor.download.endsWith('.csv')).toBe(true);
  });

  it('geçersiz dosya adı karakterlerini tireye çevirir', () => {
    downloadWatchlistCsv('a/b:c*d?e', []);
    // / \ ? % * : | " < > → '-' ; sonra .csv eklenir
    expect(createdAnchor.download).toMatch(/^watchlist-a-b-c-d-e-\d{4}-\d{2}-\d{2}\.csv$/);
  });

  it('boş/whitespace portföy adı için "watchlist" varsayılanını kullanır', () => {
    downloadWatchlistCsv('   ', []);
    expect(createdAnchor.download).toMatch(/^watchlist-watchlist-\d{4}-\d{2}-\d{2}\.csv$/);
  });

  it('null portföy adı için de "watchlist" varsayılanına düşer', () => {
    downloadWatchlistCsv(null, []);
    expect(createdAnchor.download).toMatch(/^watchlist-watchlist-\d{4}-\d{2}-\d{2}\.csv$/);
  });

  it('Blob içeriği UTF-8 BOM (\\uFEFF) + CSV gövdesi ile oluşturulur', () => {
    downloadWatchlistCsv('L', [{ assetType: 'STOCK', symbol: 'AAA' }]);
    const blobCall = Blob.mock.instances[0];
    expect(blobCall.parts[0]).toBe('﻿');
    expect(typeof blobCall.parts[1]).toBe('string');
    expect(blobCall.parts[1]).toContain('"Kategori"'); // gövde = buildWatchlistCsvContent çıktısı
    expect(blobCall.parts[1]).toContain('"AAA"');
    expect(blobCall.opts.type).toContain('text/csv');
  });
});
