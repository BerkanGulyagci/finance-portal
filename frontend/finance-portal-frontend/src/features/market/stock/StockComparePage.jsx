import EChartsCompareChart from './components/EChartsCompareChart';
import PerformanceMetricsTable from './components/PerformanceMetricsTable';
import TlInvestmentSimulation from './components/TlInvestmentSimulation';
import FinancialComparisonTable from './components/FinancialComparisonTable';
import BounceDots from './components/BounceDots';
import { Link } from 'react-router-dom';
import { ArrowLeft, X, Plus, BarChart2 } from 'lucide-react';
import { STOCK_CHART_RANGES } from './utils/stockChartRanges';
import { useTranslation } from '../../../context/LanguageContext';
import InstrumentSearchModal from '../../../components/instrument/InstrumentSearchModal';
import { ASSET_LABELS, BENCHMARK_SHORTCUTS, INDEX_SHORTCUTS, COLORS, MAX_STOCKS } from './utils/stockCompareUtils';
import { useStockCompare } from './hooks/useStockCompare';

const RANGES = STOCK_CHART_RANGES;

// ── ECharts Karşılaştırma Grafiği ─────────────────────────────────────────────
export default function StockComparePage() {
  const { t } = useTranslation();
  const {
    selectedSymbols, extraItems, searchOpen, setSearchOpen,
    chartSeriesDefs, rangeIdx, chartData, rawPrices, midasDetails,
    chartLoading, detailsLoading, compared, bist100Prices, investment, setInvestment,
    totalCount, mixedMode,
    addSymbol, removeSymbol, addExtra, removeExtra, handleCompare, handleRangeChange,
  } = useStockCompare();

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="max-w-5xl mx-auto space-y-6">

      {searchOpen && (
        <InstrumentSearchModal onSelect={addExtra} onClose={() => setSearchOpen(false)} allowIndices />
      )}

      {/* ── Başlık ── */}
      <div className="flex items-center gap-3">
        <Link to="/market/stocks" className="text-gray-400 hover:text-[#093eaa] transition-colors">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('Varlık Karşılaştırma')}</h1>
          <p className="text-xs text-gray-400 mt-0.5">
            {t('Hisse, kripto, döviz, altın, fon, enflasyon… her şeyi yan yana karşılaştır — en fazla')} {MAX_STOCKS} {t('öğe')}
          </p>
        </div>
      </div>

      {/* ── Hisse Seçici ── */}
      <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-3 sm:p-5">
        <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">{t('Hisse Ekle')}</p>

        {/* Hızlı ekle: endeksler + faiz/enflasyon benchmark'ları */}
        <div className="flex flex-wrap gap-2 mb-3 items-center">
          <span className="text-xs text-gray-400 self-center">{t('Hızlı ekle:')}</span>
          {INDEX_SHORTCUTS.map(idx => {
            const isSelected = selectedSymbols.includes(idx.symbol);
            return (
              <button
                key={idx.symbol}
                onClick={() => isSelected ? removeSymbol(idx.symbol) : addSymbol(idx.symbol)}
                className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-bold transition-all border ${
                  isSelected
                    ? 'bg-[#093eaa] text-white border-[#093eaa]'
                    : 'bg-gray-50 text-gray-600 border-gray-200 hover:border-[#093eaa] hover:text-[#093eaa]'
                }`}
              >
                {idx.label}
                {isSelected && <X className="w-3 h-3 ml-0.5" />}
              </button>
            );
          })}
          <span className="w-px h-4 bg-gray-200 mx-0.5" />
          {BENCHMARK_SHORTCUTS.map(b => {
            const isSelected = extraItems.some(e => e.key === b.key);
            return (
              <button
                key={b.key}
                onClick={() => isSelected ? removeExtra(b.key) : addExtra({ assetType: b.assetType, symbol: b.symbol, name: b.label })}
                className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-bold transition-all border ${
                  isSelected
                    ? 'bg-[#7c3aed] text-white border-[#7c3aed]'
                    : 'bg-gray-50 text-gray-600 border-gray-200 hover:border-[#7c3aed] hover:text-[#7c3aed]'
                }`}
              >
                {t(b.label)}
                {isSelected && <X className="w-3 h-3 ml-0.5" />}
              </button>
            );
          })}
        </div>

        {/* Seçili hisseler (chip'ler) */}
        <div className="flex flex-wrap gap-2 mb-4">
          {selectedSymbols.map((sym, idx) => {
            const indexShortcut = INDEX_SHORTCUTS.find(i => i.symbol === sym);
            const displayLabel = indexShortcut
              ? indexShortcut.label
              : sym.replace('.IS', '').replace('.is', '').toUpperCase();
            return (
              <span
                key={sym}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-semibold text-white"
                style={{ background: COLORS[idx % COLORS.length] }}
              >
                {displayLabel}
                <button
                  onClick={() => removeSymbol(sym)}
                  className="ml-0.5 p-1.5 -m-1.5 sm:p-0 sm:m-0 sm:ml-0.5 hover:opacity-70 transition-opacity"
                  aria-label={`${sym} ${t('kaldır')}`}
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </span>
            );
          })}

          {/* Ekstra (hisse-dışı) öğeler */}
          {extraItems.map((it, ei) => {
            const globalIdx = selectedSymbols.length + ei;
            const typeLabel = ASSET_LABELS[it.assetType] ? t(ASSET_LABELS[it.assetType]) : it.assetType;
            return (
              <span
                key={it.key}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-semibold text-white"
                style={{ background: COLORS[globalIdx % COLORS.length] }}
              >
                {t(it.name || it.symbol)}
                <span className="opacity-70 text-xs">· {typeLabel}</span>
                <button
                  onClick={() => removeExtra(it.key)}
                  className="ml-0.5 p-1.5 -m-1.5 sm:p-0 sm:m-0 sm:ml-0.5 hover:opacity-70 transition-opacity"
                  aria-label={`${it.symbol} ${t('kaldır')}`}
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </span>
            );
          })}

          {/* Tek "Ekle" — herhangi bir enstrüman (hisse, kripto, altın, döviz, fon…) */}
          {totalCount < MAX_STOCKS && (
            <button
              onClick={() => setSearchOpen(true)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md border-2 border-dashed border-gray-300 text-sm text-gray-500 hover:border-[#093eaa] hover:text-[#093eaa] transition-colors"
            >
              <Plus className="w-3.5 h-3.5" /> {t('Ekle')}
            </button>
          )}
        </div>

        {/* Aralık seçici + Karşılaştır butonu */}
        <div className="flex flex-col sm:flex-row sm:items-center gap-3 sm:flex-wrap">
          <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5">
            {RANGES.map((r, i) => (
              <button
                key={r.label}
                onClick={() => handleRangeChange(i)}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition-all ${
                  i === rangeIdx
                    ? 'bg-white text-[#093eaa] shadow-sm'
                    : 'text-gray-500 hover:text-gray-800'
                }`}
              >
                {t(r.label)}
              </button>
            ))}
          </div>

          <button
            onClick={handleCompare}
            disabled={totalCount < 2 || chartLoading}
            className="sm:ml-auto px-5 py-2 bg-[#093eaa] text-white rounded-lg text-sm font-bold hover:bg-[#0730a0] transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            <BarChart2 className="w-4 h-4" />
            {chartLoading ? t('Yükleniyor...') : t('Karşılaştır')}
          </button>
        </div>

        {totalCount < 2 && totalCount > 0 && (
          <p className="text-xs text-amber-500 mt-2">
            {t('Karşılaştırma için en az 2 öğe seçmelisin.')}
          </p>
        )}
        {mixedMode && (
          <p className="text-xs text-gray-400 mt-2">
            {t('Farklı türde varlık eklendi — yalnızca normalize % grafik gösterilir (detaylı metrik tabloları yalnız hisse-hisse kıyasında çıkar).')}
          </p>
        )}
      </div>

      {/* ── Grafik Yükleniyor ── */}
      {chartLoading && (
        <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-8 sm:p-16 flex items-center justify-center">
          <BounceDots />
        </div>
      )}

      {/* ── Normalize Grafik ── */}
      {compared && !chartLoading && chartData.length >= 2 && (
        <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-3 sm:p-6">
          <div className="mb-4">
            <h2 className="font-bold text-gray-900">{t('Göreceli Performans (%)')}</h2>
            <p className="text-xs text-gray-400 mt-0.5">
              {t('Hepsi ortak başlangıç tarihinden 0%\'dan başlar (en geç başlayan varlığın tarihi) — adil kümülatif % kıyas · Scroll ile zoom')}
            </p>
            {extraItems.some(e => ['TUFE', 'USCPI_TRY', 'DEPOSIT'].includes(e.symbol)) && (
              <p className="text-[11px] text-amber-600 mt-1">
                {t('Not: Enflasyon (TÜFE) son yayımlanan aya kadardır (~1–2 ay gecikmeli); kısa aralıklarda (3A vb.) son ayları içermeyip düz görünebilir.')}
              </p>
            )}
          </div>
          <EChartsCompareChart chartData={chartData} seriesDefs={chartSeriesDefs} />

          {/* TÜFE / ABD Enflasyonu seçiliyken: ne anlama geldiklerinin kısa açıklaması */}
          {(extraItems.some(e => e.symbol === 'TUFE') || extraItems.some(e => e.symbol === 'USCPI_TRY')) && (
            <div className="mt-4 pt-3 border-t border-gray-100 space-y-2 text-[11px] text-gray-500 leading-relaxed">
              {extraItems.some(e => e.symbol === 'TUFE') && (
                <p>
                  <span className="font-bold text-gray-700">{t('Enflasyon (TÜFE)')}:</span>{' '}
                  {t('bir yatırım değil, ölçü çizgisidir — TL fiyatların ne kadar arttığını gösterir. Bir varlık bu çizginin ÜSTÜNDEyse paran fiyatlardan hızlı büyümüştür (reel kazanç); ALTINDAysa sayı artsa bile alım gücün düşmüştür.')}
                </p>
              )}
              {extraItems.some(e => e.symbol === 'USCPI_TRY') && (
                <p>
                  <span className="font-bold text-gray-700">{t('ABD Enflasyonu')}:</span>{' '}
                  {t('ABD mallarının TL fiyatıdır (ABD enflasyonu × USD/TRY) — "dolar alım gücünü korudun mu" ölçüsü. Çizginin yüksekliği ABD enflasyonundan değil, ağırlıkla TL\'nin dolara karşı değer kaybından (USD/TRY artışından) gelir.')}
                </p>
              )}
            </div>
          )}
        </div>
      )}

      {/* ── Yeterli veri yok (kıyaslandı ama ortak/çizilebilir veri < 2 nokta) ── */}
      {compared && !chartLoading && chartData.length < 2 && (
        <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-6 sm:p-14 text-center">
          <BarChart2 className="w-12 h-12 text-gray-200 mx-auto mb-3" />
          <p className="text-gray-500 font-semibold">{t('Yeterli veri bulunamadı')}</p>
          <p className="text-gray-400 text-sm mt-1">
            {t('Seçili varlıkların bu aralıkta ortak/yeterli geçmişi yok. Daha kısa bir aralık (ör. 6A) deneyin ya da farklı varlık seçin.')}
          </p>
        </div>
      )}

      {/* ── Performans Metrikleri Tablosu (yalnız hisse-hisse kıyasında) ── */}
      {compared && !chartLoading && !mixedMode && Object.keys(rawPrices).length > 0 && (
        <PerformanceMetricsTable
          selectedSymbols={selectedSymbols}
          rawPrices={rawPrices}
          bist100Prices={bist100Prices}
          rangeIdx={rangeIdx}
          t={t}
        />
      )}

      {/* ── Finansal Karşılaştırma Tablosu — sadece gerçek hisseler için ── */}
      {compared && !chartLoading && !mixedMode && (
        <FinancialComparisonTable
          selectedSymbols={selectedSymbols}
          midasDetails={midasDetails}
          detailsLoading={detailsLoading}
          t={t}
        />
      )}

      {/* ── TL Yatırım Simülasyonu — sadece gerçek hisseler için ── */}
      {compared && !chartLoading && !mixedMode && (
        <TlInvestmentSimulation
          selectedSymbols={selectedSymbols}
          rawPrices={rawPrices}
          investment={investment}
          setInvestment={setInvestment}
          t={t}
        />
      )}
      {!compared && !chartLoading && (
        <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-6 sm:p-14 text-center">
          <BarChart2 className="w-12 h-12 text-gray-200 mx-auto mb-3" />
          <p className="text-gray-500 font-semibold">
            {selectedSymbols.length === 0
              ? t('Karşılaştırmak istediğin hisseleri seç')
              : selectedSymbols.length === 1
              ? t('En az bir hisse daha ekle')
              : t('"Karşılaştır" butonuna tıkla')}
          </p>
          <p className="text-gray-400 text-sm mt-1">
            {t('En fazla')} {MAX_STOCKS} {t('BIST hissesini yan yana karşılaştırabilirsin')}
          </p>
        </div>
      )}
    </div>
  );
}
