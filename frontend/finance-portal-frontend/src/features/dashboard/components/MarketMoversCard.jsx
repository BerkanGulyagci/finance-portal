import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TrendingUp, TrendingDown, Flame } from 'lucide-react';
import DashCard from './DashCard';
import CardTabs from './CardTabs';
import { fmtPct, pctClass, num } from '../utils/dashUtils';
import { useTranslation } from '../../../context/LanguageContext';
import { getMarketMovers } from '../../../api/marketApi';
import AssetIcon from '../../../components/instrument/AssetIcon';

const ROUTE_SEG = { CRYPTO: 'crypto', STOCK: 'stocks', FX: 'fx', COMMODITY: 'commodities' };

function MoverRow({ m, navigate }) {
  const seg = ROUTE_SEG[m.type] || 'stocks';
  const to = `/market/${seg}/${encodeURIComponent(m.id)}`;
  return (
    <button
      onClick={() => navigate(to)}
      className="w-full flex items-center gap-2 py-1.5 px-1 rounded-lg hover:bg-gray-50 transition-colors text-left"
    >
      {/* kripto→logo, hisse→Midas, döviz→bayrak (aynı); emtia→kaliteli lucide ikon */}
      <AssetIcon assetType={m.type} symbol={m.id} name={m.name} image={m.image} size={20} />
      <span className="text-xs font-semibold text-gray-800 flex-1 min-w-0 truncate">{m.symbol}</span>
      <span className={`text-xs font-bold tabular-nums shrink-0 ${pctClass(num(m.changePercent))}`}>
        {fmtPct(num(m.changePercent))}
      </span>
    </button>
  );
}

/**
 * Piyasanın Hareketlileri — günün en çok yükselen/düşen enstrümanları (tüm piyasa, sahip olunanlardan bağımsız).
 * Sekmeli: Kripto / BIST Hisse. Kendi verisini çeker (/api/v1/market/movers).
 */
export default function MarketMoversCard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [cats, setCats] = useState([]);
  const [active, setActive] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    getMarketMovers(5)
      .then(list => { if (!cancelled) setCats(Array.isArray(list) ? list : []); })
      .catch(() => {})
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const cat = cats[active];
  const empty = !cat || ((cat.gainers?.length ?? 0) === 0 && (cat.losers?.length ?? 0) === 0);

  return (
    <DashCard title={t('Piyasanın Hareketlileri')} icon={Flame} accent="#ef4444" scroll>
      {cats.length > 0 && (
        <CardTabs tabs={cats} active={active} onChange={setActive} t={t} accent="#ef4444" />
      )}

      {loading ? (
        <div className="py-6 flex justify-center gap-1.5">
          <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
          <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
          <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
        </div>
      ) : empty ? (
        <div className="py-6 text-center text-xs text-gray-400">{t('Veri yok')}</div>
      ) : (
        <div className="space-y-3">
          <div>
            <div className="flex items-center gap-1.5 mb-1 text-[11px] font-bold text-emerald-600 uppercase tracking-wider">
              <TrendingUp className="w-3.5 h-3.5" /> {t('En Çok Yükselenler')}
            </div>
            <div className="divide-y divide-gray-50">
              {(cat.gainers ?? []).map(m => <MoverRow key={`g-${m.type}-${m.id}`} m={m} navigate={navigate} />)}
              {(cat.gainers?.length ?? 0) === 0 && (
                <p className="text-[11px] text-gray-400 py-2 px-1 italic">{t('Bugün yükselen yok')}</p>
              )}
            </div>
          </div>
          <div>
            <div className="flex items-center gap-1.5 mb-1 text-[11px] font-bold text-rose-600 uppercase tracking-wider">
              <TrendingDown className="w-3.5 h-3.5" /> {t('En Çok Düşenler')}
            </div>
            <div className="divide-y divide-gray-50">
              {(cat.losers ?? []).map(m => <MoverRow key={`l-${m.type}-${m.id}`} m={m} navigate={navigate} />)}
              {(cat.losers?.length ?? 0) === 0 && (
                <p className="text-[11px] text-gray-400 py-2 px-1 italic">{t('Bugün düşen yok')}</p>
              )}
            </div>
          </div>
        </div>
      )}
    </DashCard>
  );
}
