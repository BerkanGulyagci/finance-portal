import { useEffect, useState, useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { TrendingUp, TrendingDown } from 'lucide-react';
import { getPreciousMetalSpot, getPreciousMetalHistory } from '../../../api/marketApi';

import GoldLoadingState  from '../gold/components/GoldLoadingState';
import GoldErrorState    from '../gold/components/GoldErrorState';
import GoldChart         from '../gold/components/GoldChart';
import GoldChartToolbar  from '../gold/components/GoldChartToolbar';
import GoldSourceNotice  from '../gold/components/GoldSourceNotice';
import { useTranslation } from '../../../i18n/LanguageContext';

// ── Yardımcı ─────────────────────────────────────────────────────────────────
function fmt(v, dec = 2) {
  if (v == null || isNaN(parseFloat(v))) return '-';
  return parseFloat(v).toLocaleString('tr-TR', {
    minimumFractionDigits: dec,
    maximumFractionDigits: dec,
  });
}

function StatRow({ label, value, colored, positive }) {
  const { t } = useTranslation();
  return (
    <div className="flex justify-between items-center py-2 border-b border-gray-50">
      <span className="text-xs font-bold text-gray-500 tracking-wider">{t(label)}</span>
      <span className={`text-sm font-semibold ${colored ? (positive ? 'text-emerald-600' : 'text-rose-600') : 'text-gray-900'}`}>
        {value}
      </span>
    </div>
  );
}

// ── Tab tanımları ─────────────────────────────────────────────────────────────
// Platin/Paladyum'da OHLC yok → canCandle: false, sadece çizgi grafik
const METAL_TABS = [
  { key: 'try_gram', label: (name) => `Gram ${name} (₺)`, currency: 'TRY', sym: '₺', isUsd: false, isEur: false, canCandle: false },
  { key: 'try_kg',   label: (name) => `Kg ${name} (₺)`,   currency: 'TRY', sym: '₺', isUsd: false, isEur: false, canCandle: false },
  { key: 'usd_ons',  label: (name) => `Ons ${name} ($)`,  currency: 'USD', sym: '$', isUsd: true,  isEur: false, canCandle: false },
  { key: 'eur_ons',  label: (name) => `Ons ${name} (€)`,  currency: 'EUR', sym: '€', isUsd: false, isEur: true,  canCandle: false },
];

// ── History noktalarını tab'a göre dönüştür ───────────────────────────────────
// Platin/Paladyum history'de value alanı seçilen currency'ye göre dolu gelir
function buildDisplayPoints(historyPoints, activeTab) {
  if (!historyPoints?.length) return [];
  return historyPoints.map(p => {
    let close = null;
    if (activeTab.isUsd) {
      close = p.usdOns != null ? parseFloat(p.usdOns) : (p.value != null ? parseFloat(p.value) : null);
    } else if (activeTab.isEur) {
      close = p.eurOns != null ? parseFloat(p.eurOns) : (p.value != null ? parseFloat(p.value) : null);
    } else {
      // TRY — gram veya kg
      const isGram = activeTab.key === 'try_gram';
      if (isGram) {
        close = p.tryGram != null ? parseFloat(p.tryGram) : (p.value != null ? parseFloat(p.value) : null);
      } else {
        close = p.tryKg != null ? parseFloat(p.tryKg) : (p.value != null ? parseFloat(p.value) * 1000 : null);
      }
    }
    return {
      ...p,
      displayClose: close,
      displayOpen:  null, // OHLC yok
      displayHigh:  null,
      displayLow:   null,
      displayWa:    p.value != null ? parseFloat(p.value) : close,
    };
  }).filter(p => p.displayClose != null && p.displayClose > 0);
}

const METAL_TAB_KEYS = ['try_gram', 'try_kg', 'usd_ons', 'eur_ons'];

// ── Ana bileşen ───────────────────────────────────────────────────────────────
/**
 * @param {string} metal - 'platinum' veya 'palladium'
 * @param {string} metalName - 'Platin' veya 'Paladyum'
 * @param {string} accentColor - Tailwind renk sınıfı (opsiyonel)
 */
export default function PreciousMetalPage({ metal, metalName }) {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const [spot,         setSpot]         = useState(null);
  const [history,      setHistory]      = useState(null);
  const [activeTabKey, setActiveTabKey] = useState('try_gram');
  const [range,        setRange]        = useState('1M');
  const [loadingSpot,  setLoadingSpot]  = useState(true);
  const [loadingChart, setLoadingChart] = useState(false);
  const [error,        setError]        = useState('');
  const [showMA7,   setShowMA7]   = useState(false);
  const [showMA30,  setShowMA30]  = useState(false);
  const [showMA90,  setShowMA90]  = useState(false);
  const [showTrend, setShowTrend] = useState(false);

  const activeTab = {
    ...METAL_TABS.find(tb => tb.key === activeTabKey) ?? METAL_TABS[0],
    label: METAL_TABS.find(tb => tb.key === activeTabKey)?.label(metalName) ?? metalName,
  };

  useEffect(() => {
    const tabParam = searchParams.get('tab');
    if (tabParam && METAL_TAB_KEYS.includes(tabParam)) {
      setActiveTabKey(tabParam);
    }
  }, [searchParams]);

  useEffect(() => {
    setLoadingSpot(true);
    getPreciousMetalSpot(metal)
      .then(setSpot)
      .catch(() => setError(`${metalName} ${t('verisi alınamadı.')}`))
      .finally(() => setLoadingSpot(false));
  }, [metal, metalName]);

  const loadHistory = useCallback(() => {
    setLoadingChart(true);
    getPreciousMetalHistory(metal, range, activeTab.currency)
      .then(setHistory)
      .catch(() => setHistory(null))
      .finally(() => setLoadingChart(false));
  }, [metal, range, activeTab.currency]);

  useEffect(() => { loadHistory(); }, [loadHistory]);

  const displayPoints = useMemo(
    () => buildDisplayPoints(history?.points, activeTab),
    [history?.points, activeTab]
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
    return {
      last:  closes.length ? closes[closes.length - 1] : null,
      high:  closes.length ? Math.max(...closes) : null,
      low:   closes.length ? Math.min(...closes) : null,
      first: closes.length ? closes[0] : null,
    };
  }, [displayPoints]);

  const changePct = stats?.first && stats?.last
    ? ((stats.last - stats.first) / stats.first) * 100 : null;

  const currentPrice = (() => {
    if (!spot) return null;
    if (activeTab.isUsd) return spot.usdOns;
    if (activeTab.isEur) return spot.eurOns;
    if (activeTab.key === 'try_gram') return spot.tryGram;
    return spot.tryKg;
  })();

  const sym = activeTab.sym;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">{metalName}</h1>
        <p className="text-sm text-gray-500 mt-1.5 pl-4">
          {t('Gösterilen fiyatlar')} <span className="font-semibold text-gray-600">{t('Borsa İstanbul (BİST)')}</span> {t('kıymetli maden referanslarına dayanır.')}
        </p>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {/* Tab bar */}
        <div className="flex overflow-x-auto border-b border-gray-200">
          {METAL_TABS.map(tab => (
            <button key={tab.key} onClick={() => setActiveTabKey(tab.key)}
              className={`px-5 py-3 text-sm font-semibold whitespace-nowrap transition-all border-b-2 ${
                activeTabKey === tab.key
                  ? 'border-[#093eaa] text-[#093eaa] bg-blue-50'
                  : 'border-transparent text-gray-600 hover:text-[#093eaa] hover:bg-gray-50'
              }`}>
              {tab.label(metalName)}
            </button>
          ))}
        </div>

        <div className="p-6">
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
                    {activeTab.label.toUpperCase()}
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
                  <span className={`text-4xl font-black ${isDown ? 'text-rose-600' : 'text-emerald-600'}`}>
                    {sym}{fmt(currentPrice)}
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

              {/* İstatistikler */}
              <div className="grid grid-cols-2 gap-x-8 gap-y-0 border-t border-gray-100 pt-4 mb-6">
                <StatRow label="Güncel Fiyat"  value={currentPrice != null ? `${sym}${fmt(currentPrice)}` : '-'} />
                <StatRow label="En Yüksek"     value={stats?.high != null ? `${sym}${fmt(stats.high)}` : '-'} />
                <StatRow label="En Düşük"      value={stats?.low  != null ? `${sym}${fmt(stats.low)}`  : '-'} />
                <StatRow label="Kapanış"       value={stats?.last != null ? `${sym}${fmt(stats.last)}` : '-'} />
                <StatRow label="Dönem Değişimi"
                  value={changePct != null ? `${changePct >= 0 ? '+' : ''}${fmt(changePct)}%` : '-'}
                  colored={changePct != null} positive={changePct != null && changePct >= 0} />
                <StatRow label="USD/Ons"  value={spot.usdOns  != null ? `$${fmt(spot.usdOns)}`  : '-'} />
                <StatRow label="TL/Gram"  value={spot.tryGram != null ? `₺${fmt(spot.tryGram)}` : '-'} />
                <StatRow label="TL/Kg"    value={spot.tryKg   != null ? `₺${fmt(spot.tryKg, 0)}` : '-'} />
                <StatRow label="EUR/Ons"  value={spot.eurOns  != null ? `€${fmt(spot.eurOns)}`  : '-'} />
              </div>

              {/* Toolbar — canCandle:false → mum grafik butonu yok */}
              <GoldChartToolbar
                activeTab={activeTab}
                range={range}
                onRangeChange={setRange}
                chartMode="line"
                onChartModeChange={() => {}}
                showMA7={showMA7}   onToggleMA7={() => setShowMA7(v => !v)}
                showMA30={showMA30} onToggleMA30={() => setShowMA30(v => !v)}
                showMA90={showMA90} onToggleMA90={() => setShowMA90(v => !v)}
                showTrend={showTrend} onToggleTrend={() => setShowTrend(v => !v)}
                loading={loadingChart}
                onRefresh={loadHistory}
              />

              {/* Grafik — sadece çizgi */}
              <GoldChart
                key={`${activeTabKey}-${range}`}
                points={displayPoints}
                chartMode="line"
                isDown={isDown}
                loading={loadingChart}
                showMA7={showMA7}
                showMA30={showMA30}
                showMA90={showMA90}
                showTrend={showTrend}
                currency={activeTab.isUsd ? 'USD' : activeTab.isEur ? 'EUR' : 'TRY'}
              />

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
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {[
            { label: `Gram ${metalName} (₺)`, value: spot.tryGram, sym: '₺' },
            { label: `Kg ${metalName} (₺)`,   value: spot.tryKg,   sym: '₺', dec: 0 },
            { label: `Ons ${metalName} ($)`,   value: spot.usdOns,  sym: '$' },
            { label: `Ons ${metalName} (€)`,   value: spot.eurOns,  sym: '€' },
          ].map(card => (
            <div key={card.label} className="bg-white rounded-2xl border border-gray-200 shadow-sm p-4 text-center">
              <p className="text-xs text-gray-500 font-semibold mb-2">{card.label}</p>
              <p className="text-xl font-black text-gray-900">
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
