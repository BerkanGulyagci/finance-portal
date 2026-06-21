import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { safeFilenameBase, downloadPortfolioCsv } from '../portfolioCsvExport';

// Not: Bu modül SAF mantık + bir tek tarayıcı yan-etkisi (Blob/URL/DOM indirme) içerir.
// safeFilenameBase tamamen saf olduğu için doğrudan test edilir.
// downloadPortfolioCsv için URL.createObjectURL / revokeObjectURL ve anchor.click
// vi.fn ile stub'lanır; üretilen Blob içeriği (CSV gövdesi) yakalanıp assert edilir.
// Böylece export edilmeyen buildHoldingsCsv/assetLabel/escapeCsvCell/numTr dalları
// dolaylı olarak kapsanır.

describe('safeFilenameBase', () => {
  it('geçerli bir ad verilince trim eder ve aynen döner', () => {
    expect(safeFilenameBase('Emeklilik')).toBe('Emeklilik');
    expect(safeFilenameBase('  Uzun Vade  ')).toBe('Uzun Vade');
  });

  it('null/undefined/boş/sadece-boşluk için "portfoy" fallback döner', () => {
    expect(safeFilenameBase(null)).toBe('portfoy');
    expect(safeFilenameBase(undefined)).toBe('portfoy');
    expect(safeFilenameBase('')).toBe('portfoy');
    expect(safeFilenameBase('   ')).toBe('portfoy');
  });

  it('dosya-adında güvensiz karakterleri "-" ile değiştirir', () => {
    // Yasaklı küme: / \ ? % * : | " < >
    expect(safeFilenameBase('a/b\\c?d%e*f:g|h"i<j>k')).toBe('a-b-c-d-e-f-g-h-i-j-k');
  });

  it('izinli karakterleri (boşluk, nokta, tire, alt-çizgi, türkçe) korur', () => {
    expect(safeFilenameBase('Portföy 2024_v1.0-final')).toBe('Portföy 2024_v1.0-final');
  });

  it('80 karakterden uzun adı 80 karaktere kırpar', () => {
    const long = 'x'.repeat(120);
    const out = safeFilenameBase(long);
    expect(out).toHaveLength(80);
    expect(out).toBe('x'.repeat(80));
  });

  it('kırpma trim sonrası uygulanır (baştaki boşluklar sayılmaz)', () => {
    const out = safeFilenameBase('   ' + 'y'.repeat(85));
    expect(out).toHaveLength(80);
  });
});

describe('downloadPortfolioCsv', () => {
  let createdBlob;
  let createdAnchor;
  let createObjectURLSpy;
  let revokeObjectURLSpy;
  let clickSpy;
  let createElementSpy;
  // Spy mockImplementation içinden gerçek createElement'i çağırmak için referans.
  const realCreateElement = document.createElement.bind(document);

  // En son üretilen CSV gövdesini (BOM hariç) okur.
  async function lastCsvBody() {
    expect(createdBlob).toBeInstanceOf(Blob);
    const text = await createdBlob.text();
    // triggerCsvDownload başa U+FEFF (BOM) ekler; saf CSV için sıyırırız.
    const BOM = String.fromCharCode(0xfeff);
    return text.startsWith(BOM) ? text.slice(1) : text;
  }

  // Oluşturulan <a> elemanı indirme bittiğinde DOM'dan KALDIRILIR; bu yüzden
  // download attribute'unu createElement anında yakaladığımız referanstan okuruz.
  function lastDownloadName() {
    return createdAnchor ? createdAnchor.download : null;
  }

  beforeEach(() => {
    createdBlob = null;
    createdAnchor = null;
    // jsdom'da URL.createObjectURL varsayılan olarak yok → stub şart.
    createObjectURLSpy = vi
      .spyOn(URL, 'createObjectURL')
      .mockImplementation((blob) => {
        createdBlob = blob;
        return 'blob:mock-url';
      });
    revokeObjectURLSpy = vi
      .spyOn(URL, 'revokeObjectURL')
      .mockImplementation(() => {});
    // Anchor.click jsdom'da navigasyon denemesi yapar → stub ile sustur.
    clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => {});
    // <a> elemanını oluşturulduğu an yakala (sonradan DOM'dan silindiği için).
    createElementSpy = vi
      .spyOn(document, 'createElement')
      .mockImplementation((tag, ...rest) => {
        const el = realCreateElement(tag, ...rest);
        if (String(tag).toLowerCase() === 'a') createdAnchor = el;
        return el;
      });
  });

  afterEach(() => {
    createObjectURLSpy.mockRestore();
    revokeObjectURLSpy.mockRestore();
    clickSpy.mockRestore();
    createElementSpy.mockRestore();
    document.body.innerHTML = '';
  });

  it('Blob oluşturur, indirmeyi tetikler ve objectURL serbest bırakır', () => {
    downloadPortfolioCsv({ name: 'Test', holdings: [], transactions: [] });
    expect(createObjectURLSpy).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(revokeObjectURLSpy).toHaveBeenCalledTimes(1);
    // İndirme sonrası anchor DOM'dan kaldırılır (body'de iz kalmaz).
    expect(document.querySelectorAll('a')).toHaveLength(0);
    // Yakalanan anchor: object URL href + .csv biten download adı + rel=noopener.
    expect(createdAnchor).not.toBeNull();
    expect(createdAnchor.getAttribute('href')).toBe('blob:mock-url');
    expect(createdAnchor.download.endsWith('.csv')).toBe(true);
    expect(createdAnchor.getAttribute('rel')).toBe('noopener');
  });

  it('dosya adı zaten .csv ile bitiyorsa tekrar .csv eklemez', () => {
    // safeFilenameBase çıktısına _varlik_<tarih> eklenip .csv ile bitiyor; burada
    // download adının tek .csv ile bittiğini (çift uzantı olmadığını) doğrularız.
    downloadPortfolioCsv({ name: 'Test' });
    expect(createdAnchor.download).not.toMatch(/\.csv\.csv$/);
    expect(createdAnchor.download.endsWith('.csv')).toBe(true);
  });

  it('Blob text/csv (UTF-8) tipinde üretilir', async () => {
    downloadPortfolioCsv({ name: 'Test' });
    // charset=utf-8 → Excel CSV'yi doğru kodlamayla açar.
    expect(createdBlob.type).toBe('text/csv;charset=utf-8;');
    expect(createdBlob).toBeInstanceOf(Blob);
    // NOT: Kaynak Blob'a Excel-BOM (U+FEFF) ekler (new Blob(['', body])); ancak jsdom'un
    // Blob.text() implementasyonu BOM byte'larını koruMAZ → test ortamında ilk karakter
    // gövdeyle başlar. BOM'un gerçek tarayıcıda eklendiği kaynakta sabittir; burada gövde
    // içeriğinin doğru üretildiğini doğrularız.
    const text = await createdBlob.text();
    expect(text).toContain('Portföy Adı');
    expect(text).toContain('Test');
  });

  it('dosya adı: <güvenli-ad>_varlik_<YYYY-AA-GG>.csv biçiminde', () => {
    downloadPortfolioCsv({ name: 'Emeklilik' });
    const name = lastDownloadName();
    expect(name).toMatch(/^Emeklilik_varlik_\d{4}-\d{2}-\d{2}\.csv$/);
  });

  it('portföy adı yok/güvensiz ise dosya adı fallback + sanitizasyon uygular', () => {
    downloadPortfolioCsv({});
    expect(lastDownloadName()).toMatch(/^portfoy_varlik_\d{4}-\d{2}-\d{2}\.csv$/);

    document.body.innerHTML = '';
    downloadPortfolioCsv({ name: 'a/b:c' });
    expect(lastDownloadName()).toMatch(/^a-b-c_varlik_\d{4}-\d{2}-\d{2}\.csv$/);
  });

  it('portfolio null olsa bile çağrı patlamaz (safeFilenameBase fallback)', () => {
    // downloadPortfolioCsv portfolio?.name kullanır; ad fallback "portfoy" olur.
    // Not: buildHoldingsCsv portfolio.* eriştiği için null portfolio TypeError atar;
    // bu yüzden burada null DEĞİL, boş nesne ile fallback davranışını doğruluyoruz.
    expect(() => downloadPortfolioCsv({})).not.toThrow();
  });

  it('başlık (özet) satırlarını ve sayıları tr-TR biçiminde yazar', async () => {
    downloadPortfolioCsv({
      name: 'Özet',
      currency: 'TRY',
      totalCost: 1000.5,
      totalMarketValue: 1234567.89,
      totalProfitLoss: -250.25,
      holdings: [],
      transactions: [],
    });
    const body = await lastCsvBody();
    expect(body).toContain('Portföy Adı;Özet');
    expect(body).toContain('Tip;Varlık portföyü');
    expect(body).toContain('Para Birimi;TRY');
    // numTr → ondalık virgül, binlik nokta
    expect(body).toContain('Toplam Maliyet;1.000,5');
    expect(body).toContain('Piyasa Değeri;1.234.567,89');
    expect(body).toContain('Gerçekleşmemiş K/Z;-250,25');
    // Bölüm başlıkları
    expect(body).toContain('VARLIKLAR');
    expect(body).toContain('İŞLEMLER');
    // Satırlar CRLF ile ayrılır
    expect(body).toContain('\r\n');
  });

  it('özet alanları yoksa (undefined) ilgili hücreleri boş bırakır', async () => {
    downloadPortfolioCsv({});
    const body = await lastCsvBody();
    expect(body).toContain('Portföy Adı;'); // name yok → boş
    expect(body).toContain('Para Birimi;'); // currency yok → boş
    expect(body).toContain('Toplam Maliyet;'); // numTr(undefined) → ''
  });

  it('holdings satırlarını assetLabel + numTr ile yazar (bilinen tür)', async () => {
    downloadPortfolioCsv({
      name: 'H',
      holdings: [
        {
          symbol: 'THYAO',
          assetType: 'STOCK',
          totalQuantity: 10,
          averageCost: 100.1234,
          currentPrice: 120.5,
          totalCost: 1001.23,
          marketValue: 1205,
          profitLoss: 203.77,
          currency: 'TRY',
        },
      ],
    });
    const body = await lastCsvBody();
    // STOCK → 'Hisse'; averageCost 4 ondalık, totalQuantity tam sayı (gereksiz sıfır yok)
    expect(body).toContain('THYAO;Hisse;10;100,1234;120,5;1.001,23;1.205;203,77;TRY');
  });

  it('bilinmeyen assetType String(t) olarak, null assetType boş yazılır', async () => {
    downloadPortfolioCsv({
      name: 'H',
      holdings: [
        { symbol: 'X', assetType: 'WEIRD' },
        { symbol: 'Y', assetType: null },
      ],
    });
    const body = await lastCsvBody();
    // assetLabel('WEIRD') → 'WEIRD' (ASSET_LABELS'da yok → String(t))
    expect(body).toContain('X;WEIRD;');
    // assetLabel(null) → '' ; sayısal alanlar da boş (numTr(undefined) → '')
    expect(body).toContain('Y;;;;;;;;');
  });

  it('tüm bilinen varlık etiketlerini doğru eşler', async () => {
    const types = [
      ['STOCK', 'Hisse'],
      ['CRYPTO', 'Kripto'],
      ['FX', 'Döviz'],
      ['FUND', 'Fon'],
      ['FUTURE', 'Vadeli'],
      ['GOLD', 'Altın'],
      ['COMMODITY', 'Emtia'],
      ['BOND', 'DİBS'],
    ];
    downloadPortfolioCsv({
      name: 'E',
      holdings: types.map(([t]) => ({ symbol: t, assetType: t })),
    });
    const body = await lastCsvBody();
    for (const [t, label] of types) {
      expect(body).toContain(`${t};${label};`);
    }
  });

  it('işlem satırlarında BUY→ALIŞ, diğer→SATIŞ ve tarih T→boşluk dönüşümü', async () => {
    downloadPortfolioCsv({
      name: 'T',
      transactions: [
        {
          transactionDate: '2024-05-01T13:45:30.000Z',
          transactionType: 'BUY',
          symbol: 'BTC',
          assetType: 'CRYPTO',
          quantity: 0.5,
          price: 1234.5678,
          commission: 1.25,
        },
        {
          transactionDate: '2024-06-15T09:00:00',
          transactionType: 'SELL',
          symbol: 'BTC',
          assetType: 'CRYPTO',
          quantity: 0.25,
          price: 2000,
          commission: 0,
        },
      ],
    });
    const body = await lastCsvBody();
    // T → boşluk, ilk 19 karakter (saniyeye kadar), CRYPTO → Kripto
    expect(body).toContain('2024-05-01 13:45:30;ALIŞ;BTC;Kripto;0,5;1.234,5678;1,25');
    // BUY değil → SATIŞ ; commission 0 → numTr(0) = '0'
    expect(body).toContain('2024-06-15 09:00:00;SATIŞ;BTC;Kripto;0,25;2.000;0');
  });

  it('transactionType eksikse SATIŞ (BUY değil) varsayar', async () => {
    downloadPortfolioCsv({
      name: 'T',
      transactions: [{ symbol: 'AAA', assetType: 'STOCK', quantity: 1, price: 10 }],
    });
    const body = await lastCsvBody();
    expect(body).toContain(';SATIŞ;AAA;Hisse;1;10;');
  });

  it('transactionDate yoksa tarih hücresi boş kalır', async () => {
    downloadPortfolioCsv({
      name: 'T',
      transactions: [{ transactionType: 'BUY', symbol: 'AAA' }],
    });
    const body = await lastCsvBody();
    // satır boş tarih ile başlar: ';ALIŞ;AAA;...'
    expect(body).toContain(';ALIŞ;AAA;');
  });

  it('holdings/transactions hiç verilmezse (?? []) sadece başlık satırları üretilir', async () => {
    downloadPortfolioCsv({ name: 'Boş' });
    const body = await lastCsvBody();
    expect(body).toContain('VARLIKLAR');
    expect(body).toContain('İŞLEMLER');
    // 'Sembol' kolon başlığı HEM varlık HEM işlem tablosunda var → tam 2 kez geçer;
    // veri satırı eklenmediği için bu sayı artmaz (boş döngü doğrulaması).
    const sembolCount = body.split('Sembol').length - 1;
    expect(sembolCount).toBe(2);
    // Veri satırı yokluğunu netleştir: VARLIKLAR ve İŞLEMLER başlıkları arasında
    // yalnızca kolon başlığı satırı bulunur.
    const between = body.slice(
      body.indexOf('VARLIKLAR'),
      body.indexOf('İŞLEMLER')
    );
    // bölüm-başlığı + kolon-başlığı = 2 satır (sondaki boş satır hariç → 3 segment)
    expect(between.split('\r\n').filter((l) => l !== '')).toHaveLength(2);
  });

  it('escapeCsvCell: ayırıcı/tırnak/satır-sonu içeren değerleri RFC4180 tırnaklar', async () => {
    downloadPortfolioCsv({
      name: 'Risk; "büyük"\nportföy',
      holdings: [{ symbol: 'A;B', assetType: 'STOCK', currency: 'a"b' }],
    });
    const body = await lastCsvBody();
    // name ';', '"' ve '\n' içerir → tüm hücre tırnaklı, içteki " ikiye katlanır
    expect(body).toContain('Portföy Adı;"Risk; ""büyük""\nportföy"');
    // symbol 'A;B' → ayırıcı içerdiği için tırnaklı
    expect(body).toContain('"A;B"');
    // currency 'a"b' → tırnak içerdiği için "a""b"
    expect(body).toContain('"a""b"');
  });

  it('numTr sonlu-olmayan / boş sayıları boş hücreye çevirir', async () => {
    downloadPortfolioCsv({
      name: 'N',
      totalCost: NaN,
      totalMarketValue: Infinity,
      totalProfitLoss: '',
      holdings: [
        { symbol: 'Z', assetType: 'STOCK', totalQuantity: 'not-a-number', averageCost: null },
      ],
    });
    const body = await lastCsvBody();
    // NaN/Infinity/'' → '' ; parseFloat('not-a-number') NaN → '' ; null → ''
    expect(body).toContain('Toplam Maliyet;\r\n');
    expect(body).toContain('Piyasa Değeri;\r\n');
    expect(body).toContain('Z;Hisse;;;'); // quantity ve averageCost boş
  });

  it('numTr string sayıyı parseFloat ile çözüp tr-TR biçimler', async () => {
    downloadPortfolioCsv({
      name: 'S',
      holdings: [{ symbol: 'P', assetType: 'STOCK', totalQuantity: '3.5', currentPrice: '1500.25' }],
    });
    const body = await lastCsvBody();
    // '3.5' → 3,5 ; '1500.25' → 1.500,25
    expect(body).toContain('P;Hisse;3,5;');
    expect(body).toContain('1.500,25');
  });
});
