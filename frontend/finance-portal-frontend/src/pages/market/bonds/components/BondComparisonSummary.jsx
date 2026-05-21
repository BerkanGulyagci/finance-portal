import { fmtNum, fmtPct, calculatePeriodChangePercent } from './bondChartUtils';
import { useTranslation } from '../../../../i18n/LanguageContext';

/**
 * Karşılaştırma özet kartları.
 * Her iki kıymet için yan yana gösterim.
 *
 * Props:
 *   mainCode        — ana kıymet kodu
 *   mainDetail      — ana kıymet detay objesi (EvdsBondItemDto)
 *   mainHistory     — ana kıymet history noktaları
 *   compareCode     — karşılaştırma kıymeti kodu
 *   compareDetail   — karşılaştırma kıymeti detay objesi
 *   compareHistory  — karşılaştırma kıymeti history noktaları
 *   period          — seçili periyot label'ı (örn. "1 Ay")
 */
export default function BondComparisonSummary({
  mainCode, mainDetail, mainHistory = [],
  compareCode, compareDetail, compareHistory = [],
  period,
}) {
  const { t } = useTranslation();
  if (!compareCode) return null;

  const mainPeriodPct    = calculatePeriodChangePercent(mainHistory);
  const comparePeriodPct = calculatePeriodChangePercent(compareHistory);

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4">
      <SummaryCard
        code={mainCode}
        detail={mainDetail}
        periodPct={mainPeriodPct}
        period={period}
        color="#093eaa"
        label={t('Ana Kıymet')}
      />
      <SummaryCard
        code={compareCode}
        detail={compareDetail}
        periodPct={comparePeriodPct}
        period={period}
        color="#f97316"
        label={t('Karşılaştırılan Kıymet')}
      />
    </div>
  );
}

function SummaryCard({ code, detail, periodPct, period, color, label }) {
  const { t } = useTranslation();
  const lastValue    = detail?.indicatorValue  != null ? parseFloat(detail.indicatorValue)  : null;
  const dailyPct     = detail?.dailyChangePercent != null ? parseFloat(detail.dailyChangePercent) : null;
  const couponRate   = detail?.couponRate != null ? parseFloat(detail.couponRate) : null;
  const maturityDate = detail?.maturityDate ?? '-';
  const remaining    = detail?.remainingDays ?? null;

  const periodPos = periodPct != null && periodPct >= 0;
  const dailyPos  = dailyPct  != null && dailyPct  >= 0;

  return (
    <div className="bg-white rounded-2xl border-2 shadow-sm p-5" style={{ borderColor: color + '40' }}>
      {/* Başlık */}
      <div className="flex items-center gap-2 mb-3">
        <span className="inline-block w-3 h-3 rounded-full" style={{ backgroundColor: color }} />
        <div>
          <p className="text-xs text-gray-400 font-medium">{label}</p>
          <p className="text-base font-bold text-gray-900 font-mono">{code}</p>
        </div>
      </div>

      {/* Değerler */}
      <div className="space-y-2">
        <Row label={t('Son Gösterge Değeri')}>
          <span className="font-bold text-gray-900">{lastValue != null ? fmtNum(lastValue, 2) : '-'}</span>
        </Row>

        <Row label={`${period ?? t('Seçili Periyot')} ${t('Değişimi')}`}>
          {periodPct != null ? (
            <span className={`font-bold text-xs ${periodPos ? 'text-emerald-600' : 'text-rose-600'}`}>
              {fmtPct(periodPct, 2)}
            </span>
          ) : <span className="text-gray-300 text-xs">-</span>}
        </Row>

        <Row label={t('Günlük Değişim %')}>
          {dailyPct != null ? (
            <span className={`font-semibold text-xs ${dailyPos ? 'text-emerald-600' : 'text-rose-600'}`}>
              {fmtPct(dailyPct, 2)}
            </span>
          ) : <span className="text-gray-300 text-xs">-</span>}
        </Row>

        <Row label={t('Kupon Faiz Oranı')}>
          <span className="text-sm font-semibold text-gray-700">
            {couponRate != null ? `%${fmtNum(couponRate, 2)}` : '-'}
          </span>
        </Row>

        <Row label={t('Vade Tarihi')}>
          <span className="text-sm text-gray-600">{maturityDate}</span>
        </Row>

        <Row label={t('Kalan Gün')}>
          {remaining != null ? (
            <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
              remaining <= 90  ? 'bg-amber-100 text-amber-700' :
              remaining <= 365 ? 'bg-blue-100 text-blue-700'   :
                                 'bg-gray-100 text-gray-600'
            }`}>
              {remaining} {t('gün')}
            </span>
          ) : <span className="text-gray-300 text-xs">-</span>}
        </Row>
      </div>

      <p className="text-xs text-gray-300 mt-3">{t('Kaynak:')} TCMB EVDS</p>
    </div>
  );
}

function Row({ label, children }) {
  return (
    <div className="flex items-center justify-between py-1.5 border-b border-gray-50 last:border-0">
      <span className="text-xs text-gray-400">{label}</span>
      <div>{children}</div>
    </div>
  );
}
