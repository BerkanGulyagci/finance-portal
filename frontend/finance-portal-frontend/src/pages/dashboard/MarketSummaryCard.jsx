import { Link } from 'react-router-dom';
import { LineChart } from 'lucide-react';
import DashCard from './DashCard';
import { fmtMoney, fmtPct, pctClass, num } from './dashUtils';
import { getWatchlistDetailPath } from '../portfolio/constants/watchlistMarketRoutes';
import { useTranslation } from '../../i18n/LanguageContext';

function MarketRow({ label, sub, value, changePct, to }) {
  const inner = (
    <>
      <span className="min-w-0">
        <span className="block text-sm font-semibold text-gray-900 truncate">{label}</span>
        {sub && <span className="block text-[11px] text-gray-400 truncate">{sub}</span>}
      </span>
      <span className="text-right shrink-0">
        <span className="block text-sm font-bold text-gray-800 tabular-nums">{value}</span>
        {changePct != null && (
          <span className={`block text-[11px] font-semibold tabular-nums ${pctClass(changePct)}`}>{fmtPct(changePct)}</span>
        )}
      </span>
    </>
  );
  return to
    ? <Link to={to} className="flex items-center justify-between gap-2 py-2 -mx-1 px-1 rounded-lg hover:bg-gray-50 transition-colors">{inner}</Link>
    : <div className="flex items-center justify-between gap-2 py-2">{inner}</div>;
}

/**
 * Piyasa özeti — döviz + altın kompakt satırlar (kripto piyasası tarzı).
 */
export default function MarketSummaryCard({ fx, gold }) {
  const { t } = useTranslation();
  const rates = fx?.rates ?? [];
  const find = sym => rates.find(r => r.symbol === sym);
  const rows = ['USD', 'EUR', 'GBP'].map(sym => {
    const r = find(sym);
    return r ? {
      label: `${sym}/TRY`,
      to: getWatchlistDetailPath('FX', sym),
      value: `₺${fmtMoney(r.sell, { min: 4, max: 4 })}`,
      changePct: r.changePercent != null ? num(r.changePercent) : null,
    } : null;
  }).filter(Boolean);

  return (
    <DashCard title={t('Piyasa Özeti')} icon={LineChart} to="/market" toLabel={t('Tümü →')}>
      <div className="divide-y divide-gray-50">
        {rows.map(r => <MarketRow key={r.label} {...r} />)}
        {gold?.price && (
          <MarketRow
            label={t('Altın/Ons')}
            value={`₺${fmtMoney(gold.price)}`}
            changePct={gold.changePercent != null ? num(gold.changePercent) : null}
            to={getWatchlistDetailPath('GOLD', 'ONS')}
          />
        )}
        {rows.length === 0 && !gold?.price && (
          <p className="text-sm text-gray-400 py-6 text-center">{t('Veri yükleniyor...')}</p>
        )}
      </div>
    </DashCard>
  );
}
