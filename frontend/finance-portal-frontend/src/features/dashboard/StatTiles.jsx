import { Wallet, TrendingUp, TrendingDown, PieChart } from 'lucide-react';
import { fmtMoney, fmtPct, pctClass, num, ASSET_LABEL } from './dashUtils';
import { useTranslation } from '../../i18n/LanguageContext';

function Tile({ icon: Icon, tone, label, value, valueClass = 'text-gray-900', sub, subClass = 'text-gray-400' }) {
  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 flex items-center gap-3 hover:shadow-md transition-shadow min-w-0">
      <div className={`w-11 h-11 rounded-2xl flex items-center justify-center shrink-0 ${tone}`}>
        <Icon className="w-5 h-5" />
      </div>
      <div className="min-w-0">
        <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide truncate">{label}</p>
        <p className={`text-lg font-black tabular-nums leading-tight truncate ${valueClass}`}>{value}</p>
        {sub != null && <p className={`text-[11px] font-semibold truncate ${subClass}`}>{sub}</p>}
      </div>
    </div>
  );
}

/**
 * Üst alan istatistik kartları: Toplam Değer, Günlük K/Z, Toplam Getiri, Risk/Dağılım.
 */
export default function StatTiles({
  totalValue, dailyPnl, dailyPct, totalPnl, totalPct,
  topType, topPct, typeCount, portfolioCount,
}) {
  const { t } = useTranslation();
  const dailyUp = num(dailyPnl) >= 0;
  const totalUp = num(totalPnl) >= 0;

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 m3-stagger">
      <Tile
        icon={Wallet}
        tone="bg-[#093eaa]/10 text-[#093eaa]"
        label={t('Toplam Değer')}
        value={`₺${fmtMoney(totalValue)}`}
        sub={`${portfolioCount} ${t('portföy')}`}
      />
      <Tile
        icon={dailyUp ? TrendingUp : TrendingDown}
        tone={dailyUp ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'}
        label={t('Günlük K/Z')}
        value={`${dailyUp ? '+' : ''}₺${fmtMoney(dailyPnl)}`}
        valueClass={pctClass(dailyPnl)}
        sub={fmtPct(dailyPct)}
        subClass={pctClass(dailyPct)}
      />
      <Tile
        icon={totalUp ? TrendingUp : TrendingDown}
        tone={totalUp ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'}
        label={t('Toplam Getiri')}
        value={`${totalUp ? '+' : ''}₺${fmtMoney(totalPnl)}`}
        valueClass={pctClass(totalPnl)}
        sub={fmtPct(totalPct)}
        subClass={pctClass(totalPct)}
      />
      <Tile
        icon={PieChart}
        tone="bg-violet-50 text-violet-600"
        label={t('Risk / Dağılım')}
        value={topType ? `${t(ASSET_LABEL[topType] ?? topType)} %${topPct}` : '—'}
        sub={`${typeCount} ${t('varlık türü')}`}
      />
    </div>
  );
}
