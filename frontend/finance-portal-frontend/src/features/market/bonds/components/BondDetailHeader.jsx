import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';

export default function BondDetailHeader({ bond }) {
  const { t } = useTranslation();
  const days = bond.remainingDays ?? null;

  const daysColor =
    days != null && days <= 90  ? 'bg-amber-100 text-amber-700' :
    days != null && days <= 365 ? 'bg-blue-100 text-blue-700'   :
                                  'bg-gray-100 text-gray-600';

  const lastUpdated = bond.lastUpdated
    ? new Date(bond.lastUpdated).toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit', year: 'numeric' })
    : '-';

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm px-5 py-4">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        {/* Sol */}
        <div className="flex items-center gap-3">
          <Link to="/market/bonds" className="text-gray-400 hover:text-[#093eaa] transition-colors shrink-0">
            <ArrowLeft className="w-5 h-5" />
          </Link>
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className="text-2xl font-bold text-gray-900 font-mono tracking-wide leading-none">
                {bond.instrumentCode}
              </h1>
              <span className="px-2.5 py-0.5 bg-indigo-50 text-indigo-700 text-xs font-bold rounded-full">
                {bond.type ?? 'DİBS'}
              </span>
            </div>
            {/* Tarih + kalan gün badge'leri */}
            <div className="flex flex-wrap gap-2 mt-2">
              {bond.issueDate && (
                <span className="px-2.5 py-0.5 bg-gray-100 text-gray-600 text-xs font-medium rounded-full">
                  {t('İhraç:')} {bond.issueDate}
                </span>
              )}
              {bond.maturityDate && (
                <span className="px-2.5 py-0.5 bg-gray-100 text-gray-600 text-xs font-medium rounded-full">
                  {t('Vade:')} {bond.maturityDate}
                </span>
              )}
              {days != null && (
                <span className={`px-2.5 py-0.5 text-xs font-semibold rounded-full ${daysColor}`}>
                  {t('Kalan:')} {days} {t('gün')}
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Sağ */}
        <div className="text-right text-xs text-gray-400 space-y-0.5">
          <p>{t('Kaynak:')} <span className="font-semibold text-[#093eaa]">TCMB EVDS</span></p>
          <p>{t('Son Güncelleme:')} <span className="font-semibold text-gray-600">{lastUpdated}</span></p>
        </div>
      </div>
    </div>
  );
}
