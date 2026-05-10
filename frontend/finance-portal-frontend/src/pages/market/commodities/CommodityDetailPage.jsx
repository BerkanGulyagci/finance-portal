import { useState, useEffect, useCallback, useMemo } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { getCommoditySpot, getCommodityHistory, getFxTcmb } from '../../../api/marketApi';
import { getPreciousMetalMarketPath } from '../../portfolio/constants/watchlistMarketRoutes';
import CommodityDetailHeader      from './components/CommodityDetailHeader';
import CommodityDetailOhlcCards   from './components/CommodityDetailOhlcCards';
import CommodityDetailToolbar     from './components/CommodityDetailToolbar';
import CommodityDetailChart       from './components/CommodityDetailChart';
import CommodityDetailSourceNotice from './components/CommodityDetailSourceNotice';
import CommoditiesErrorState      from './components/CommoditiesErrorState';

// history noktalarını display değerleriyle zenginleştir (USX → USD dönüşümü zaten backend'de yapılıyor)
function enrichPoints(points) {
  if (!points?.length) return [];
  return points.map(p => ({
    ...p,
    displayOpen:  p.displayOpen  ?? p.rawOpen,
    displayHigh:  p.displayHigh  ?? p.rawHigh,
    displayLow:   p.displayLow   ?? p.rawLow,
    displayClose: p.displayClose ?? p.rawClose,
  }));
}

export default function CommodityDetailPage() {
  const { symbol } = useParams();
  // URL'den decode et (CL%3DF → CL=F)
  const decodedSymbol = decodeURIComponent(symbol ?? '');
  const preciousRedirect = useMemo(
    () => (decodedSymbol ? getPreciousMetalMarketPath(decodedSymbol) : null),
    [decodedSymbol],
  );

  const [spot,         setSpot]         = useState(null);
  const [history,      setHistory]      = useState(null);
  const [usdTryRate,   setUsdTryRate]   = useState(null);
  const [showTry,      setShowTry]      = useState(false);
  const [range,        setRange]        = useState('1M');
  const [chartMode,    setChartMode]    = useState('line');
  const [loadingSpot,  setLoadingSpot]  = useState(true);
  const [loadingChart, setLoadingChart] = useState(false);
  const [error,        setError]        = useState(null);

  // İlk yükleme: spot + kur paralel
  useEffect(() => {
    if (!decodedSymbol || preciousRedirect) return;
    setLoadingSpot(true);
    setError(null);

    Promise.all([
      getCommoditySpot(decodedSymbol),
      getFxTcmb(),
    ])
      .then(([spotData, fxData]) => {
        setSpot(spotData);
        const usdRate = fxData?.rates?.find(r => r.symbol === 'USD');
        if (usdRate?.sell) setUsdTryRate(parseFloat(usdRate.sell));
      })
      .catch(() => setError('Emtia verisi alınamadı.'))
      .finally(() => setLoadingSpot(false));
  }, [decodedSymbol, preciousRedirect]);

  // History yükleme
  const loadHistory = useCallback(() => {
    if (!decodedSymbol || preciousRedirect) return;
    setLoadingChart(true);
    getCommodityHistory(decodedSymbol, range)
      .then(setHistory)
      .catch(() => setHistory(null))
      .finally(() => setLoadingChart(false));
  }, [decodedSymbol, preciousRedirect, range]);

  useEffect(() => { loadHistory(); }, [loadHistory]);

  // Range değişince 5Y'de mum kapat
  function handleRangeChange(newRange) {
    setRange(newRange);
  }

  const displayPoints = useMemo(
    () => enrichPoints(history?.points),
    [history?.points]
  );

  // spot'tan meta bilgisi çıkar (backend CommoditySpotDto'dan geliyor)
  const meta = spot ? {
    symbol:        spot.symbol,
    displayNameTr: spot.displayNameTr,
    displayNameEn: spot.displayNameEn,
    category:      spot.category,
    unit:          spot.unit,
  } : null;

  if (preciousRedirect) {
    return (
      <Navigate
        to={`${preciousRedirect.path}?tab=${encodeURIComponent(preciousRedirect.tab)}`}
        replace
      />
    );
  }

  if (!loadingSpot && error) return <CommoditiesErrorState message={error} />;

  return (
    <div className="space-y-5">
      {/* Header: başlık + fiyat + 52H */}
      {loadingSpot ? (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
          <div className="space-y-3">
            <div className="h-6 bg-gray-200 rounded animate-pulse w-48" />
            <div className="h-10 bg-gray-100 rounded animate-pulse w-64" />
            <div className="h-4 bg-gray-100 rounded animate-pulse w-32" />
          </div>
        </div>
      ) : meta && (
        <CommodityDetailHeader
          meta={meta}
          spot={spot}
          usdTryRate={usdTryRate}
          showTry={showTry}
          onToggleCurrency={() => setShowTry(v => !v)}
        />
      )}

      {/* OHLC kartları */}
      {displayPoints.length > 0 && (
        <CommodityDetailOhlcCards points={displayPoints} />
      )}

      {/* Grafik kartı */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
        <CommodityDetailToolbar
          range={range}
          onRangeChange={handleRangeChange}
          chartMode={chartMode}
          onChartModeChange={setChartMode}
          loading={loadingChart}
          onRefresh={loadHistory}
        />

        <CommodityDetailChart
          key={`${range}-${chartMode}`}
          points={displayPoints}
          chartMode={chartMode}
          loading={loadingChart}
        />
      </div>

      {/* Kaynak uyarısı */}
      <CommodityDetailSourceNotice />
    </div>
  );
}
