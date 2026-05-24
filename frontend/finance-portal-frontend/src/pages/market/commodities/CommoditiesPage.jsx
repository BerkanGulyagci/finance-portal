import { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { getCommodityList, getCommoditySpot, getFxTcmb } from '../../../api/marketApi';
import { CATEGORY_ORDER, CATEGORY_META } from './components/commodityConstants';
import CommoditiesHeader from './components/CommoditiesHeader';
import CommoditiesSourceNotice from './components/CommoditiesSourceNotice';
import CommodityCard from './components/CommodityCard';
import CommoditiesLoadingState from './components/CommoditiesLoadingState';
import CommoditiesErrorState from './components/CommoditiesErrorState';
import { useTranslation } from '../../../i18n/LanguageContext';

// M3 tarzı kategori filtre çipi
function CategoryChip({ active, label, count, meta, onClick }) {
  const Icon = meta?.icon;
  return (
    <button
      onClick={onClick}
      className={`inline-flex items-center gap-1.5 pl-2.5 pr-2.5 py-1.5 rounded-full text-xs font-semibold border transition-all ${
        active
          ? 'bg-[#093eaa] text-white border-[#093eaa] shadow-sm'
          : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-50 hover:border-gray-300'
      }`}
    >
      {Icon && <Icon className={`w-3.5 h-3.5 ${active ? 'text-white' : meta.color}`} />}
      {label}
      <span
        className={`text-[10px] font-bold leading-none px-1.5 py-0.5 rounded-full ${
          active ? 'bg-white/20 text-white' : 'bg-gray-100 text-gray-500'
        }`}
      >
        {count}
      </span>
    </button>
  );
}

export default function CommoditiesPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [commodities,   setCommodities]   = useState([]);
  const [spots,         setSpots]         = useState({});
  const [usdTryRate,    setUsdTryRate]    = useState(null);
  const [loadingList,   setLoadingList]   = useState(true);
  const [loadingSpots,  setLoadingSpots]  = useState(false);
  const [error,         setError]         = useState(null);
  const [activeCategory, setActiveCategory] = useState('ALL');

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

  // İlk yükleme: liste + kur paralel (loadingList başlangıçta zaten true)
  useEffect(() => {
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
        setError(t('Emtia listesi alınamadı.'));
        setLoadingList(false);
      });
  }, []);

  function handleRefresh() {
    if (commodities.length) fetchAllSpots(commodities);
  }

  function handleCardClick(symbol) {
    navigate(`/market/commodities/${encodeURIComponent(symbol)}`);
  }

  // Kategori başına adet
  const counts = useMemo(() => {
    const m = {};
    commodities.forEach(c => { m[c.category] = (m[c.category] || 0) + 1; });
    return m;
  }, [commodities]);

  // Kategori sırasına göre tek listede sırala → satırlar her zaman dolu
  const ordered = useMemo(() => {
    const rank = cat => {
      const i = CATEGORY_ORDER.indexOf(cat);
      return i === -1 ? CATEGORY_ORDER.length : i;
    };
    return [...commodities].sort((a, b) => rank(a.category) - rank(b.category));
  }, [commodities]);

  const visible = activeCategory === 'ALL'
    ? ordered
    : ordered.filter(c => c.category === activeCategory);

  if (loadingList) return <CommoditiesLoadingState />;
  if (error)       return <CommoditiesErrorState message={error} />;

  return (
    <div className="space-y-6 w-full">
      <CommoditiesHeader loading={loadingSpots} onRefresh={handleRefresh} usdTryRate={usdTryRate} />
      <CommoditiesSourceNotice />

      {/* Kategori filtreleri */}
      <div className="flex flex-wrap items-center gap-2">
        <CategoryChip
          active={activeCategory === 'ALL'}
          label={t('Tümü')}
          count={commodities.length}
          onClick={() => setActiveCategory('ALL')}
        />
        {CATEGORY_ORDER.filter(cat => counts[cat]).map(cat => (
          <CategoryChip
            key={cat}
            active={activeCategory === cat}
            label={t(CATEGORY_META[cat].label)}
            count={counts[cat]}
            meta={CATEGORY_META[cat]}
            onClick={() => setActiveCategory(cat)}
          />
        ))}
      </div>

      {/* Tek tip ızgara — tüm emtialar, eşit yükseklikte kartlar */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-5 items-stretch">
        {visible.map(meta => (
          <CommodityCard
            key={meta.symbol}
            meta={meta}
            spot={spots[meta.symbol] ?? null}
            loading={loadingSpots && !spots[meta.symbol]}
            usdTryRate={usdTryRate}
            onClick={() => handleCardClick(meta.symbol)}
          />
        ))}
      </div>
    </div>
  );
}
