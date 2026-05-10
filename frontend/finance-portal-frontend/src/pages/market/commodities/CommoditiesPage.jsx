import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { getCommodityList, getCommoditySpot, getFxTcmb } from '../../../api/marketApi';
import { CATEGORY_ORDER } from './components/commodityConstants';
import CommoditiesHeader from './components/CommoditiesHeader';
import CommoditiesSourceNotice from './components/CommoditiesSourceNotice';
import CommodityCategorySection from './components/CommodityCategorySection';
import CommoditiesLoadingState from './components/CommoditiesLoadingState';
import CommoditiesErrorState from './components/CommoditiesErrorState';

export default function CommoditiesPage() {
  const navigate = useNavigate();

  const [commodities,   setCommodities]   = useState([]);
  const [spots,         setSpots]         = useState({});
  const [usdTryRate,    setUsdTryRate]    = useState(null);
  const [loadingList,   setLoadingList]   = useState(true);
  const [loadingSpots,  setLoadingSpots]  = useState(false);
  const [error,         setError]         = useState(null);

  // İlk yükleme: liste + kur paralel
  useEffect(() => {
    setLoadingList(true);
    Promise.all([
      getCommodityList(),
      getFxTcmb(),
    ])
      .then(([list, fxData]) => {
        setCommodities(list);

        // TCMB'den USD/TRY satış kurunu al
        const usdRate = fxData?.rates?.find(r => r.symbol === 'USD');
        if (usdRate?.sell) setUsdTryRate(parseFloat(usdRate.sell));

        setLoadingList(false);
        fetchAllSpots(list);
      })
      .catch(() => {
        setError('Emtia listesi alınamadı.');
        setLoadingList(false);
      });
  }, []);

  async function fetchAllSpots(list) {
    setLoadingSpots(true);
    const results = await Promise.allSettled(
      list.map(c =>
        getCommoditySpot(c.symbol).then(s => ({ symbol: c.symbol, spot: s }))
      )
    );
    const map = {};
    results.forEach(r => {
      if (r.status === 'fulfilled' && r.value?.spot) {
        map[r.value.symbol] = r.value.spot;
      }
    });
    setSpots(map);
    setLoadingSpots(false);
  }

  function handleRefresh() {
    if (commodities.length) fetchAllSpots(commodities);
  }

  function handleCardClick(symbol) {
    navigate(`/market/commodities/${encodeURIComponent(symbol)}`);
  }

  // Kategoriye göre grupla
  const grouped = {};
  commodities.forEach(c => {
    if (!grouped[c.category]) grouped[c.category] = [];
    grouped[c.category].push(c);
  });

  if (loadingList) return <CommoditiesLoadingState />;
  if (error)       return <CommoditiesErrorState message={error} />;

  return (
    <div className="space-y-6 w-full">
      <CommoditiesHeader loading={loadingSpots} onRefresh={handleRefresh} usdTryRate={usdTryRate} />
      <CommoditiesSourceNotice />

      {CATEGORY_ORDER.filter(cat => grouped[cat]?.length).map(cat => (
        <CommodityCategorySection
          key={cat}
          category={cat}
          items={grouped[cat]}
          spots={spots}
          loadingSpots={loadingSpots}
          usdTryRate={usdTryRate}
          onCardClick={handleCardClick}
        />
      ))}
    </div>
  );
}
