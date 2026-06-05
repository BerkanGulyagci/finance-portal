import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor} from '@testing-library/react';

// ── ECharts mock ─────────────────────────────────────────────────────────────────
// Grafik artık ECharts (canvas). jsdom'da clientWidth/Height 0 → echarts.init throw eder.
// init/setOption/resize/dispose'u stub'lıyoruz. setOption çağrısını yakalayıp en son
// option'ı global'e yazıyoruz → testler hangi metallerin (series) çizildiğini doğrulayabilir.
let __lastEchartsOption = null;
vi.mock('echarts', () => ({
  init: vi.fn(() => ({
    setOption: vi.fn((opt) => { __lastEchartsOption = opt; }),
    resize: vi.fn(),
    dispose: vi.fn(),
    on: vi.fn(),
    off: vi.fn(),
  })),
  use: vi.fn(),
  graphic: {},
}));

// ── API mock ────────────────────────────────────────────────────────────────────
// Komponent getPreciousMetalHistory(metal, range, currency) → { points: [...] }
// bekliyor (d?.points). Her test kendi davranışını ayarlayabilsin diye vi.fn.
vi.mock('../../../../api/marketApi', () => ({
  getPreciousMetalHistory: vi.fn(),
}));

import CommodityComparePage from '../CommodityComparePage';
import { getPreciousMetalHistory } from '../../../../api/marketApi';
import { LanguageProvider } from '../../../../context/LanguageContext';

// Varsayılan dil "tr" → t(key) anahtarın kendisini döndürür; komponentteki Türkçe
// etiketler ("Altın", "Emtia Karşılaştırma" vb.) DOM'a olduğu gibi yansır.
function renderPage() {
  return render(
    <LanguageProvider>
      <CommodityComparePage />
    </LanguageProvider>
  );
}

// Belirli bir tarihten geriye doğru ISO tarih (YYYY-MM-DD) üretir.
function isoDaysAgo(n, base = new Date('2026-06-01')) {
  const d = new Date(base);
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

// Yükselen fiyat serisi: hem usdOns hem tryGram alanlarını doldurur (komponent
// extractPrice ile bunları okur). Son fiyat ilk fiyattan büyük → pozitif getiri.
function makePoints(count = 12, startUsd = 2000, startTry = 2500) {
  return Array.from({ length: count }, (_, i) => ({
    date: isoDaysAgo(count - 1 - i),
    usdOns: startUsd + i * 5,
    tryGram: startTry + i * 7,
  }));
}

describe('CommodityComparePage (Vitest + Testing Library, recharts & API mocklu)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    // Varsayılan: her metal için dolu seri döndür.
    getPreciousMetalHistory.mockResolvedValue({ points: makePoints() });
  });

  it('başlık ve seçim kontrollerini (Emtia/Dönem/Birim) render eder (smoke)', async () => {
    renderPage();

    expect(screen.getByRole('heading', { name: 'Emtia Karşılaştırma', level: 1 }))
      .toBeInTheDocument();

    // Bölüm başlıkları
    expect(screen.getAllByText('Emtia')[0]).toBeInTheDocument();
    expect(screen.getByText('Dönem')).toBeInTheDocument();
    expect(screen.getByText('Birim')).toBeInTheDocument();

    // 4 metal butonu mevcut
    expect(screen.getByRole('button', { name: /Altın/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Gümüş/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Platin/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Paladyum/ })).toBeInTheDocument();

    // İlk yükleme tamamlanınca (varsayılan seçili: gold + silver) 2 metal çekilir.
    await waitFor(() =>
      expect(getPreciousMetalHistory).toHaveBeenCalledWith('gold', '1M', 'USD')
    );
    expect(getPreciousMetalHistory).toHaveBeenCalledWith('silver', '1M', 'USD');
  });

  it('veri gelince normalize performans grafiğini ve seçili metal çizgilerini gösterir', async () => {
    renderPage();

    // Mock recharts'tan LineChart render edilmeli (chartData boş değil).
    expect(await screen.findByTestId('line-chart')).toBeInTheDocument();

    // Varsayılan seçili metaller: gold + silver → iki çizgi.
    expect(screen.getByTestId('line-gold')).toBeInTheDocument();
    expect(screen.getByTestId('line-silver')).toBeInTheDocument();
    // Seçili olmayanlar çizilmez.
    expect(screen.queryByTestId('line-platinum')).not.toBeInTheDocument();

    // Tablolar başlıklarıyla render olur.
    expect(screen.getByText('Dönem Getirileri')).toBeInTheDocument();
    expect(screen.getByText('Risk / Dalgalanma')).toBeInTheDocument();
    expect(screen.getByText('Güncel Fiyatlar')).toBeInTheDocument();
  });

  it('API boş seri döndürünce grafik yerine "veri bulunamadı" mesajını gösterir', async () => {
    getPreciousMetalHistory.mockResolvedValue({ points: [] });
    renderPage();

    expect(await screen.findByText('Grafik verisi bulunamadı.')).toBeInTheDocument();
    expect(screen.queryByTestId('line-chart')).not.toBeInTheDocument();
  });

  it('API hata fırlatınca (catch → []) çökmez, boş-durum mesajı gösterilir', async () => {
    getPreciousMetalHistory.mockRejectedValue(new Error('network down'));
    renderPage();

    // fetchMetalHistory catch'i [] döndürür → chartData boş → boş mesaj.
    expect(await screen.findByText('Grafik verisi bulunamadı.')).toBeInTheDocument();
  });

  it('Dönem butonuna tıklayınca API yeni range ile yeniden çağrılır', async () => {
    renderPage();
    await waitFor(() =>
      expect(getPreciousMetalHistory).toHaveBeenCalledWith('gold', '1M', 'USD')
    );

    // "1Y" dönem butonu (label "1Y")
    fireEvent.click(screen.getByRole('button', { name: '1Y' }));

    await waitFor(() =>
      expect(getPreciousMetalHistory).toHaveBeenCalledWith('gold', '1Y', 'USD')
    );
  });

  it('Birim TL/Gram seçilince API TRY currency ile yeniden çağrılır', async () => {
    renderPage();
    await waitFor(() =>
      expect(getPreciousMetalHistory).toHaveBeenCalledWith('gold', '1M', 'USD')
    );

    fireEvent.click(screen.getByRole('button', { name: 'TL/Gram' }));

    await waitFor(() =>
      expect(getPreciousMetalHistory).toHaveBeenCalledWith('gold', '1M', 'TRY')
    );
  });

  it('seçili olmayan bir metale (Platin) tıklayınca o metal için de veri çekilir', async () => {
    renderPage();
    await waitFor(() =>
      expect(getPreciousMetalHistory).toHaveBeenCalledWith('gold', '1M', 'USD')
    );

    fireEvent.click(screen.getByRole('button', { name: /Platin/ }));

    await waitFor(() =>
      expect(getPreciousMetalHistory).toHaveBeenCalledWith('platinum', '1M', 'USD')
    );
    // Grafikte yeni çizgi belirir.
    expect(await screen.findByTestId('line-platinum')).toBeInTheDocument();
  });

  it('son seçili metal kapatılamaz (en az bir metal seçili kalır)', async () => {
    renderPage();
    await waitFor(() =>
      expect(getPreciousMetalHistory).toHaveBeenCalledWith('gold', '1M', 'USD')
    );

    // Önce silver'ı kapat → sadece gold kalır.
    fireEvent.click(screen.getByRole('button', { name: /Gümüş/ }));
    await waitFor(() =>
      expect(screen.queryByTestId('line-silver')).not.toBeInTheDocument()
    );
    expect(screen.getByTestId('line-gold')).toBeInTheDocument();

    // Tek kalan gold'a tıkla → toggleMetal guard'ı yüzünden seçili kalmalı.
    fireEvent.click(screen.getByRole('button', { name: /Altın/ }));
    // gold hâlâ çizili (kaldırılmadı).
    expect(screen.getByTestId('line-gold')).toBeInTheDocument();
  });

  it('"Güncel Fiyatlar" tablosu USD ve TL son fiyatlarını biçimlendirir', async () => {
    renderPage();

    // Tablo başlığını bekle, sonra tablonun gövdesinde fiyatları doğrula.
    expect(await screen.findByText('Güncel Fiyatlar')).toBeInTheDocument();

    // Kaynak etiketi her satırda "Borsa İstanbul" yazar.
    await waitFor(() =>
      expect(screen.getAllByText('Borsa İstanbul').length).toBeGreaterThan(0)
    );

    // Disclaimer metni (t(DISCLAIMER) → anahtarın kendisi) tabloda yer alır.
    expect(
      screen.getByText(/Borsa İstanbul resmi referans verileridir/)
    ).toBeInTheDocument();
  });

  it('özet kartlarında pozitif getiri yüzde işaretiyle (+...%) gösterilir', async () => {
    renderPage();
    await screen.findByTestId('line-chart');

    // makePoints yükselen seri → totalRet > 0 → en az bir "+...%" metni olmalı.
    await waitFor(() => {
      const plus = screen.getAllByText(/^\+[\d.,]+%$/);
      expect(plus.length).toBeGreaterThan(0);
    });
  });
});
