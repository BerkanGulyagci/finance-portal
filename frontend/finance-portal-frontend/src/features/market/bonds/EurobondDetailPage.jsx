import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, TrendingUp, TrendingDown, BarChart2, Repeat } from 'lucide-react';
import { getGlobalBondDetail } from '../../../api/marketApi';
import InstrumentActionButtons from '../../../components/instrument/InstrumentActionButtons';
import EurobondChart from './components/EurobondChart';
import { useTranslation } from '../../../context/LanguageContext';
import { SkeletonBondDetail } from '../../../components/common/Skeleton';

function fmtPrice(v, currency) {
  if (v == null) return '-';
  const n = Number(v);
  const sym = currency === 'TRY' ? '₺' : currency === 'EUR' ? '€' : currency === 'USD' ? '$' : '';
  return `${sym}${n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 3 })}`;
}

function Row({ label, value }) {
  if (value == null || value === '') return null;
  return (
    <div className="flex items-center justify-between gap-4 px-4 py-2.5 border-b border-gray-100 last:border-0">
      <span className="text-sm text-gray-500">{label}</span>
      <span className="text-sm font-semibold text-gray-900 text-right">{value}</span>
    </div>
  );
}

export default function EurobondDetailPage() {
  const { t } = useTranslation();
  const { isin } = useParams();
  const [d, setD] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showTry, setShowTry] = useState(false); // fiyatı TL olarak göster (kote ⇄ TL)

  useEffect(() => {
    setLoading(true); setError('');
    getGlobalBondDetail(isin)
      .then(setD)
      .catch((e) => setError(e?.response?.status === 404 ? t('Bu tahvil için canlı veri bulunamadı.') : t('Tahvil verisi yüklenemedi.')))
      .finally(() => setLoading(false));
  }, [isin]);

  const chg = d?.changePercent != null ? Number(d.changePercent) : null;
  const pos = chg != null && chg >= 0;
  const hasTry = d?.lastPriceTry != null && d?.currency && d.currency !== 'TRY';
  const showingTry = hasTry && showTry;

  return (
    <div className="max-w-5xl mx-auto">
      <Link to="/market/bonds" className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-[#093eaa] mb-4">
        <ArrowLeft className="w-4 h-4" /> {t('Tahviller')}
      </Link>

      {loading && <SkeletonBondDetail variant="eurobond" />}
      {error && !loading && <div className="bg-white rounded-2xl border border-rose-200 p-6 text-rose-500 text-sm">{error}</div>}

      {!loading && !error && d && (
        <div className="space-y-5">
          {/* Üst kart: isim + canlı fiyat + işlem butonları */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-5">
            <div className="flex items-start justify-between gap-4 flex-wrap">
              <div className="min-w-0">
                <h1 className="text-lg sm:text-xl font-bold text-gray-900">{d.name || d.isin}</h1>
                <p className="text-sm text-gray-500 mt-0.5">
                  {d.issuer} · <span className="font-mono">{d.isin}</span>
                  {d.currency ? <span className="ml-2 px-2 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700">{d.currency}</span> : null}
                </p>
                <p className="text-xs text-gray-400 mt-1">{t('Kaynak: Business Insider · Hazine dış borç tahvili')}</p>
              </div>
              <div className="flex items-center gap-2 flex-wrap">
                <Link
                  to={`/market/stocks/compare?add=BOND|${encodeURIComponent(d.isin)}&name=${encodeURIComponent(d.name || d.isin)}`}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold border border-[#093eaa]/30 text-[#093eaa] bg-[#093eaa]/5 hover:bg-[#093eaa]/10 transition-colors"
                >
                  <BarChart2 className="w-3.5 h-3.5" /> {t('Karşılaştır')}
                </Link>
                <InstrumentActionButtons assetType="BOND" symbol={d.isin} name={d.name || d.isin} price={d.lastPrice} subType="EUROBOND" />
              </div>
            </div>

            {d.lastPrice != null && (
              <div className="flex items-baseline gap-3 mt-4 pt-4 border-t border-gray-100">
                <div>
                  <p className="text-xs text-gray-400">{t('Güncel Fiyat')}</p>
                  <button
                    type="button"
                    onClick={() => hasTry && setShowTry(s => !s)}
                    title={hasTry ? t('TL ⇄ döviz çevir') : undefined}
                    className={`text-2xl sm:text-3xl font-black text-gray-900 inline-flex items-center gap-1.5 ${hasTry ? 'cursor-pointer hover:text-[#093eaa] transition-colors' : 'cursor-default'}`}
                  >
                    {fmtPrice(showingTry ? d.lastPriceTry : d.lastPrice, showingTry ? 'TRY' : d.currency)}
                    {hasTry && <Repeat className="w-4 h-4 text-gray-300" />}
                  </button>
                  {hasTry && (
                    <p className="text-[11px] text-gray-400 mt-0.5">
                      {showingTry
                        ? `≈ ${fmtPrice(d.lastPrice, d.currency)} · ${t('fiyata tıklayarak döviz/TL değiştir')}`
                        : `≈ ${fmtPrice(d.lastPriceTry, 'TRY')} · ${t('fiyata tıklayarak TL gör')}`}
                    </p>
                  )}
                </div>
                {chg != null && (
                  <span className={`inline-flex items-center gap-1 text-sm font-bold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                    {pos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                    {pos ? '+' : ''}{chg.toFixed(2)}%
                  </span>
                )}
              </div>
            )}
          </div>

          {/* Fiyat grafiği (klinecharts) */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-5">
            <h2 className="text-base font-bold text-gray-900 mb-3">{t('Fiyat Grafiği')}</h2>
            <EurobondChart isin={d.isin} />
            <p className="text-xs text-gray-400 mt-2">{t('Kaynak: Business Insider · OHLC verisi')}</p>
          </div>

          {/* Bond Data */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
            <h2 className="text-base font-bold text-gray-900 px-4 py-3 border-b border-gray-100">{t('Tahvil Bilgileri')}</h2>
            <Row label="ISIN" value={d.isin} />
            <Row label={t('İhraçcı')} value={d.issuer} />
            <Row label={t('Ülke')} value={d.country} />
            <Row label={t('Döviz')} value={d.currency} />
            <Row label={t('Kupon Oranı')} value={d.couponRate} />
            <Row label={t('İhraç Hacmi')} value={d.issueVolume} />
            <Row label={t('İhraç Fiyatı')} value={d.issuePrice} />
            <Row label={t('İhraç Tarihi')} value={d.issueDate} />
            <Row label={t('Vade Tarihi')} value={d.maturityDate} />
            <Row label={t('Birim (Denomination)')} value={d.denomination} />
            <Row label={t('Ödeme Tipi')} value={d.paymentType} />
            <Row label={t('Kupon Ödeme Tarihi')} value={d.couponPaymentDate} />
            <Row label={t('Yıllık Ödeme Sayısı')} value={d.paymentsPerYear} />
            <Row label={t('Kupon Başlangıç')} value={d.couponStartDate} />
            <Row label={t('Son Kupon')} value={d.finalCouponDate} />
          </div>
        </div>
      )}
    </div>
  );
}
