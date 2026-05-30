import { COMPARE_COLORS, toNum, fmtPct, fmtPctColor } from './compareUtils';
import { useTranslation } from '../../../../../i18n/LanguageContext';

const RETURN_ROWS = [
  { key: 'returnOneDay',      label: 'Günlük' },
  { key: 'returnOneWeek',     label: 'Haftalık' },
  { key: 'returnOneMonth',    label: '1 Ay' },
  { key: 'returnThreeMonths', label: '3 Ay' },
  { key: 'returnSixMonths',   label: '6 Ay' },
  { key: 'returnYearToDate',  label: 'YBG' },
  { key: 'returnOneYear',     label: '1 Yıl' },
  { key: 'returnThreeYears',  label: '3 Yıl' },
  { key: 'returnFiveYears',   label: '5 Yıl' },
];

function BestBadge() {
  const { t } = useTranslation();
  return <span className="ml-1 text-[9px] bg-emerald-100 text-emerald-700 px-1 py-0.5 rounded font-bold">{t('EN İYİ')}</span>;
}

export default function FundCompareReturnsTable({ detailMap, selectedCodes }) {
  const { t } = useTranslation();
  function getBest(rowKey) {
    let best = null, bestCode = null;
    selectedCodes.forEach(code => {
      const v = toNum(detailMap[code]?.[rowKey]);
      if (v != null && (best == null || v > best)) { best = v; bestCode = code; }
    });
    return bestCode;
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100">
        <h2 className="font-bold text-gray-900">{t('Dönem Getirileri (Kümülatif)')}</h2>
        <p className="text-xs text-gray-400 mt-0.5">{t('Rasyonet / YatırımDirekt kaynaklı resmi getiri değerleri')}</p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[500px]">
          <thead className="bg-gray-50">
            <tr>
              <th className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 w-28">
                {t('Dönem')}
              </th>
              {selectedCodes.map((code, idx) => (
                <th key={code} className="text-right px-5 py-3 text-xs font-bold border-b border-gray-200 whitespace-nowrap">
                  <span className="flex items-center justify-end gap-1.5">
                    <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: COMPARE_COLORS[idx % COMPARE_COLORS.length] }} />
                    <span style={{ color: COMPARE_COLORS[idx % COMPARE_COLORS.length] }}>{code}</span>
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {RETURN_ROWS
              .filter(row => selectedCodes.some(code => toNum(detailMap[code]?.[row.key]) != null))
              .map((row, ri) => {
              const bestCode = getBest(row.key);
              return (
                <tr key={row.key} className={`border-t border-gray-100 ${ri % 2 === 0 ? 'bg-white' : 'bg-gray-50/40'}`}>
                  <td className="px-5 py-3 text-sm font-semibold text-gray-600">{t(row.label)}</td>
                  {selectedCodes.map(code => {
                    const v = toNum(detailMap[code]?.[row.key]);
                    const isBest = code === bestCode && v != null;
                    return (
                      <td key={code} className="px-5 py-3 text-sm text-right">
                        <span className={`font-bold ${fmtPctColor(v)}`}>
                          {fmtPct(v)}
                          {isBest && <BestBadge />}
                        </span>
                      </td>
                    );
                  })}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
