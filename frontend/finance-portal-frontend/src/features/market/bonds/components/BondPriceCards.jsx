import { useTranslation } from '../../../../i18n/LanguageContext';

function fmtNum(v, decimals = 2) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

function PriceCard({ label, value, sub, accent, changeColor }) {
  return (
    <div className={`bg-white rounded-2xl border shadow-sm p-5 ${accent ? 'border-[#093eaa]/30' : 'border-gray-200'}`}>
      <p className="text-xs text-gray-400 mb-2 font-medium uppercase tracking-wide">{label}</p>
      <p className={`text-2xl font-bold font-mono ${
        changeColor === 'pos' ? 'text-emerald-600' :
        changeColor === 'neg' ? 'text-rose-600' :
        accent ? 'text-[#093eaa]' : 'text-gray-900'
      }`}>
        {value ?? '-'}
      </p>
      {sub && <p className="text-xs text-gray-400 mt-1">{sub}</p>}
    </div>
  );
}

export default function BondPriceCards({ bond }) {
  const { t } = useTranslation();
  const indicator = bond.indicatorValue != null ? fmtNum(bond.indicatorValue, 2) : '-';
  const previous  = bond.previousValue  != null ? fmtNum(bond.previousValue,  2) : '-';

  // Günlük değişim
  const dailyChange = bond.dailyChange != null ? parseFloat(bond.dailyChange) : null;
  const dailyChangePct = bond.dailyChangePercent != null ? parseFloat(bond.dailyChangePercent) : null;
  const changePos = dailyChange != null && dailyChange > 0;
  const changeNeg = dailyChange != null && dailyChange < 0;

  const changeStr = dailyChange != null
    ? `${changePos ? '+' : ''}${fmtNum(dailyChange, 4)}`
    : '-';
  const changePctStr = dailyChangePct != null
    ? `${changePos ? '+' : ''}%${fmtNum(Math.abs(dailyChangePct), 2)}`
    : null;

  const couponRate = bond.couponRate != null ? `%${fmtNum(bond.couponRate, 2)}` : '-';

  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <PriceCard
        label={t('TCMB EVDS Gösterge Değeri')}
        value={indicator}
        sub={t('Günlük gösterge')}
        accent
      />
      <PriceCard
        label={t('Önceki Değer')}
        value={previous}
        sub={t('Bir önceki iş günü')}
      />
      <PriceCard
        label={t('Günlük Değişim')}
        value={changeStr}
        sub={changePctStr ?? undefined}
        changeColor={changePos ? 'pos' : changeNeg ? 'neg' : undefined}
      />
      <PriceCard
        label={t('Kupon Faiz Oranı')}
        value={couponRate}
        sub={t('Kupon oranı')}
      />
    </div>
  );
}
