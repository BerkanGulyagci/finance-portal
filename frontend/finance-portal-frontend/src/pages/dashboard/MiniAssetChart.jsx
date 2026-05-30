import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { X, TrendingUp, TrendingDown } from 'lucide-react';
import { getMarketPriceHistory } from '../../api/marketApi';
import { getWatchlistDetailPath } from '../portfolio';
import { CardLink } from './DashCard';
import { useTranslation } from '../../i18n/LanguageContext';

const ASSET_LABEL = {
  STOCK: 'Hisse', FUND: 'Fon', FX: 'Döviz', FUTURE: 'Vadeli',
  CRYPTO: 'Kripto', GOLD: 'Altın', COMMODITY: 'Emtia', BOND: 'Tahvil',
};

function sparkPath(values, w, h, pad = 3) {
  if (values.length < 2) return '';
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const step = (w - 2 * pad) / (values.length - 1);
  return values.map((v, i) => {
    const x = pad + i * step;
    const y = h - pad - ((v - min) / range) * (h - 2 * pad);
    return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`;
  }).join(' ');
}

/**
 * Dashboard'a eklenebilen mini varlık grafiği widget'ı. Genel price-history endpoint'inden
 * 3 aylık kapanış serisini çekip küçük bir SVG çizgi grafik + son fiyat + dönem değişimi gösterir.
 */
export default function MiniAssetChart({ assetType, symbol, name, owner, ownerId, onRemove }) {
  const { t } = useTranslation();
  const [closes, setCloses] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getMarketPriceHistory(assetType, symbol, '3M')
      .then(d => {
        if (cancelled) return;
        setCloses((d?.closePrices ?? []).map(Number).filter(v => Number.isFinite(v) && v > 0));
      })
      .catch(() => { if (!cancelled) setCloses([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [assetType, symbol]);

  const last = closes[closes.length - 1];
  const first = closes[0];
  const pct = first ? ((last - first) / first) * 100 : null;
  const up = (pct ?? 0) >= 0;
  const color = up ? '#10b981' : '#ef4444';
  const W = 280, H = 56;
  const detailPath = getWatchlistDetailPath?.(assetType, symbol);

  return (
    <div className="relative bg-white rounded-2xl border border-gray-200 shadow-sm p-3.5 min-w-0 h-full flex flex-col">
      <div className="flex items-start justify-between gap-2 shrink-0">
        <div className="min-w-0 pr-6">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-bold text-gray-900 truncate text-sm">{name || symbol}</span>
            <span className="text-[10px] font-semibold text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded">
              {t(ASSET_LABEL[assetType] ?? assetType)}
            </span>
          </div>
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-[11px] text-gray-400">{symbol}</span>
            {owner && (
              ownerId ? (
                <Link to={`/portfolio/${ownerId}`}
                  className="inline-flex items-center text-[10px] font-semibold text-[#093eaa] bg-[#093eaa]/5 px-1.5 py-0.5 rounded hover:bg-[#093eaa]/10 transition-colors">
                  {owner}
                </Link>
              ) : (
                <span className="inline-flex items-center text-[10px] font-semibold text-[#093eaa] bg-[#093eaa]/5 px-1.5 py-0.5 rounded">
                  {owner}
                </span>
              )
            )}
          </div>
        </div>
        <button
          onClick={onRemove}
          title={t('Kaldır')}
          className="absolute top-2 right-2 z-20 p-1 rounded-md text-gray-300 hover:text-rose-500 hover:bg-rose-50 transition-colors"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      {loading ? (
        <div className="flex-1 flex items-center justify-center"><p className="text-sm text-gray-400">{t('Yükleniyor...')}</p></div>
      ) : closes.length < 2 ? (
        <div className="flex-1 flex items-center justify-center"><p className="text-sm text-gray-400">{t('Veri bulunamadı.')}</p></div>
      ) : (
        <>
          <div className="flex items-baseline gap-2 mt-1.5 shrink-0">
            <span className="text-lg font-black text-gray-900 tabular-nums">
              {last.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}
            </span>
            {pct != null && (
              <span className={`text-xs font-bold flex items-center gap-0.5 ${up ? 'text-emerald-600' : 'text-rose-600'}`}>
                {up ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                {up ? '+' : ''}{pct.toFixed(2)}% · 3A
              </span>
            )}
          </div>
          <div className="flex-1 min-h-0 mt-1.5">
            <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="w-full h-full">
              <path d={sparkPath(closes, W, H)} fill="none" stroke={color} strokeWidth="1.6" />
            </svg>
          </div>
          {detailPath && <div className="shrink-0 mt-1"><CardLink to={detailPath}>{t('Detay')}</CardLink></div>}
        </>
      )}
    </div>
  );
}
