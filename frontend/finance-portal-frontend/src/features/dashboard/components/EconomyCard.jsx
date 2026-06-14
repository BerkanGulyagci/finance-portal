import { Activity, Percent } from 'lucide-react';
import DashCard, { CardLink } from './DashCard';
import { fmtPct, pctClass, num } from '../utils/dashUtils';
import { useTranslation } from '../../../context/LanguageContext';

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
 * Ekonomi göstergeleri — Enflasyon (TÜFE yıllık), Çekirdek Enflasyon, Politika Faizi, ABD CPI.
 * `eco`: { inflation, coreInflation, policyRate, usCpi }
 */
export default function EconomyCard({ eco }) {
  const { t } = useTranslation();
  const has = v => v != null && Number.isFinite(num(v));

  return (
    <DashCard title={t('Ekonomi Göstergeleri')} icon={Activity} accent="#7c3aed" scroll
      action={<CardLink to="/market/economy">{t('Tümü')}</CardLink>}>
      <div className="divide-y divide-gray-50">
        <IndicatorRow icon={Activity} tone="bg-rose-50 text-rose-600"
          label={t('Enflasyon (TÜFE, yıllık)')}
          value={has(eco?.inflation) ? `%${num(eco.inflation).toFixed(2)}` : '—'} />
        <IndicatorRow icon={Activity} tone="bg-orange-50 text-orange-600"
          label={t('Çekirdek Enflasyon')}
          value={has(eco?.coreInflation) ? `%${num(eco.coreInflation).toFixed(2)}` : '—'} />
        <IndicatorRow icon={Percent} tone="bg-amber-50 text-amber-600"
          label={t('Politika Faizi')}
          value={has(eco?.policyRate) ? `%${num(eco.policyRate).toFixed(2)}` : '—'} />
        <IndicatorRow icon={Activity} tone="bg-blue-50 text-blue-600"
          label={t('ABD Enflasyonu (CPI)')}
          value={has(eco?.usCpi) ? `%${num(eco.usCpi).toFixed(2)}` : '—'} />
      </div>
    </DashCard>
  );
}
