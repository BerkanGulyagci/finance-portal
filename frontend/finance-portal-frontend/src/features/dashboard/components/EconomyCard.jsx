import { Activity, Percent, DollarSign, BarChart3 } from 'lucide-react';
import DashCard, { CardLink } from './DashCard';
import { fmtMoney, fmtPct, pctClass, num } from '../utils/dashUtils';
import { useTranslation } from '../../../i18n/LanguageContext';

function IndicatorRow({ icon: Icon, tone, label, value, changePct }) {
  return (
    <div className="flex items-center gap-3 py-2">
      <span className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 ${tone}`}>
        <Icon className="w-4 h-4" />
      </span>
      <span className="text-sm font-medium text-gray-700 flex-1 min-w-0 truncate">{label}</span>
      <span className="text-right shrink-0">
        <span className="block text-sm font-bold text-gray-900 tabular-nums">{value}</span>
        {changePct != null && (
          <span className={`block text-[11px] font-semibold tabular-nums ${pctClass(changePct)}`}>{fmtPct(changePct)}</span>
        )}
      </span>
    </div>
  );
}

/**
 * Ekonomi göstergeleri — Enflasyon (TÜFE yıllık), Politika Faizi, Dolar/TL, BIST 100.
 * `eco`: { inflation, policyRate, usdTry, usdChange, bist100, bistChange }
 */
export default function EconomyCard({ eco }) {
  const { t } = useTranslation();
  const has = v => v != null && Number.isFinite(num(v));

  return (
    <DashCard title={t('Ekonomi Göstergeleri')} icon={Activity} accent="#7c3aed"
      action={<CardLink to="/market/economy">{t('Tümü')}</CardLink>}>
      <div className="divide-y divide-gray-50">
        <IndicatorRow icon={Activity} tone="bg-rose-50 text-rose-600"
          label={t('Enflasyon (TÜFE, yıllık)')}
          value={has(eco?.inflation) ? `%${num(eco.inflation).toFixed(2)}` : '—'} />
        <IndicatorRow icon={Percent} tone="bg-amber-50 text-amber-600"
          label={t('Politika Faizi')}
          value={has(eco?.policyRate) ? `%${num(eco.policyRate).toFixed(2)}` : '—'} />
        <IndicatorRow icon={DollarSign} tone="bg-emerald-50 text-emerald-600"
          label={t('Dolar/TL')}
          value={has(eco?.usdTry) ? `₺${fmtMoney(eco.usdTry, { min: 4, max: 4 })}` : '—'}
          changePct={has(eco?.usdChange) ? num(eco.usdChange) : null} />
        <IndicatorRow icon={BarChart3} tone="bg-[#093eaa]/10 text-[#093eaa]"
          label={t('BIST 100')}
          value={has(eco?.bist100) ? num(eco.bist100).toLocaleString('tr-TR', { maximumFractionDigits: 0 }) : '—'}
          changePct={has(eco?.bistChange) ? num(eco.bistChange) : null} />
      </div>
    </DashCard>
  );
}
