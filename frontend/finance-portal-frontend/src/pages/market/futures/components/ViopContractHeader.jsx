import { TrendingUp, TrendingDown, Clock, Plus } from 'lucide-react';
import { parseTrNumber, formatPrice } from '../../../../utils/numberFormat';
import { useTranslation } from '../../../../i18n/LanguageContext';

export default function ViopContractHeader({ contract }) {
  const { t } = useTranslation();
  if (!contract) return null;

  // changePercent'ten % işaretini temizle ve parse et
  const changePercentStr = contract.changePercent?.replace('%', '').trim() || '0';
  const changePercent = parseTrNumber(changePercentStr) || 0;
  const isPositive = changePercent >= 0;

  // Son fiyatı parse et
  const lastPrice = parseTrNumber(contract.lastPrice);

  // Şirket kodunu name'den çıkar (örn: "AEFES Vadeli 25 May 26" → "AEFES")
  const extractCompanyCode = (name) => {
    if (!name) return null;
    const match = name.match(/^([A-Z]+)/);
    return match ? match[1] : null;
  };

  const companyCode = extractCompanyCode(contract.name);
  const logoUrl = companyCode ? `https://s3-symbol-logo.tradingview.com/${companyCode.toLowerCase()}.svg` : null;

  // TODO: Portföye ekleme fonksiyonu
  const handleAddToPortfolio = () => {
    console.log('TODO: Portföye ekle modal açılacak', contract);
    // Modal açılacak: Pozisyon türü (Long/Short), işlem tarihi, fiyat, adet, komisyon, not
  };

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm px-5 py-3">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        {/* Sol: logo + isim/tip + fiyat (tek satır) */}
        <div className="flex items-center gap-3 min-w-0">
          {logoUrl && (
            <img
              src={`/api/proxy/image?url=${encodeURIComponent(logoUrl)}`}
              alt={companyCode}
              className="w-9 h-9 rounded-lg object-contain border border-gray-100 p-1 bg-white shrink-0"
              onError={e => { e.target.style.display = 'none'; }}
            />
          )}
          <div className="min-w-0">
            <h1 className="text-lg font-black text-gray-900 leading-tight truncate">{contract.name}</h1>
            <p className="text-xs text-gray-400">{t('Vadeli İşlem Sözleşmesi · VİOP')}</p>
          </div>

          <div className="flex items-baseline gap-2 pl-3 sm:pl-4 ml-1 border-l border-gray-100">
            <span className={`text-2xl font-black ${isPositive ? 'text-emerald-600' : 'text-rose-600'}`}>
              {lastPrice !== null ? formatPrice(lastPrice) : '-'}
            </span>
            {changePercent !== 0 && (
              <span className={`flex items-center gap-0.5 text-sm font-bold ${isPositive ? 'text-emerald-600' : 'text-rose-600'}`}>
                {isPositive ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                {isPositive ? '+' : ''}{changePercent.toFixed(2)}%
              </span>
            )}
          </div>
        </div>

        {/* Sağ: zaman + buton */}
        <div className="flex items-center gap-3 shrink-0">
          {contract.time && (
            <div className="flex items-center gap-1.5 text-xs text-gray-400">
              <Clock className="w-3.5 h-3.5" />
              <span>{contract.time}</span>
            </div>
          )}
          <button
            onClick={handleAddToPortfolio}
            className="flex items-center gap-2 px-3.5 py-1.5 bg-[#093eaa] text-white text-sm font-semibold rounded-lg hover:bg-[#072d7a] transition-colors shadow-sm"
            title={t('Portföye Ekle')}
          >
            <Plus className="w-4 h-4" />
            <span className="hidden sm:inline">{t('Portföye Ekle')}</span>
          </button>
        </div>
      </div>
    </div>
  );
}
