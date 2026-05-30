import { Globe2 } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';

/**
 * Open Exchange Rates mini kartı — verilen sembol için anlık TRY karşılığı.
 */
export default function OpenRateCard({ sym, tryPerUnit }) {
  const { t } = useTranslation();
  return (
    <section className="bg-white rounded-3xl border border-[#e2e8f0] shadow-[0_1px_3px_rgba(15,23,42,0.06)] p-5">
      <h2 className="font-bold text-[#1a1c1e] mb-3 flex items-center gap-2 text-sm">
        <Globe2 className="w-4 h-4 text-[#093eaa]" /> Open Exchange Rates
      </h2>
      {tryPerUnit != null ? (
        <>
          <p className="text-2xl font-black text-[#1a1c1e] tabular-nums">
            {tryPerUnit.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} <span className="text-sm font-semibold text-[#5a6472]">TRY</span>
          </p>
          <p className="text-xs text-[#5a6472] mt-0.5">1 {sym} = {tryPerUnit.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} TRY</p>
          <span className="inline-flex items-center gap-1 mt-2 text-[11px] font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" /> {t('Gerçek zamanlıya yakın')}
          </span>
        </>
      ) : (
        <p className="text-sm text-[#9aa6b6]">{t('Veri bulunamadı.')}</p>
      )}
    </section>
  );
}
