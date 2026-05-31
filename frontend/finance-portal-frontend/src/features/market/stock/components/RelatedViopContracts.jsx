import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { TrendingUp, TrendingDown, ExternalLink, Loader2, Layers } from 'lucide-react';
import { getViopContractsByUnderlying, getViopContractSpec } from '../../../../api/marketApi';
import { parseTrNumber, formatPrice } from '../../../../utils/numberFormat';
import { useTranslation } from '../../../../context/LanguageContext';

export default function RelatedViopContracts({ symbol }) {
  const { t } = useTranslation();
  const [contracts, setContracts] = useState([]);
  const [spec, setSpec] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!symbol) return;
    let cancelled = false;
    const fetchContracts = async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await getViopContractsByUnderlying(symbol);
        if (cancelled) return;
        const arr = Array.isArray(data) ? data : [];
        setContracts(arr);
        // Spec'i ilk kontrat üzerinden çek — multiplier/marginRate dayanak için sabittir.
        if (arr.length > 0 && arr[0]?.name) {
          getViopContractSpec(arr[0].name)
            .then(s => { if (!cancelled) setSpec(s); })
            .catch(() => {});
        }
      } catch (err) {
        if (!cancelled) setError(err.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    fetchContracts();
    return () => { cancelled = true; };
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
      <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
        <h2 className="font-bold text-gray-900">
          {t('İlişkili VİOP Kontratları')}
        </h2>
        {spec && spec.multiplier != null && (
          <span className="inline-flex items-center gap-1.5 text-[11px] text-gray-700 bg-blue-50 border border-blue-200 px-2 py-1 rounded-md">
            <Layers className="w-3 h-3 text-blue-700" />
            {t('Çarpan')}: <span className="font-bold tabular-nums">{Number(spec.multiplier)}</span>
            {spec.marginRate != null && (
              <> · {t('Marjin')}: <span className="font-bold">%{(Number(spec.marginRate) * 100).toFixed(1)}</span></>
            )}
          </span>
        )}
      </div>

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
