import { Link } from 'react-router-dom';
import { BarChart2 } from 'lucide-react';
import { useTranslation } from '../../i18n/LanguageContext';

/**
 * "Karşılaştır" butonu — kıymetli madenlerin (altın, gümüş, platin, paladyum) kendi
 * normalize karşılaştırma sayfasına götürür (`/market/commodities/compare`). Sayfa-içi
 * "Tümüyle Karşılaştır" (her enstrümanla) yerine, aynı tür (yalnız madenler) kıyas içindir.
 */
export default function PreciousCompareButton({ className = '' }) {
  const { t } = useTranslation();
  return (
    <Link
      to="/market/commodities/compare"
      title={t('Altın, gümüş, platin ve paladyumu kendi karşılaştırma sayfasında kıyasla')}
      className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border border-[#093eaa]/25 text-[#093eaa] bg-[#093eaa]/5 hover:bg-[#093eaa]/10 transition-all ${className}`}>
      <BarChart2 className="w-3.5 h-3.5" /> {t('Karşılaştır')}
    </Link>
  );
}
