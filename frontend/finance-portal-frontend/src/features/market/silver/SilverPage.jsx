import { useEffect, useState, useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { TrendingUp, TrendingDown } from 'lucide-react';
import { getSilverSpot, getSilverHistory } from '../../../api/marketApi';

import GoldLoadingState  from '../gold/components/GoldLoadingState';
import GoldErrorState    from '../gold/components/GoldErrorState';
import GoldChart         from '../gold/components/GoldChart';
import GoldChartToolbar  from '../gold/components/GoldChartToolbar';
import PreciousCompareButton from '../../../components/common/PreciousCompareButton';
import UniversalCompareButton from '../../../components/common/UniversalCompareButton';
import InstrumentActionButtons from '../../../components/instrument/InstrumentActionButtons';

// Aktif sekme → COMMODITY sembol kategorisi (portföy/alarm/kıyas için)
const PRECIOUS_CAT = { try_gram: 'GRAM_TRY', try_kg: 'KG_TRY', usd_ons: 'USD_ONS', eur_ons: 'EUR_ONS' };
import GoldSourceNotice  from '../gold/components/GoldSourceNotice';
import PreciousStatsCard from '../precious/components/PreciousStatsCard';
import { useTranslation } from '../../../context/LanguageContext';

// ── Yardımcı ─────────────────────────────────────────────────────────────────
function fmt(v, dec = 2) {
  if (v == null || isNaN(parseFloat(v))) return '-';
  return parseFloat(v).toLocaleString('tr-TR', {
    minimumFractionDigits: dec,
    maximumFractionDigits: dec,
  });
}

// ── Tab tanımları ─────────────────────────────────────────────────────────────
const SILVER_TABS = [
  { key: 'try_gram', label: 'Gram Gümüş (₺)', currency: 'TRY', sym: '₺', isUsd: false, canCandle: true  },
  { key: 'try_kg',   label: 'Kg Gümüş (₺)',   currency: 'TRY', sym: '₺', isUsd: false, canCandle: true  },
  { key: 'usd_ons',  label: 'Ons Gümüş ($)',  currency: 'USD', sym: '$', isUsd: true,  canCandle: true  },
];

// ── Gümüş candle normalizasyon sabitleri ──────────────────────────────────────
const SILVER_SYNTHETIC_OPEN_MAX_GAP = 0.15;
const SILVER_MAX_CANDLE_RANGE = 0.25;

function buildSilverCandlePoint(p, previousClose, mult) {
  const rawClose = p.close != null && parseFloat(p.close) > 0 ? parseFloat(p.close) : null;
  const rawHigh  = p.high  != null && parseFloat(p.high)  > 0 ? parseFloat(p.high)  : null;
  const rawLow   = p.low   != null && parseFloat(p.low)   > 0 ? parseFloat(p.low)   : null;
  if (!rawClose || !rawHigh || !rawLow) return null;

  const close = rawClose * mult;
  const high  = rawHigh  * mult;
  const low   = rawLow   * mult;

  let open = previousClose != null ? previousClose : close;
  if (previousClose != null) {
    const gap = Math.abs(previousClose - close) / close;
    if (gap > SILVER_SYNTHETIC_OPEN_MAX_GAP) open = close;
  }

  let safeHigh = Math.max(high, open, close);
  let safeLow  = Math.min(low,  open, close);
  let normalized = false;

  if (safeLow > 0) {
    const rangeRatio = (safeHigh - safeLow) / close;
    if (rangeRatio > SILVER_MAX_CANDLE_RANGE) {
      safeHigh = Math.max(open, close) * 1.05;
      safeLow  = Math.min(open, close) * 0.95;
      normalized = true;
    }
  }

  return { open, high: safeHigh, low: safeLow, close, normalized };
}

// ── History noktalarını tab ve chart moduna göre dönüştür ─────────────────────
function buildDisplayPoints(historyPoints, activeTab, chartMode) {
  if (!historyPoints?.length) return [];

  const isCandle = chartMode === 'candle';
  let previousClose = null;

  return historyPoints.reduce((acc, p) => {
    if (activeTab.isUsd) {
      const close = p.close != null ? parseFloat(p.close) : null;
      const wa    = p.weightedAverageUsdOns != null ? parseFloat(p.weightedAverageUsdOns) : close;
      if (!close || close <= 0) return acc;

      if (isCandle) {
        const candle = buildSilverCandlePoint(p, previousClose, 1);
        if (!candle) return acc;
        previousClose = candle.close;
        acc.push({ ...p, displayClose: candle.close, displayOpen: candle.open,
          displayHigh: candle.high, displayLow: candle.low, displayWa: wa, normalized: candle.normalized });
      } else {
        const lineVal = (close != null && close > 0) ? close : wa;
        acc.push({ ...p, displayClose: lineVal, displayOpen: null, displayHigh: null, displayLow: null, displayWa: wa });
      }
      return acc;
    }

    const mult = activeTab.key === 'try_gram' ? 1 : 1000;

    if (isCandle) {
      const candle = buildSilverCandlePoint(p, previousClose, mult);
      if (!candle) return acc;
      previousClose = candle.close;
      const wa = p.weightedAverage != null ? parseFloat(p.weightedAverage) * mult : candle.close;
      acc.push({ ...p, displayClose: candle.close, displayOpen: candle.open,
        displayHigh: candle.high, displayLow: candle.low, displayWa: wa, normalized: candle.normalized });
    } else {
      const wa    = p.weightedAverage != null ? parseFloat(p.weightedAverage) * mult : null;
      const close = p.close != null ? parseFloat(p.close) * mult : null;
      const refVal = p.tryGram != null ? parseFloat(p.tryGram) * (activeTab.key === 'try_gram' ? 1 : 1000)
                   : p.value   != null ? parseFloat(p.value)   * mult
                   : null;
      // Çizgi grafik: kapanış öncelikli (portföy / modal ile aynı mantık)
      const lineVal = (close != null && close > 0) ? close : (wa != null && wa > 0) ? wa : refVal;
      if (!lineVal || lineVal <= 0) return acc;
      acc.push({ ...p, displayClose: lineVal, displayOpen: null, displayHigh: null, displayLow: null, displayWa: wa ?? lineVal });
    }
    return acc;
  }, []);
}

// ── Ana sayfa ─────────────────────────────────────────────────────────────────
export default function SilverPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const [spot,         setSpot]         = useState(null);
  const [history,      setHistory]      = useState(null);
  const [activeTabKey, setActiveTabKey] = useState('try_gram');
  const [range,        setRange]        = useState('1M');
  const [chartMode,    setChartMode]    = useState('line');
  const [loadingSpot,  setLoadingSpot]  = useState(true);
  const [loadingChart, setLoadingChart] = useState(false);
  const [error,        setError]        = useState('');
  const [showMA7,   setShowMA7]   = useState(false);
  const [showMA30,  setShowMA30]  = useState(false);
  const [showMA90,  setShowMA90]  = useState(false);
  const [showTrend, setShowTrend] = useState(false);

  const activeTab = SILVER_TABS.find(tb => tb.key === activeTabKey) ?? SILVER_TABS[0];

  useEffect(() => {
    const tabParam = searchParams.get('tab');
    if (tabParam && SILVER_TABS.some(x => x.key === tabParam)) {
      setActiveTabKey(tabParam);
    }
  }, [searchParams]);

  useEffect(() => {
    setLoadingSpot(true);
    getSilverSpot()
      .then(setSpot)
      .catch(() => setError(t('Gümüş verisi alınamadı.')))
      .finally(() => setLoadingSpot(false));
  }, []);

  const loadHistory = useCallback(() => {
    setLoadingChart(true);
    const currency = activeTab.currency;

    // Backend zaten ALL/5Y için AG endpoint'ini, diğerleri için veri-sorgulama'yı kullanıyor
    // Mum grafik için de getSilverHistory — OHLC + AG hybrid veri döner
    getSilverHistory(range, currency)
      .then(setHistory)
      .catch(() => setHistory(null))
      .finally(() => setLoadingChart(false));
  }, [range, activeTab.currency]);

  useEffect(() => { loadHistory(); }, [loadHistory]);

  function handleTabChange(tabKey) {
    setActiveTabKey(tabKey);
    setChartMode('line');
  }

  // Range değişince 5Y/ALL'da mum modunu kapat
  function handleRangeChange(newRange) {
    setRange(newRange);
    if (newRange === '5Y' || newRange === 'ALL') {
      setChartMode('line');
    }
  }

  const displayPoints = useMemo(
    () => buildDisplayPoints(history?.points, activeTab, chartMode),
    [history?.points, activeTab, chartMode]
  );

  const normalizedCount = useMemo(
    () => displayPoints.filter(p => p.normalized).length,
    [displayPoints]
  );

  const isDown = useMemo(() => {
    if (!displayPoints.length) return false;
    const first = parseFloat(displayPoints[0].displayClose ?? 0);
    const last  = parseFloat(displayPoints[displayPoints.length - 1].displayClose ?? 0);
    return last < first;
  }, [displayPoints]);

  const stats = useMemo(() => {
    if (!displayPoints.length) return null;
    const closes = displayPoints.map(p => parseFloat(p.displayClose)).filter(v => !isNaN(v) && v > 0);
    const highs  = displayPoints.map(p => parseFloat(p.displayHigh ?? p.displayClose)).filter(v => !isNaN(v) && v > 0);
    const lows   = displayPoints.map(p => parseFloat(p.displayLow  ?? p.displayClose)).filter(v => !isNaN(v) && v > 0);
    const was    = displayPoints.map(p => parseFloat(p.displayWa   ?? p.displayClose)).filter(v => !isNaN(v) && v > 0);
    return {
      last:  closes.length ? closes[closes.length - 1] : null,
      high:  highs.length  ? Math.max(...highs)  : null,
      low:   lows.length   ? Math.min(...lows)   : null,
      wa:    was.length    ? was.reduce((a, b) => a + b, 0) / was.length : null,
      first: closes.length ? closes[0] : null,
    };
  }, [displayPoints]);

  const changePct = stats?.first && stats?.last
    ? ((stats.last - stats.first) / stats.first) * 100 : null;

  /** BIST spot kapanış — grafik yüklenmeden önce gösterim */
  const spotClosePrice = useMemo(() => {
    if (!spot) return null;
    if (activeTab.isUsd) return spot.silverUsdOns != null ? parseFloat(spot.silverUsdOns) : null;
    if (activeTab.key === 'try_gram') {
      const c = spot.silverGramCloseTry ?? spot.silverGramTry;
      return c != null ? parseFloat(c) : null;
    }
    const kg = spot.closeTryKg ?? spot.weightedAverageTryKg;
    return kg != null ? parseFloat(kg) : null;
  }, [spot, activeTab]);

  /** Seçili aralığın son kapanışı; yoksa spot kapanış */
  const displayPrice = stats?.last ?? spotClosePrice;

  const sym = activeTab.sym;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">{t('Gümüş')}</h1>
        <p className="text-sm text-gray-500 mt-1.5 pl-4">
          {t('Gösterilen fiyatlar')} <span className="font-semibold text-gray-600">{t('Borsa İstanbul (BİST)')}</span> {t('kıymetli maden referanslarına dayanır.')}
        </p>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {/* Tab bar */}
        <div className="flex overflow-x-auto border-b border-gray-200">
          {SILVER_TABS.map(tab => (
            <button key={tab.key} onClick={() => handleTabChange(tab.key)}
              className={`px-5 py-3 text-sm font-semibold whitespace-nowrap transition-all border-b-2 ${
                activeTabKey === tab.key
                  ? 'border-[#093eaa] text-[#093eaa] bg-blue-50'
                  : 'border-transparent text-gray-600 hover:text-[#093eaa] hover:bg-gray-50'
              }`}>
              {t(tab.label)}
            </button>
          ))}
        </div>

        <div className="p-3 sm:p-6">
          {loadingSpot ? (
            <GoldLoadingState />
          ) : error ? (
            <GoldErrorState message={error} />
          ) : spot ? (
            <>
              {/* Header */}
              <div className="mb-5">
                <div className="flex items-center gap-2 mb-1 flex-wrap">
                  <h2 className="text-lg font-bold text-gray-700 tracking-wide">
                    {t(activeTab.label).toUpperCase()}
                  </h2>
                  {spot.official && (
                    <span className="text-xs font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded-full">
                      {t('Borsa İstanbul')}
                    </span>
                  )}
                  {spot.stale && (
                    <span className="text-xs font-bold text-amber-700 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-full">
                      {t('Eski Veri')}
                    </span>
                  )}
                </div>
                <div className="flex items-baseline gap-3 flex-wrap">
                  <span className={`text-3xl sm:text-4xl font-black ${isDown ? 'text-rose-600' : 'text-emerald-600'}`}>
                    {sym}{fmt(displayPrice)}
                  </span>
                  {changePct != null && (
                    <span className={`flex items-center gap-1 text-sm font-semibold ${isDown ? 'text-rose-600' : 'text-emerald-600'}`}>
                      {isDown ? <TrendingDown className="w-4 h-4" /> : <TrendingUp className="w-4 h-4" />}
                      ({changePct >= 0 ? '+' : ''}{fmt(changePct)}%)
                    </span>
                  )}
                </div>
                {spot.lastValidDate && (
                  <p className="text-xs text-gray-400 mt-1">{t('BİST:')} {spot.lastValidDate}</p>
                )}
              </div>

              {/* ── SOL: toolbar + grafik 2/3 · SAĞ: butonlar + fiyat bilgileri 1/3 ── */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-3 sm:gap-5 items-start">
                <div className="lg:col-span-2 min-w-0">
                  <GoldChartToolbar
                    activeTab={activeTab}
                    range={range}
                    onRangeChange={handleRangeChange}
                    chartMode={chartMode}
                    onChartModeChange={setChartMode}
                    showMA7={showMA7}   onToggleMA7={() => setShowMA7(v => !v)}
                    showMA30={showMA30} onToggleMA30={() => setShowMA30(v => !v)}
                    showMA90={showMA90} onToggleMA90={() => setShowMA90(v => !v)}
                    showTrend={showTrend} onToggleTrend={() => setShowTrend(v => !v)}
                    loading={loadingChart}
                    onRefresh={loadHistory}
                  />
                  {chartMode === 'candle' && (
                    <div className="mb-3 text-xs text-amber-600 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
                      {t('⚠ Gümüş mum grafiğinde açılış değeri BIST tarafından verilmediği için sentetik olarak hesaplanmıştır.')}
                      {normalizedCount > 0 && (
                        <span className="ml-1">
                          {normalizedCount} {t('günün aşırı sapmalı değerleri görsel tutarlılık için normalize edildi.')}
                        </span>
                      )}
                    </div>
                  )}
                  <GoldChart
                    key={`${activeTabKey}-${range}-${chartMode}`}
                    points={displayPoints}
                    chartMode={chartMode}
                    isDown={isDown}
                    loading={loadingChart}
                    showMA7={showMA7}
                    showMA30={showMA30}
                    showMA90={showMA90}
                    showTrend={showTrend}
                    currency={activeTab.isUsd ? 'USD' : 'TRY'}
                    height={300}
                    persistId={`silver:${activeTabKey}`}
                  />
                </div>
                {/* SAĞ: butonlar (üstte) + bilgi kartı */}
                <div className="min-w-0 space-y-3">
                  <div className="flex items-center gap-2 flex-wrap">
                    <PreciousCompareButton />
                    <UniversalCompareButton
                      assetType="COMMODITY"
                      symbol={`SILVER:${PRECIOUS_CAT[activeTabKey] ?? 'GRAM_TRY'}`}
                      name={t(activeTab.label)}
                    />
                    <InstrumentActionButtons
                      assetType="COMMODITY"
                      symbol={`SILVER:${PRECIOUS_CAT[activeTabKey] ?? 'GRAM_TRY'}`}
                      name={t(activeTab.label)}
                      price={displayPrice}
                    />
                  </div>
                  <PreciousStatsCard
                    stats={[
                      { label: 'Güncel Fiyat (Kapanış)', value: displayPrice != null ? `${sym}${fmt(displayPrice)}` : '-', highlight: true },
                      { label: 'En Yüksek',     value: stats?.high != null ? `${sym}${fmt(stats.high)}` : '-' },
                      { label: 'En Düşük',      value: stats?.low  != null ? `${sym}${fmt(stats.low)}`  : '-' },
                      { label: 'Dönem Kapanış', value: stats?.last != null ? `${sym}${fmt(stats.last)}` : '-' },
                      { label: 'Dönem Ağırlıklı Ort.', value: stats?.wa != null ? `${sym}${fmt(stats.wa)}` : '-' },
                      {
                        label: 'Dönem Değişimi',
                        value: changePct != null ? `${changePct >= 0 ? '+' : ''}${fmt(changePct)}%` : '-',
                        color: changePct != null ? (changePct >= 0 ? 'text-emerald-600' : 'text-rose-600') : null,
                      },
                      ...(!activeTab.isUsd ? [
                        { label: 'TL/Kg Kapanış', value: spot.closeTryKg != null ? `₺${fmt(spot.closeTryKg, 0)}` : '-' },
                        { label: 'TL/Gram',       value: spot.silverGramTry != null ? `₺${fmt(spot.silverGramTry)}` : '-' },
                      ] : [
                        { label: 'USD/Ons', value: spot.silverUsdOns != null ? `$${fmt(spot.silverUsdOns)}` : '-' },
                      ]),
                    ]}
                  />
                </div>
              </div>

              <div className="mt-4">
                <GoldSourceNotice
                  source={history?.source ?? spot?.source}
                  official={history?.official ?? spot?.official}
                  fallback={history?.fallback ?? spot?.fallback}
                  disclaimer={history?.disclaimer ?? spot?.disclaimer}
                />
              </div>
            </>
          ) : null}
        </div>
      </div>

      {/* Spot özet kartları */}
      {spot && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {[
            { label: 'Gram Gümüş (₺)', value: spot.silverGramCloseTry ?? spot.silverGramTry, sym: '₺' },
            { label: 'Kg Gümüş (₺)',   value: spot.closeTryKg ?? spot.weightedAverageTryKg, sym: '₺', dec: 0 },
            { label: 'Ons Gümüş ($)',  value: spot.silverUsdOns,                            sym: '$' },
          ].map(card => (
            <div key={card.label} className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-5 text-center">
              <p className="text-xs text-gray-500 font-semibold mb-2">{t(card.label)}</p>
              <p className="text-xl sm:text-2xl font-black text-gray-900">
                {card.value != null ? `${card.sym}${fmt(card.value, card.dec ?? 2)}` : '-'}
              </p>
              <p className="text-xs text-gray-400 mt-1">{t('Borsa İstanbul')}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
