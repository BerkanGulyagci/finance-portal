import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { renderCell, Dash } from '../HoldingsTableCells';
import { LanguageProvider, useTranslation } from '../../../../context/LanguageContext';

// HoldingsTableCells, SAF bir sunum katmanıdır: renderCell(key, h, commoditySpots, valuesHidden, t)
// anahtara göre bir tablo hücresi JSX'i üretir. Grafik/ağ/canvas YOK; tek context bağımlılığı
// `name`/`trend` hücrelerindeki <Link> (router) ve <TrendBadge> (LanguageProvider) içindir.
//
// Bu yüzden mock yerine GERÇEK sağlayıcılarla sarmalıyoruz:
//   - MemoryRouter → <Link> render edilebilsin (name hücresi).
//   - LanguageProvider → varsayılan "tr" dilinde t(key) anahtarın KENDİSİNİ döndürür
//     (vars verilince {x} interpolasyonu yapar) → beklenen Türkçe etiketler ekrana basılır.
//
// renderCell `t`'yi parametre olarak aldığından, küçük bir harness component ile context'teki
// gerçek `t`'yi alıp fonksiyona geçiriyoruz (LanguageContext davranışıyla birebir).

/** renderCell çıktısını gerçek `t` ile render eden test harness'ı. */
function Cell({ colKey, holding, commoditySpots = null, valuesHidden = false }) {
  const { t } = useTranslation();
  return <table><tbody><tr><td>{renderCell(colKey, holding, commoditySpots, valuesHidden, t)}</td></tr></tbody></table>;
}

function renderCellUi(props) {
  return render(
    <MemoryRouter>
      <LanguageProvider>
        <Cell {...props} />
      </LanguageProvider>
    </MemoryRouter>
  );
}

// Sık kullanılan örnek holding'ler (alanlar komponentin OKUDUĞU isimlerle birebir).
const stockHolding = {
  symbol: 'THYAO',
  name: 'Türk Hava Yolları',
  assetType: 'STOCK',
  currency: 'TL',
  totalQuantity: 100,
  averageCost: 200,
  currentPrice: 250,
  marketValue: 25000,
  totalCost: 20000,
  profitLoss: 5000,
};

describe('HoldingsTableCells - renderCell', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  describe('Dash (yardımcı sunum component)', () => {
    it('tek başına render edilince "-" basar', () => {
      render(<Dash />);
      expect(screen.getByText('-')).toBeInTheDocument();
    });
  });

  describe('name hücresi', () => {
    it('detay yolu olan varlıkta isim bir <Link> (anchor) olarak render edilir', () => {
      renderCellUi({ colKey: 'name', holding: stockHolding });
      const link = screen.getByRole('link', { name: /Türk Hava Yolları/ });
      expect(link).toBeInTheDocument();
      // STOCK THYAO → /market/stocks/THYAO
      expect(link).toHaveAttribute('href', '/market/stocks/THYAO');
    });

    it('detay yolu olmayan varlıkta (bilinmeyen tip) düz metin span basılır, link OLMAZ', () => {
      renderCellUi({
        colKey: 'name',
        holding: { symbol: 'ZZZ', name: 'Bilinmeyen Varlık', assetType: 'MYSTERY' },
      });
      expect(screen.getByText('Bilinmeyen Varlık')).toBeInTheDocument();
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
    });

    it('kıymetli maden sembolünü (SILVER:GRAM_TRY) "Gram Gümüş" olarak çözer', () => {
      renderCellUi({
        colKey: 'name',
        holding: { symbol: 'SILVER:GRAM_TRY', assetType: 'COMMODITY' },
      });
      // COMMODITY precious → link var; metin resolveHoldingName ile "Gram Gümüş"
      expect(screen.getByText('Gram Gümüş')).toBeInTheDocument();
    });
  });

  describe('symbol hücresi', () => {
    it('sembolü aynen basar', () => {
      renderCellUi({ colKey: 'symbol', holding: stockHolding });
      expect(screen.getByText('THYAO')).toBeInTheDocument();
    });
  });

  describe('assetType hücresi', () => {
    it('STOCK için "Hisse" etiketi basar', () => {
      renderCellUi({ colKey: 'assetType', holding: stockHolding });
      expect(screen.getByText('Hisse')).toBeInTheDocument();
    });

    it('FUTURE SHORT pozisyonda "Vadeli" + "S" rozeti gösterir', () => {
      renderCellUi({
        colKey: 'assetType',
        holding: { assetType: 'FUTURE', viopDirection: 'SHORT', symbol: 'XU030' },
      });
      expect(screen.getByText('Vadeli')).toBeInTheDocument();
      expect(screen.getByText('S')).toBeInTheDocument();
    });

    it('TÜFE-endeksli BOND için "TÜFE" rozeti gösterir', () => {
      renderCellUi({
        colKey: 'assetType',
        holding: { assetType: 'BOND', category: 'INFLATION_LINKED_BOND', symbol: 'TRT1' },
      });
      expect(screen.getByText('DİBS')).toBeInTheDocument();
      expect(screen.getByText('TÜFE')).toBeInTheDocument();
    });
  });

  describe('qty hücresi', () => {
    it('hisse miktarını tr formatında basar', () => {
      renderCellUi({ colKey: 'qty', holding: stockHolding });
      expect(screen.getByText('100')).toBeInTheDocument();
    });

    it('non-gold BOND için "nominal değer" eki gösterir', () => {
      renderCellUi({
        colKey: 'qty',
        holding: { assetType: 'BOND', category: 'ZERO_COUPON_BOND', totalQuantity: 10000, symbol: 'TRT' },
      });
      expect(screen.getByText('nominal değer')).toBeInTheDocument();
    });

    it('valuesHidden=true iken maskelenir (gerçek miktar GÖRÜNMEZ)', () => {
      renderCellUi({ colKey: 'qty', holding: stockHolding, valuesHidden: true });
      expect(screen.queryByText('100')).not.toBeInTheDocument();
      // maske karakterleri (•) basılır
      expect(screen.getByText(/•/)).toBeInTheDocument();
    });
  });

  describe('marketValue hücresi', () => {
    it('piyasa değerini 2 ondalıkla + para birimiyle basar', () => {
      renderCellUi({ colKey: 'marketValue', holding: stockHolding });
      // 25000 → "25.000,00 TL"
      expect(screen.getByText(/25\.000,00\s*TL/)).toBeInTheDocument();
    });

    it('FUTURE satırında "i" bilgi rozeti (VİOP basitleştirme) eklenir', () => {
      renderCellUi({
        colKey: 'marketValue',
        holding: { assetType: 'FUTURE', symbol: 'XU030', marketValue: 1234.5, currency: 'TL' },
      });
      expect(screen.getByLabelText('VIOP bilgi')).toBeInTheDocument();
    });
  });

  describe('unrealizedPnl hücresi (PnlAmt)', () => {
    it('pozitif K/Z "+" ile ve emerald renkte gösterilir', () => {
      const { container } = renderCellUi({ colKey: 'unrealizedPnl', holding: stockHolding });
      // profitLoss=5000 → "+5.000,00 TL"
      const el = screen.getByText(/\+5\.000,00\s*TL/);
      expect(el).toBeInTheDocument();
      expect(container.querySelector('.text-emerald-600')).toBeTruthy();
    });

    it('negatif K/Z rose renkte ve "+" olmadan gösterilir', () => {
      const losing = { ...stockHolding, profitLoss: -1500 };
      const { container } = renderCellUi({ colKey: 'unrealizedPnl', holding: losing });
      expect(screen.getByText(/-1\.500,00\s*TL/)).toBeInTheDocument();
      expect(container.querySelector('.text-rose-600')).toBeTruthy();
    });

    it('değer hesaplanamazsa Dash ("-") basar', () => {
      // profitLoss yok, marketValue/totalCost de yok → unrealizedGainLoss null
      renderCellUi({
        colKey: 'unrealizedPnl',
        holding: { symbol: 'X', assetType: 'STOCK', currency: 'TL' },
      });
      expect(screen.getByText('-')).toBeInTheDocument();
    });

    it('valuesHidden iken para maskesi basar', () => {
      renderCellUi({ colKey: 'unrealizedPnl', holding: stockHolding, valuesHidden: true });
      expect(screen.queryByText(/5\.000/)).not.toBeInTheDocument();
      expect(screen.getByText(/•/)).toBeInTheDocument();
    });
  });

  describe('unrealizedPct hücresi (PnlPct)', () => {
    it('yüzdeyi "+" ve % ekiyle gösterir', () => {
      // pnl=5000, cost=20000 → %25 → "+25,00%"
      renderCellUi({ colKey: 'unrealizedPct', holding: stockHolding });
      expect(screen.getByText(/\+25,00%/)).toBeInTheDocument();
    });
  });

  describe('realPnl hücresi (reel K/Z, ReelDash dalı)', () => {
    it('reel veri yokken açıklamalı tire (cursor-help) basar', () => {
      const { container } = renderCellUi({
        colKey: 'realPnl',
        holding: { symbol: 'X', assetType: 'STOCK', currency: 'TL' }, // realProfitLoss yok
      });
      const dash = container.querySelector('.cursor-help');
      expect(dash).toBeTruthy();
      expect(dash.textContent).toBe('-');
    });

    it('reel veri varken PnlAmt olarak basar', () => {
      renderCellUi({
        colKey: 'realPnl',
        holding: { symbol: 'X', assetType: 'STOCK', currency: 'TL', realProfitLoss: 750 },
      });
      expect(screen.getByText(/\+750,00\s*TL/)).toBeInTheDocument();
    });
  });

  describe('beatInflation hücresi (BeatChip)', () => {
    it('reel K/Z pozitifse "Yendi" rozeti gösterir', () => {
      renderCellUi({
        colKey: 'beatInflation',
        holding: { symbol: 'X', assetType: 'STOCK', realProfitLoss: 120 },
      });
      expect(screen.getByText('Yendi')).toBeInTheDocument();
    });

    it('reel K/Z negatifse "Yenildi" rozeti gösterir', () => {
      renderCellUi({
        colKey: 'beatInflation',
        holding: { symbol: 'X', assetType: 'STOCK', realProfitLoss: -30 },
      });
      expect(screen.getByText('Yenildi')).toBeInTheDocument();
    });
  });

  describe('inflationSince hücresi', () => {
    it('birikimli enflasyon yüzdesini + kaynak etiketini gösterir', () => {
      renderCellUi({
        colKey: 'inflationSince',
        holding: { symbol: 'X', assetType: 'STOCK', inflationSincePercent: 64.2, inflationSource: 'TÜFE' },
      });
      expect(screen.getByText(/64,20%/)).toBeInTheDocument();
      // kaynak rozeti
      expect(screen.getByText('TÜFE')).toBeInTheDocument();
    });
  });

  describe('currency / tarih hücreleri', () => {
    it('currency holding.currency değerini basar', () => {
      renderCellUi({ colKey: 'currency', holding: stockHolding });
      expect(screen.getByText('TL')).toBeInTheDocument();
    });

    it('firstBuyDate verisi yoksa Dash basar', () => {
      renderCellUi({ colKey: 'firstBuyDate', holding: { symbol: 'X', assetType: 'STOCK' } });
      expect(screen.getByText('-')).toBeInTheDocument();
    });
  });

  describe('trend hücresi (TrendBadge — LanguageProvider gerektirir)', () => {
    it('MA kesişimi yükselişteyse "Yükseliş" rozeti basar', () => {
      // price>ma20>ma50 → multi-signal UP
      renderCellUi({
        colKey: 'trend',
        holding: { symbol: 'THYAO', assetType: 'STOCK', currentPrice: 260, ma20: 240, ma50: 220 },
      });
      expect(screen.getByText('Yükseliş')).toBeInTheDocument();
    });

    it('trend hesaplanamazsa em-dash placeholder basar (throw etmez)', () => {
      const { container } = renderCellUi({
        colKey: 'trend',
        holding: { symbol: 'X', assetType: 'STOCK' }, // sinyal üretecek alan yok
      });
      // TrendBadge no-signal dalı "—" (em dash) basar
      expect(container.textContent).toContain('—');
    });
  });

  describe('VİOP-özel hücreler (sadece FUTURE)', () => {
    it('viopDirection FUTURE değilse Dash basar', () => {
      renderCellUi({ colKey: 'viopDirection', holding: stockHolding });
      expect(screen.getByText('-')).toBeInTheDocument();
    });

    it('viopDirection SHORT FUTURE için "SHORT" rozeti basar', () => {
      renderCellUi({
        colKey: 'viopDirection',
        holding: { assetType: 'FUTURE', viopDirection: 'SHORT', symbol: 'XU030' },
      });
      expect(screen.getByText('SHORT')).toBeInTheDocument();
    });

    it('marginStatus CRITICAL için "TEHLİKE" uyarısı gösterir', () => {
      renderCellUi({
        colKey: 'marginStatus',
        holding: { assetType: 'FUTURE', symbol: 'XU030', marginStatus: 'CRITICAL', marginRatio: 0.1 },
      });
      expect(screen.getByText(/TEHLİKE/)).toBeInTheDocument();
    });

    it('marginStatus HEALTHY için yeşil oran rozeti gösterir', () => {
      renderCellUi({
        colKey: 'marginStatus',
        holding: { assetType: 'FUTURE', symbol: 'XU030', marginStatus: 'HEALTHY', marginRatio: 0.8 },
      });
      // %80 → "%80"
      expect(screen.getByText(/%80/)).toBeInTheDocument();
    });

    it('viopNominal FUTURE için referans değeri + Info ikonu gösterir', () => {
      const { container } = renderCellUi({
        colKey: 'viopNominal',
        holding: { assetType: 'FUTURE', symbol: 'XU030', viopNotional: 50000, currency: 'TL' },
      });
      expect(screen.getByText(/50\.000,00\s*TL/)).toBeInTheDocument();
      // lucide Info ikonu (svg) eklenir
      expect(container.querySelector('svg')).toBeTruthy();
    });
  });

  describe('default dal (bilinmeyen kolon)', () => {
    it('tanınmayan kolon anahtarında Dash ("-") basar, throw ETMEZ', () => {
      renderCellUi({ colKey: 'kesinlikle_yok_boyle_bir_kolon', holding: stockHolding });
      expect(screen.getByText('-')).toBeInTheDocument();
    });
  });
});
