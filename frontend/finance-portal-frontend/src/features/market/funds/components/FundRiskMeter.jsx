const RISK_COLORS = [
  '#22c55e',
  '#84cc16',
  '#eab308',
  '#f97316',
  '#ef4444',
  '#dc2626',
  '#991b1b',
];

import { useTranslation } from '../../../../i18n/LanguageContext';

const RISK_LABELS = ['', 'Çok Düşük', 'Düşük', 'Orta-Düşük', 'Orta', 'Orta-Yüksek', 'Yüksek', 'Çok Yüksek'];

export default function FundRiskMeter({ riskValue, code }) {
  const { t } = useTranslation();
  if (!riskValue) return null;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4">{code} {t('Fon Risk Değeri')}</h2>

      <div className="flex gap-1 mb-3">
        {[1, 2, 3, 4, 5, 6, 7].map(level => (
          <div
            key={level}
            className="flex-1 h-8 rounded-lg flex items-center justify-center text-xs font-bold text-white transition-all"
            style={{
              backgroundColor: RISK_COLORS[level - 1],
              opacity: level === riskValue ? 1 : 0.25,
              transform: level === riskValue ? 'scaleY(1.15)' : 'scaleY(1)',
            }}
          >
            {level}
          </div>
        ))}
      </div>

      <div className="flex justify-between text-xs text-gray-400 mb-4">
        <span>{t('Düşük Risk')}</span>
        <span>{t('Yüksek Risk')}</span>
      </div>

      <div className="flex items-center gap-3 bg-gray-50 rounded-xl p-3">
        <div
          className="w-10 h-10 rounded-xl flex items-center justify-center text-white font-black text-lg flex-shrink-0"
          style={{ backgroundColor: RISK_COLORS[riskValue - 1] }}
        >
          {riskValue}
        </div>
        <div>
          <p className="text-sm font-bold text-gray-900">{t(RISK_LABELS[riskValue])}</p>
          <p className="text-xs text-gray-400 mt-0.5">
            {t('Fonun haftalık getirileri üzerinden, volatilitesi dikkate alınarak hesaplanır. 1 en az riskli, 7 en fazla riskli olmak üzere Risk Değeri 1 ile 7 arasındadır.')}
          </p>
        </div>
      </div>
    </div>
  );
}
