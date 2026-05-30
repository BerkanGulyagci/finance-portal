import { Link } from 'react-router-dom';
import { ExternalLink } from 'lucide-react';
import { COMPARE_COLORS, toNum, fmtPct, fmtPctColor, fmtPrice, calcPeriodReturn } from './compareUtils';
import { useTranslation } from '../../../../../i18n/LanguageContext';

function RiskBar({ level }) {
  if (level == null) return <span className="text-gray-300 text-xs">-</span>;
  const colors = ['', '#22c55e', '#84cc16', '#eab308', '#f97316', '#ef4444', '#dc2626', '#7c3aed'];
  return (
    <div className="flex items-center gap-1.5">
      <div className="flex gap-0.5">
        {[1,2,3,4,5,6,7].map(i => (
          <div key={i} className="w-3 h-2 rounded-sm"
            style={{ backgroundColor: i <= level ? colors[level] : '#e5e7eb' }} />
        ))}
      </div>
      <span className="text-xs font-bold" style={{ color: colors[level] }}>{level}/7</span>
    </div>
  );
}

export default function FundCompareSummaryCards({ detailMap, selectedCodes, range }) {
  const { t } = useTranslation();
  return (
    <div className={`grid gap-4 ${selectedCodes.length === 2 ? 'grid-cols-2' : selectedCodes.length === 3 ? 'grid-cols-3' : 'grid-cols-2 lg:grid-cols-4'}`}>
      {selectedCodes.map((code, idx) => {
        const d = detailMap[code];
        const color = COMPARE_COLORS[idx % COMPARE_COLORS.length];
        const periodRet = calcPeriodReturn(d?.priceHistory ?? [], range);
        const ret1m = toNum(d?.returnOneMonth);
        const ret1y = toNum(d?.returnOneYear);

        return (
          <div key={code} className="bg-white rounded-2xl border border-gray-200 shadow-sm p-4 relative overflow-hidden">
            {/* Renk şeridi */}
            <div className="absolute top-0 left-0 right-0 h-1 rounded-t-2xl" style={{ backgroundColor: color }} />

            <div className="flex items-start justify-between mb-3 mt-1">
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-bold text-lg font-mono" style={{ color }}>{code}</span>
                  <Link to={`/market/tefas/${code}`}
                    className="text-gray-400 hover:text-gray-600 transition-colors">
                    <ExternalLink className="w-3.5 h-3.5" />
                  </Link>
                </div>
                {d?.name && (
                  <p className="text-xs text-gray-500 mt-0.5 leading-tight line-clamp-2">{d.name}</p>
                )}
              </div>
            </div>

            {/* Fiyat */}
            <div className="mb-3">
              <p className="text-xs text-gray-400">{t('Güncel Fiyat')}</p>
              <p className="text-xl font-bold text-gray-900 font-mono">{fmtPrice(d?.price)}</p>
              {d?.currencyCode && <p className="text-xs text-gray-400">{d.currencyCode}</p>}
            </div>

            {/* Getiriler */}
            <div className="grid grid-cols-2 gap-2 mb-3">
              <div className="bg-gray-50 rounded-lg p-2 text-center">
                <p className="text-[10px] text-gray-400 mb-0.5">{t('1 Ay')}</p>
                <p className={`text-sm font-bold ${fmtPctColor(ret1m)}`}>{fmtPct(ret1m)}</p>
              </div>
              <div className="bg-gray-50 rounded-lg p-2 text-center">
                <p className="text-[10px] text-gray-400 mb-0.5">{t('1 Yıl')}</p>
                <p className={`text-sm font-bold ${fmtPctColor(ret1y)}`}>{fmtPct(ret1y)}</p>
              </div>
            </div>

            {/* Dönem getirisi */}
            {periodRet != null && (
              <div className="bg-gray-50 rounded-lg p-2 text-center mb-3">
                <p className="text-[10px] text-gray-400 mb-0.5">{t('Seçili Dönem')}</p>
                <p className={`text-sm font-bold ${fmtPctColor(periodRet)}`}>{fmtPct(periodRet)}</p>
              </div>
            )}

            {/* Risk + Fon tipi */}
            <div className="space-y-1.5 border-t border-gray-100 pt-2.5">
              <div className="flex items-center justify-between">
                <span className="text-xs text-gray-500">{t('Risk')}</span>
                <RiskBar level={d?.riskLevel} />
              </div>
              {d?.fundType && (
                <div className="flex items-center justify-between">
                  <span className="text-xs text-gray-500">{t('Tip')}</span>
                  <span className="text-xs font-semibold text-gray-700">{d.fundType}</span>
                </div>
              )}
              {d?.managerName && (
                <div className="flex items-center justify-between">
                  <span className="text-xs text-gray-500">{t('Yönetici')}</span>
                  <span className="text-xs font-semibold text-gray-700 text-right max-w-[100px] truncate">{d.managerName}</span>
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
