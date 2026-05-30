import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { TrendingUp, TrendingDown, ExternalLink, Loader2 } from 'lucide-react';
import { getViopContractsByUnderlying } from '../../../../api/marketApi';
import { parseTrNumber, formatPrice } from '../../../../utils/numberFormat';
import { useTranslation } from '../../../../context/LanguageContext';

export default function RelatedViopContracts({ symbol }) {
  const { t } = useTranslation();
  const [contracts, setContracts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!symbol) {
      console.log('RelatedViopContracts: symbol yok');
      return;
    }

    console.log('RelatedViopContracts: symbol =', symbol);

    const fetchContracts = async () => {
      try {
        setLoading(true);
        setError(null);
        console.log('VİOP kontratları çekiliyor:', symbol);
        const data = await getViopContractsByUnderlying(symbol);
        console.log('VİOP kontratları geldi:', data);
        setContracts(data);
      } catch (err) {
        console.error('VİOP kontratları yüklenemedi:', err);
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchContracts();
  }, [symbol]);

  if (loading) {
    return (
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
        <h2 className="font-bold text-gray-900 mb-4">
          {t('İlişkili VİOP Kontratları')}
        </h2>
        <div className="flex items-center justify-center py-8">
          <Loader2 className="w-6 h-6 text-blue-600 animate-spin" />
          <span className="ml-2 text-sm text-gray-500">{t('Yükleniyor...')}</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
        <h2 className="font-bold text-gray-900 mb-4">
          {t('İlişkili VİOP Kontratları')}
        </h2>
        <div className="bg-red-50 border border-red-200 rounded-xl p-4">
          <p className="text-sm text-red-600">{t('Hata:')} {error}</p>
        </div>
      </div>
    );
  }

  if (contracts.length === 0) {
    return (
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
        <h2 className="font-bold text-gray-900 mb-4">
          {t('İlişkili VİOP Kontratları')}
        </h2>
        <div className="bg-gray-50 rounded-xl p-4">
          <p className="text-sm text-gray-500">{t('Bu hisse için VİOP kontratı bulunamadı.')}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4">
        {t('İlişkili VİOP Kontratları')}
      </h2>

      <div className="space-y-2">
        {contracts.map((contract, index) => {
          const lastPrice = parseTrNumber(contract.lastPrice);
          const changePercentStr = contract.changePercent?.replace('%', '').trim() || '0';
          const changePercent = parseTrNumber(changePercentStr) || 0;
          const isPositive = changePercent >= 0;

          return (
            <Link
              key={index}
              to={`/market/futures/${encodeURIComponent(contract.name)}`}
              state={{ contract }}
              className="flex items-center justify-between p-3 bg-gray-50 hover:bg-blue-50 rounded-xl transition-colors group border border-transparent hover:border-blue-200"
            >
              <div className="flex-1 min-w-0">
                <p className="text-sm font-bold text-gray-900 truncate group-hover:text-blue-900">
                  {contract.name}
                </p>
                <p className="text-xs text-gray-500">{t('Vadeli İşlem')}</p>
              </div>

              <div className="flex items-center gap-3 ml-4">
                <div className="text-right">
                  <p className="text-sm font-bold text-gray-900">
                    {lastPrice !== null ? formatPrice(lastPrice) : '-'}
                  </p>
                  {changePercent !== 0 && (
                    <p className={`text-xs font-semibold flex items-center justify-end gap-0.5 ${isPositive ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {isPositive ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                      {isPositive ? '+' : ''}{changePercent.toFixed(2)}%
                    </p>
                  )}
                </div>
                <ExternalLink className="w-4 h-4 text-gray-400 group-hover:text-blue-600 flex-shrink-0" />
              </div>
            </Link>
          );
        })}
      </div>

      <div className="mt-4 bg-blue-50 rounded-xl p-3 border border-blue-200">
        <p className="text-xs text-blue-900 leading-relaxed">
          <span className="font-semibold">{t('VİOP:')}</span> {symbol} {t('hisse senedine dayalı vadeli işlem sözleşmeleri. Gelecekteki fiyat hareketlerine göre pozisyon alabilirsiniz.')}
        </p>
      </div>
    </div>
  );
}
