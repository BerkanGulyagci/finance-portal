import { useEffect, useState, useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getGoldSpot, getGoldHistory } from '../../../api/marketApi';

import { GOLD_TABS, calcTheoreticalPrice } from './utils/goldConstants';
import GoldTabs                   from './components/GoldTabs';
import GoldLoadingState           from './components/GoldLoadingState';
import GoldErrorState             from './components/GoldErrorState';
import GoldHeaderCard             from './components/GoldHeaderCard';
import GoldPriceStats             from './components/GoldPriceStats';
import GoldChartToolbar           from './components/GoldChartToolbar';
import TrendBadge                 from '../../../components/common/TrendBadge';
import UniversalCompareButton      from '../../../components/common/UniversalCompareButton';
import PreciousCompareButton       from '../../../components/common/PreciousCompareButton';
import InstrumentActionButtons     from '../../../components/instrument/InstrumentActionButtons';
import { buildTrendItem }         from '../../../utils/trendUtils';
import GoldChart                  from './components/GoldChart';
import GoldSourceNotice           from './components/GoldSourceNotice';
import GoldTheoreticalPricesTable from './components/GoldTheoreticalPricesTable';
import GoldCalculator             from './components/GoldCalculator';
import { useTranslation } from '../../../context/LanguageContext';

// Alarm/işlem ön-doldurması için: compareSymbol → spot TRY fiyat alanı (backend probe ile aynı eşleme)
const GOLD_SPOT_FIELD_BY_SYMBOL = {
  GOLD: 'onsTry',
  GRAM: 'gramGoldTry',
  CEYREK: 'quarterGoldTry',
  YARIM: 'halfGoldTry',
  ZIYNET: 'ziynetGoldTry',
  CUMHUR: 'republicGoldTry',
  '14AYAR': 'fourteenKBraceletTry',
  '22AYAR': 'twentyTwoKBraceletTry',
};

// ── Chart noktalarını tab'a göre dönüştür ─────────────────────────────────────
function buildDisplayPoints(historyPoints, activeTab) {
  if (!historyPoints?.length || !activeTab) return [];

  const isUsd = activeTab.currency === 'USD';

  return historyPoints.map(p => {
    if (isUsd) {
      // ONS: close/high/low/open doğrudan USD
      const close = parseFloat(p.close);
      const wa    = p.weightedAverageUsdOns != null ? parseFloat(p.weightedAverageUsdOns) : close;
      return {
        ...p,
        displayClose: isNaN(close) ? null : close,
        displayOpen:  p.open  != null ? parseFloat(p.open)  : null,
        displayHigh:  p.high  != null ? parseFloat(p.high)  : null,
        displayLow:   p.low   != null ? parseFloat(p.low)   : null,
        displayWa:    isNaN(wa) ? null : wa,
      };
    }

    // TRY: gram veya teorik hesap
    const baseGram = p.weightedAverage != null ? parseFloat(p.weightedAverage) : parseFloat(p.close);
    const closeGram = parseFloat(p.close);
    const highGram  = p.high != null ? parseFloat(p.high) : null;
    const lowGram   = p.low  != null ? parseFloat(p.low)  : null;
    const openGram  = p.open != null ? parseFloat(p.open) : null;

    if (activeTab.key === 'gram') {
      // Gram: doğrudan BIST gram değerleri
      return {
        ...p,
        displayClose: isNaN(closeGram) ? null : closeGram,
        displayOpen:  openGram,
        displayHigh:  highGram,
        displayLow:   lowGram,
        displayWa:    isNaN(baseGram) ? null : baseGram,
      };
    }

    // Teorik ürünler: weightedAverage üzerinden hesapla
    const { grossWeight, fineness } = activeTab;
    const price = calcTheoreticalPrice(baseGram, grossWeight, fineness);
    const priceClose = calcTheoreticalPrice(closeGram, grossWeight, fineness);
    const priceHigh  = highGram != null ? calcTheoreticalPrice(highGram, grossWeight, fineness) : null;
    const priceLow   = lowGram  != null ? calcTheoreticalPrice(lowGram,  grossWeight, fineness) : null;
    const priceOpen  = openGram != null ? calcTheoreticalPrice(openGram, grossWeight, fineness) : null;

    return {
      ...p,
      displayClose: priceClose,
      displayOpen:  priceOpen,
      displayHigh:  priceHigh,
      displayLow:   priceLow,
      displayWa:    price,
    };
  });
}

// ── Ana sayfa ─────────────────────────────────────────────────────────────────
export default function GoldPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const [spot,         setSpot]         = useState(null);
  const [history,      setHistory]      = useState(null);
  const [activeTabKey, setActiveTabKey] = useState('ons');
  const [range,        setRange]        = useState('1M');
  const [chartMode,    setChartMode]    = useState('line');
  const [loadingSpot,  setLoadingSpot]  = useState(true);
  const [loadingChart, setLoadingChart] = useState(false);
  const [error,        setError]        = useState('');

  // İndikatör state'leri (MA20/50/200 — tüm detay grafiklerinde ortak)
  const [showMA20,  setShowMA20]  = useState(false);
  const [showMA50,  setShowMA50]  = useState(false);
  const [showMA200, setShowMA200] = useState(false);
  const [showTrend, setShowTrend] = useState(false);

  const activeTab = GOLD_TABS.find(tb => tb.key === activeTabKey) ?? GOLD_TABS[0];

  // İzleme listesi vb. deep link: /market/gold?tab=gram
  useEffect(() => {
    const tabParam = searchParams.get('tab');
    if (tabParam && GOLD_TABS.some(x => x.key === tabParam)) {
      setActiveTabKey(tabParam);
    }
  }, [searchParams]);

  // ── Spot ────────────────────────────────────────────────────────────────────
  useEffect(() => {
    setLoadingSpot(true);
    getGoldSpot()
      .then(setSpot)
      .catch(() => setError(t('Altın verisi alınamadı.')))
      .finally(() => setLoadingSpot(false));
  }, []);

  // ── History ───────────────────────────────────────────────────────────────
  const loadHistory = useCallback(() => {
    setLoadingChart(true);
    const currency = activeTab.currency; // 'USD' veya 'TRY'

    // Backend zaten ALL/5Y için AU endpoint'ini, diğerleri için veri-sorgulama'yı kullanıyor
    // Mum grafik için her zaman getGoldHistory (OHLC destekli)
    // Çizgi grafik ALL/5Y için de getGoldHistory — backend AU endpoint'ine yönlendirir
    getGoldHistory(range, currency)
      .then(setHistory)
      .catch(() => setHistory(null))
      .finally(() => setLoadingChart(false));
  }, [range, activeTab.currency, activeTab.key]);

  useEffect(() => { loadHistory(); }, [loadHistory]);

  // Range değişince 5Y/ALL'da mum modunu kapat
  function handleRangeChange(newRange) {
    setRange(newRange);
    if (newRange === '5Y' || newRange === 'ALL') {
      setChartMode('line');
    }
  }

  // Tab değişimi
  function handleTabChange(tabKey) {
    setActiveTabKey(tabKey);
    const tab = GOLD_TABS.find(tb => tb.key === tabKey);
    // Mum grafik sadece ons ve gram'da var, ve 5Y/ALL range'de değil
    if (!tab?.canCandle || range === '5Y' || range === 'ALL') setChartMode('line');
  }

  // ── Display noktaları ─────────────────────────────────────────────────────
  const displayPoints = useMemo(
    () => buildDisplayPoints(history?.points, activeTab),
    [history?.points, activeTab]
  );

  // Grafik yönü (son - ilk)
  const trendItem = useMemo(
    () => buildTrendItem(displayPoints.map(p => parseFloat(p.displayClose)), 'GOLD'),
    [displayPoints],
  );
  const isDown = useMemo(() => {
    if (!displayPoints.length) return false;
    const first = parseFloat(displayPoints[0].displayClose ?? 0);
    const last  = parseFloat(displayPoints[displayPoints.length - 1].displayClose ?? 0);
    return last < first;
  }, [displayPoints]);

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">{t('Altın')}</h1>

      {/* Ana kart */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <GoldTabs activeTab={activeTabKey} onTabChange={handleTabChange} />

        <div className="p-6">
          {loadingSpot ? (
            <GoldLoadingState />
          ) : error ? (
            <GoldErrorState message={error} />
          ) : spot ? (
            <>
              {/* Başlık */}
              <GoldHeaderCard
                spot={spot}
                activeTab={activeTab}
                historyPoints={displayPoints}
              />

              {/* Bilgiler (geniş) + Çevirici (dar) */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 items-start">
                <div className="lg:col-span-2 min-w-0">
                  <GoldPriceStats
                    spot={spot}
                    activeTab={activeTab}
                    historyPoints={displayPoints}
                  />
                </div>
                <GoldCalculator spot={spot} />
              </div>

              {/* Trend rozeti + Karşılaştır (sola hizalı — sağdaki zaman filtresinden ayrı) */}
              <div className="flex items-center gap-3 flex-wrap mb-4 mt-3">
                {trendItem && (
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-gray-400 font-medium">{t('Trend:')}</span>
                    <TrendBadge item={trendItem} size="sm" />
                  </div>
                )}
                <div className="flex items-center gap-2 flex-wrap">
                  <PreciousCompareButton />
                  <UniversalCompareButton
                    assetType="GOLD"
                    symbol={activeTab.compareSymbol}
                    name={t(activeTab.label)}
                  />
                  <InstrumentActionButtons
                    assetType="GOLD"
                    symbol={activeTab.compareSymbol}
                    name={t(activeTab.label)}
                    price={spot?.[GOLD_SPOT_FIELD_BY_SYMBOL[activeTab.compareSymbol] ?? 'gramGoldTry']}
                  />
                </div>
              </div>

              {/* Grafik toolbar */}
              <GoldChartToolbar
                activeTab={activeTab}
                range={range}
                onRangeChange={handleRangeChange}
                chartMode={chartMode}
                onChartModeChange={setChartMode}
                loading={loadingChart}
                onRefresh={loadHistory}
              />

              {/* Grafik (tam genişlik) */}
              <GoldChart
                key={`${activeTabKey}-${range}-${chartMode}`}
                points={displayPoints}
                chartMode={activeTab.canCandle ? chartMode : 'line'}
                isDown={isDown}
                loading={loadingChart}
                showMA20={showMA20}   onToggleMA20={() => setShowMA20(v => !v)}
                showMA50={showMA50}   onToggleMA50={() => setShowMA50(v => !v)}
                showMA200={showMA200} onToggleMA200={() => setShowMA200(v => !v)}
                showTrend={showTrend} onToggleTrend={() => setShowTrend(v => !v)}
                dataLen={displayPoints.length}
                currency={activeTab.currency}
                height={340}
              />

              {/* Kaynak uyarısı */}
              <div className="mt-4">
                <GoldSourceNotice
                  source={history?.source ?? spot?.source}
                  official={history?.official ?? spot?.official}
                  fallback={history?.fallback ?? spot?.fallback}
                  disclaimer={history?.disclaimer ?? spot?.disclaimer}
                />
              </div>

              {/* Teorik referans fiyatlar — grafiğin altında */}
              <div className="mt-5">
                <GoldTheoreticalPricesTable spot={spot} />
              </div>
            </>
          ) : null}
        </div>
      </div>
    </div>
  );
}
