import { useMemo } from 'react';
import { TrendingUp, TrendingDown } from 'lucide-react';
import { fmt, getSpotPrice } from '../utils/goldConstants';
import { useTranslation } from '../../../../context/LanguageContext';

export default function GoldHeaderCard({ spot, activeTab, historyPoints }) {
  const { t } = useTranslation();

  const isUsd = activeTab?.currency === 'USD';

  // ── Dönem değişimi — history'nin ilk ve son close'undan ──────────────────
  const { changeAmt, changePct } = useMemo(() => {
    if (!historyPoints?.length) {
      // Fallback: ons için spot'tan
      if (isUsd && spot?.onsChangePercent != null) {
        return {
          changePct: parseFloat(spot.onsChangePercent),
          changeAmt: spot.onsChange != null ? parseFloat(spot.onsChange) : null,
        };
      }
      return { changeAmt: null, changePct: null };
    }

    const closes = historyPoints
      .map(p => parseFloat(p.displayClose ?? p.close))
      .filter(v => !isNaN(v) && v > 0);

    if (closes.length < 2) {
      if (isUsd && spot?.onsChangePercent != null) {
        return {
          changePct: parseFloat(spot.onsChangePercent),
          changeAmt: spot.onsChange != null ? parseFloat(spot.onsChange) : null,
        };
      }
      return { changeAmt: null, changePct: null };
    }

    const first = closes[0];
    const last  = closes[closes.length - 1];
    return {
      changeAmt: last - first,
      changePct: ((last - first) / first) * 100,
    };
  }, [historyPoints, isUsd, spot]);

  if (!spot || !activeTab) return null;

  const price = getSpotPrice(spot, activeTab);
  const sym   = isUsd ? '$' : '₺';

  const isDown     = (changePct ?? 0) < 0;
  const priceColor = isDown ? 'text-rose-600' : 'text-emerald-600';

  return (
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
        {spot.fallback && (
          <span className="text-xs font-bold text-amber-700 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-full">
            {t('Fallback')}
          </span>
        )}
      </div>

      <div className="flex items-baseline gap-3 flex-wrap">
        <span className={`text-4xl font-black ${priceColor}`}>
          {sym}{fmt(price)}
        </span>
        {changePct != null && (
          <span className={`flex items-center gap-1 text-sm font-semibold ${priceColor}`}>
            {isDown ? <TrendingDown className="w-4 h-4" /> : <TrendingUp className="w-4 h-4" />}
            {changeAmt != null && `${changeAmt >= 0 ? '+' : ''}${fmt(changeAmt)} `}
            ({changePct >= 0 ? '+' : ''}{fmt(changePct)}%)
          </span>
        )}
      </div>

      {/* Güncelleme zamanı */}
      {(spot.lastUpdated || spot.updatedAt) && (
        <p className="text-xs text-gray-400 mt-1">
          {t('Güncelleme:')}{' '}
          {(() => {
            try {
              const raw = spot.lastUpdated ?? spot.updatedAt;
              return new Date(raw.replace(/\[.*\]$/, '')).toLocaleString('tr-TR');
            } catch {
              return spot.lastUpdated ?? spot.updatedAt;
            }
          })()}
          {spot.bistDate && ` · ${t('BİST:')} ${spot.bistDate}`}
        </p>
      )}
    </div>
  );
}
