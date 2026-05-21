import { useEffect, useState, useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getGoldSpot, getGoldHistory } from '../../../api/marketApi';
import { getGoldNews } from '../../../api/newsApi';

import { GOLD_TABS, calcTheoreticalPrice } from './components/goldConstants';
import GoldTabs                   from './components/GoldTabs';
import GoldLoadingState           from './components/GoldLoadingState';
import GoldErrorState             from './components/GoldErrorState';
import GoldHeaderCard             from './components/GoldHeaderCard';
import GoldPriceStats             from './components/GoldPriceStats';
import GoldChartToolbar           from './components/GoldChartToolbar';
import GoldChart                  from './components/GoldChart';
import GoldSourceNotice           from './components/GoldSourceNotice';
import GoldTheoreticalPricesTable from './components/GoldTheoreticalPricesTable';
import GoldCalculator             from './components/GoldCalculator';
import { useTranslation } from '../../../i18n/LanguageContext';

// ── Haber bileşeni (küçük, burada kalabilir) ──────────────────────────────────
function GoldNews({ news, label }) {
  const { t } = useTranslation();
  const resolvedLabel = label ?? t('İlgili Haberler');
  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
        📰 {resolvedLabel}
      </h2>
      {news.length === 0 ? (
        <p className="text-gray-400 text-sm">{t('Haber yükleniyor...')}</p>
      ) : (
        <div className="space-y-4">
          {news.slice(0, 6).map((item, i) => (
            <a
              key={i}
              href={item.url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex gap-3 group hover:bg-gray-50 rounded-xl p-2 -mx-2 transition-colors"
            >
              {item.imageUrl && (
                <img
                  src={item.imageUrl}
                  alt=""
                  className="w-16 h-16 object-cover rounded-lg flex-shrink-0"
                />
              )}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-gray-800 group-hover:text-[#093eaa] transition-colors line-clamp-2 leading-snug">
                  {item.title}
                </p>
                <p className="text-xs text-gray-400 mt-1">
                  {item.source && <span className="font-semibold">{item.source} · </span>}
                  {item.publishedAt
                    ? new Date(item.publishedAt).toLocaleDateString('tr-TR')
                    : ''}
                </p>
              </div>
            </a>
          ))}
        </div>
      )}
    </div>
  );
}

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
  const [news,         setNews]         = useState([]);
  const [newsLabel,    setNewsLabel]    = useState(t('İlgili Haberler'));

  // İndikatör state'leri
  const [showMA7,   setShowMA7]   = useState(false);
  const [showMA30,  setShowMA30]  = useState(false);
  const [showMA90,  setShowMA90]  = useState(false);
  const [showTrend, setShowTrend] = useState(false);

  const activeTab = GOLD_TABS.find(tb => tb.key === activeTabKey) ?? GOLD_TABS[0];

  // İzleme listesi vb. deep link: /market/gold?tab=gram
  useEffect(() => {
    const tabParam = searchParams.get('tab');
    if (tabParam && GOLD_TABS.some(x => x.key === tabParam)) {
      setActiveTabKey(tabParam);
    }
  }, [searchParams]);

  // ── Spot + haberler ───────────────────────────────────────────────────────
  useEffect(() => {
    setLoadingSpot(true);
    getGoldSpot()
      .then(setSpot)
      .catch(() => setError(t('Altın verisi alınamadı.')))
      .finally(() => setLoadingSpot(false));

    getGoldNews()
      .then(result => {
        setNews(result?.items ?? []);
        setNewsLabel(result?.label ?? t('İlgili Haberler'));
      })
      .catch(() => {});
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

              {/* İstatistikler */}
              <GoldPriceStats
                spot={spot}
                activeTab={activeTab}
                historyPoints={displayPoints}
              />

              {/* Grafik toolbar */}
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

              {/* Grafik */}
              <GoldChart
                key={`${activeTabKey}-${range}-${chartMode}`}
                points={displayPoints}
                chartMode={activeTab.canCandle ? chartMode : 'line'}
                isDown={isDown}
                loading={loadingChart}
                showMA7={showMA7}
                showMA30={showMA30}
                showMA90={showMA90}
                showTrend={showTrend}
                currency={activeTab.currency}
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
            </>
          ) : null}
        </div>
      </div>

      {/* Teorik fiyatlar tablosu */}
      {spot && <GoldTheoreticalPricesTable spot={spot} />}

      {/* Hesaplama + Haberler */}
      {spot && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <GoldCalculator spot={spot} />
          <GoldNews news={news} label={newsLabel} />
        </div>
      )}
    </div>
  );
}
