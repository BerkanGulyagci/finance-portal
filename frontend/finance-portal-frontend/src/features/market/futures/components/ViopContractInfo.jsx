import { Info, Calendar, Package, TrendingUp, ExternalLink } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useTranslation } from '../../../../context/LanguageContext';

export default function ViopContractInfo({ contract }) {
  const { t } = useTranslation();
  if (!contract) return null;

  // Kontrat adından vade tarihini çıkarmaya çalış
  // Örnek: "AEFES Vadeli 25 May 26" → "25 Mayıs 2026"
  const extractMaturityDate = (name) => {
    if (!name) return null;
    
    const monthMap = {
      'Oca': 'Ocak', 'Şub': 'Şubat', 'Mar': 'Mart', 'Nis': 'Nisan',
      'May': 'Mayıs', 'Haz': 'Haziran', 'Tem': 'Temmuz', 'Ağu': 'Ağustos',
      'Eyl': 'Eylül', 'Eki': 'Ekim', 'Kas': 'Kasım', 'Ara': 'Aralık'
    };

    // "25 May 26" formatını ara
    const match = name.match(/(\d{1,2})\s+([A-Za-zğüşıöçĞÜŞİÖÇ]{3})\s+(\d{2})/);
    if (match) {
      const day = match[1];
      const monthShort = match[2];
      const yearShort = match[3];
      const monthFull = monthMap[monthShort] || monthShort;
      const yearFull = `20${yearShort}`;
      return `${day} ${monthFull} ${yearFull}`;
    }
    
    return null;
  };

  const maturityDate = extractMaturityDate(contract.name);

  // Şirket kodunu çıkar
  const extractCompanyCode = (name) => {
    if (!name) return null;
    const match = name.match(/^([A-Z]+)/);
    return match ? match[1] : null;
  };

  const companyCode = extractCompanyCode(contract.name);

  // Sözleşme tipini belirle
  const extractContractType = (name) => {
    if (!name) return t('Vadeli İşlem');
    if (name.includes('FİZ') || name.includes('Fiziki')) return t('Vadeli Fiziki Teslimat');
    if (name.includes('NAK') || name.includes('Nakdi')) return t('Vadeli Nakdi Uzlaşma');
    return t('Vadeli İşlem');
  };

  const contractType = extractContractType(contract.name);

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
        <Info className="w-5 h-5 text-[#093eaa]" />
        {t('Sözleşme Bilgileri')}
      </h2>

      <div className="space-y-3">
        {/* Sözleşme Adı */}
        <div className="flex items-start gap-3 p-3 bg-gray-50 rounded-xl">
          <Package className="w-4 h-4 text-gray-400 flex-shrink-0 mt-0.5" />
          <div className="flex-1 min-w-0">
            <p className="text-xs text-gray-500 font-semibold mb-0.5">{t('Sözleşme Adı')}</p>
            <p className="text-sm font-bold text-gray-900 break-words">{contract.name}</p>
          </div>
        </div>

        {/* Dayanak Varlık */}
        {companyCode && (
          <Link
            to={`/market/stocks/${companyCode}`}
            className="flex items-start gap-3 p-3 bg-[#093eaa]/[0.06] rounded-xl border border-[#093eaa]/20 hover:border-[#093eaa]/40 transition-all group"
          >
            <TrendingUp className="w-4 h-4 text-[#093eaa] flex-shrink-0 mt-0.5 group-hover:scale-110 transition-transform" />
            <div className="flex-1">
              <p className="text-xs text-[#093eaa] font-semibold mb-0.5">{t('Dayanak Varlık')}</p>
              <div className="flex items-center gap-2">
                <p className="text-sm font-bold text-[#093eaa]">{companyCode} {t('Hisse Senedi')}</p>
                <ExternalLink className="w-3.5 h-3.5 text-[#093eaa] opacity-0 group-hover:opacity-100 transition-opacity" />
              </div>
              <p className="text-xs text-[#093eaa]/80 mt-1">{t('Hisse detayına git →')}</p>
            </div>
          </Link>
        )}

        {/* Vade Tarihi */}
        {maturityDate && (
          <div className="flex items-start gap-3 p-3 bg-gray-50 rounded-xl">
            <Calendar className="w-4 h-4 text-gray-400 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="text-xs text-gray-500 font-semibold mb-0.5">{t('Vade Tarihi')}</p>
              <p className="text-sm font-bold text-gray-900">{maturityDate}</p>
            </div>
          </div>
        )}

        {/* Sözleşme Tipi */}
        <div className="flex items-start gap-3 p-3 bg-gray-50 rounded-xl">
          <div className="w-4 h-4 flex items-center justify-center text-gray-400 flex-shrink-0 mt-0.5">
            <span className="text-xs font-bold">⚡</span>
          </div>
          <div className="flex-1">
            <p className="text-xs text-gray-500 font-semibold mb-0.5">{t('Sözleşme Tipi')}</p>
            <p className="text-sm font-bold text-gray-900">{contractType}</p>
          </div>
        </div>

        {/* Piyasa */}
        <div className="flex items-start gap-3 p-3 bg-gray-50 rounded-xl">
          <div className="w-4 h-4 flex items-center justify-center text-gray-400 flex-shrink-0 mt-0.5">
            <span className="text-xs font-bold">🏛</span>
          </div>
          <div className="flex-1">
            <p className="text-xs text-gray-500 font-semibold mb-0.5">{t('Piyasa')}</p>
            <p className="text-sm font-bold text-gray-900">VİOP</p>
          </div>
        </div>

        {/* Bilgi notu */}
        <div className="bg-gray-50 rounded-xl p-3 border border-gray-100">
          <p className="text-xs text-gray-600 leading-relaxed">
            <span className="font-semibold">{t('VİOP:')}</span> {t('Borsa İstanbul bünyesinde işlem gören vadeli işlem sözleşmeleridir. Yatırımcılar gelecekteki fiyat hareketlerine göre pozisyon alabilirler.')}
          </p>
        </div>
      </div>
    </div>
  );
}
