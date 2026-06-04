import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BarChart3 } from 'lucide-react';
import DashCard from './DashCard';
import { num } from '../utils/dashUtils';
import { useTranslation } from '../../../context/LanguageContext';
import { getVolumeLeaders } from '../../../api/marketApi';
import AssetIcon from '../../../components/instrument/AssetIcon';

const ROUTE_SEG = { CRYPTO: 'crypto', STOCK: 'stocks', FX: 'fx', COMMODITY: 'commodities' };

/** Büyük hacmi kompakt yazar: 1.234.567.890 → "1,23 Mr", 8.500.000 → "8,50 Mn". */
function fmtVolume(v, currency) {
  const n = num(v);
  const sym = currency === 'TRY' ? '₺' : currency === 'USD' ? '$' : '';
  const abs = Math.abs(n);
  let out;
  if (abs >= 1e12) out = `${(n / 1e12).toLocaleString('tr-TR', { maximumFractionDigits: 2 })} Tr`;
  else if (abs >= 1e9) out = `${(n / 1e9).toLocaleString('tr-TR', { maximumFractionDigits: 2 })} Mr`;
  else if (abs >= 1e6) out = `${(n / 1e6).toLocaleString('tr-TR', { maximumFractionDigits: 2 })} Mn`;
  else if (abs >= 1e3) out = `${(n / 1e3).toLocaleString('tr-TR', { maximumFractionDigits: 1 })} B`;
  else out = n.toLocaleString('tr-TR', { maximumFractionDigits: 0 });
  return `${sym}${out}`;
}

function LeaderRow({ m, navigate }) {
  const seg = ROUTE_SEG[m.type] || 'stocks';
  const to = `/market/${seg}/${encodeURIComponent(m.id)}`;
  return (
    <button
      onClick={() => navigate(to)}
      className="w-full flex items-center gap-2 py-1.5 px-1 rounded-lg hover:bg-gray-50 transition-colors text-left"
    >
      <AssetIcon assetType={m.type} symbol={m.id} name={m.name} image={m.image} size={20} />
      <span className="text-xs font-semibold text-gray-800 flex-1 min-w-0 truncate">{m.symbol}</span>
      <span className="text-xs font-bold tabular-nums shrink-0 text-[#093eaa]">
        {fmtVolume(m.volume, m.currency)}
      </span>
    </button>
  );
}

/**
 * Hacim Liderleri — günlük işlem hacmi en yüksek enstrümanlar (tüm piyasa, portföyden bağımsız).
 * Sekmeli: Kripto / BIST Hisse / Emtia. Her tip kendi içinde hacme göre sıralanır
 * (birimler farklı: hisse/kripto TL, emtia USD). Kendi verisini çeker (/api/v1/market/volume-leaders).
 */
export default function VolumeLeadersCard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [cats, setCats] = useState([]);
  const [active, setActive] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    getVolumeLeaders(5)
      .then(list => { if (!cancelled) setCats(Array.isArray(list) ? list : []); })
      .catch(() => {})
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const cat = cats[active];
  const leaders = cat?.gainers ?? [];
  const empty = leaders.length === 0;

  return (
    <DashCard title={t('Hacim Liderleri')} icon={BarChart3} accent="#093eaa" scroll>
      {cats.length > 0 && (
        <div className="flex flex-wrap gap-1 mb-3">
          {cats.map((c, i) => (
            <button
              key={c.key}
              onClick={() => setActive(i)}
              className={`px-2.5 py-1 rounded-lg text-xs font-bold transition-colors ${
                i === active ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {t(c.label)}
            </button>
          ))}
        </div>
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
        <div>
          <div className="flex items-center gap-1.5 mb-1 text-[11px] font-bold text-[#093eaa] uppercase tracking-wider">
            <BarChart3 className="w-3.5 h-3.5" /> {t('En Yüksek Günlük Hacim')}
          </div>
          <div className="divide-y divide-gray-50">
            {leaders.map(m => <LeaderRow key={`v-${m.type}-${m.id}`} m={m} navigate={navigate} />)}
          </div>
        </div>
      )}
    </DashCard>
  );
}
