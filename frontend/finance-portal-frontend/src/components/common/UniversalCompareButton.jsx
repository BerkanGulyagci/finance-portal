import { Link } from 'react-router-dom';
import { GitCompare } from 'lucide-react';
import { useTranslation } from '../../i18n/LanguageContext';

/**
 * "Tümüyle Karşılaştır" butonu — herhangi bir enstrümanı evrensel karşılaştırma sayfasına
 * ön-seçimle gönderir (`/market/stocks/compare?add=TÜR|SEMBOL&name=`). Üzerine gelince
 * (native title) ne işe yaradığını açıklar; böylece kullanıcı sayfa-içi "Karşılaştır"
 * (aynı tür, aynı grafik) ile bunun (her şeyle, ayrı sayfa) farkını anlar.
 *
 * Not: native `title` kullanıyoruz çünkü grafik kartları `overflow-hidden`; özel CSS balon
 * tooltip'ler kart kenarında kırpılıyordu.
 *
 * @param assetType STOCK | CRYPTO | FX | GOLD | FUND | COMMODITY | BOND | FUTURE
 * @param symbol    backend genel price-history endpoint'inin beklediği sembol
 * @param name      kıyas sayfasında görünecek ad
 */
export default function UniversalCompareButton({
  assetType,
  symbol,
  name,
  label = 'Tümüyle Karşılaştır',
  hint = 'Hisse, döviz, altın, fon, enflasyon… her şeyle ayrı sayfada kıyasla',
  className = '',
}) {
  const { t } = useTranslation();
  if (!symbol) return null;

  const url = `/market/stocks/compare?add=${encodeURIComponent(`${assetType}|${symbol}`)}&name=${encodeURIComponent(name || symbol)}`;

  return (
    <Link
      to={url}
      title={t(hint)}
      className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border border-[#093eaa]/25 text-[#093eaa] bg-[#093eaa]/5 hover:bg-[#093eaa]/10 transition-all ${className}`}>
      <GitCompare className="w-3.5 h-3.5" /> {t(label)}
    </Link>
  );
}
