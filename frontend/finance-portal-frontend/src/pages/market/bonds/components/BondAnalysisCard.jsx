import { useTranslation } from '../../../../i18n/LanguageContext';

function fmtNum(v, decimals = 2) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (isNaN(n)) return null;
  return n.toLocaleString('tr-TR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

export default function BondAnalysisCard({ bond }) {
  const { t } = useTranslation();
  const days           = bond.remainingDays ?? null;
  const indicator      = bond.indicatorValue != null ? parseFloat(bond.indicatorValue) : null;
  const previous       = bond.previousValue  != null ? parseFloat(bond.previousValue)  : null;
  const dailyChange    = bond.dailyChange    != null ? parseFloat(bond.dailyChange)    : null;
  const dailyChangePct = bond.dailyChangePercent != null ? parseFloat(bond.dailyChangePercent) : null;
  const couponRate     = bond.couponRate != null ? parseFloat(bond.couponRate) : null;

  // Vade cümlesi
  const maturityText =
    days == null ? t('vade bilgisi mevcut değildir') :
    days <= 90   ? `${t('vadesine')} ${days} ${t('gün kalmıştır ve kısa vadeli olarak değerlendirilebilir')}` :
    days <= 365  ? `${t('vadesine')} ${days} ${t('gün kalmıştır ve orta vadeli olarak değerlendirilebilir')}` :
                   `${t('vadesine')} ${days} ${t('gün kalmıştır ve uzun vadeli olarak değerlendirilebilir')}`;

  // Gösterge değeri cümlesi
  const indicatorText = indicator != null
    ? `${t('TCMB EVDS gösterge değeri')} ${fmtNum(indicator, 2)} ${t('seviyesindedir.')}`
    : t('Gösterge değeri bilgisi mevcut değildir.');

  // Önceki değer + değişim cümlesi
  const changeText = previous != null && dailyChange != null && dailyChangePct != null
    ? `${t('Önceki değer')} ${fmtNum(previous, 2)} ${t('olup günlük değişim')} ${dailyChange > 0 ? '+' : dailyChange < 0 ? '-' : ''}${fmtNum(Math.abs(dailyChange), 4)} ${t('ve günlük değişim oranı')} ${dailyChangePct > 0 ? '+' : dailyChangePct < 0 ? '-' : ''}%${fmtNum(Math.abs(dailyChangePct), 2)} ${t('olarak hesaplanmıştır.')}`
    : '';

  // Kupon cümlesi
  const couponText = couponRate != null
    ? `${t('Kupon faiz oranı')} %${fmtNum(couponRate, 2)} ${t('seviyesindedir.')}`
    : t('Kupon faiz oranı bilgisi mevcut değildir.');

  return (
    <div className="bg-blue-50 border border-blue-200 rounded-2xl p-6">
      <h2 className="text-base font-bold text-blue-900 mb-3">{t('Mini Analiz')}</h2>
      <p className="text-sm text-blue-800 leading-relaxed">
        {t('Bu kıymetin')} {maturityText}. {indicatorText}{' '}
        {changeText && <>{changeText} </>}
        {couponText}{' '}
        <span className="text-blue-600 font-medium">
          {t('Bu veriler TCMB EVDS kaynaklı gösterge değerleridir, alış/satış fiyatı değildir.')}
        </span>
      </p>
    </div>
  );
}
