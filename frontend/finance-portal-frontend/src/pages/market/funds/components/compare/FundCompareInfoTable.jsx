import { COMPARE_COLORS, toNum, fmtBig } from './compareUtils';

/** NaN, null, undefined, "NaN" string → "-" */
function safe(v) {
  if (v == null) return '-';
  const s = String(v).trim();
  if (s === '' || s === 'NaN' || s === 'null' || s === 'undefined') return '-';
  return s;
}

function safeNum(v, suffix = '') {
  const n = toNum(v);
  if (n == null || isNaN(n)) return '-';
  return `${n}${suffix}`;
}

const INFO_ROWS = [
  { key: 'fundType',            label: 'Fon Tipi',          fmt: v => safe(v) },
  { key: 'riskLevel',           label: 'Risk Seviyesi',      fmt: v => v != null && !isNaN(v) ? `${v}/7` : '-' },
  { key: 'marketCap',           label: 'Fon Büyüklüğü',      fmt: v => fmtBig(v) },
  { key: 'managementFeeAnnual', label: 'Yönetim Ücreti',     fmt: v => { const s = safe(v); return s !== '-' ? `%${s}` : '-'; } },
  { key: 'commission',          label: 'Komisyon',           fmt: v => { const s = safe(v); return s !== '-' ? `%${s}` : '-'; } },
  { key: 'buySettlement',       label: 'Alım Valörü',        fmt: v => v != null && !isNaN(Number(v)) ? `T+${v}` : '-' },
  { key: 'sellSettlement',      label: 'Satım Valörü',       fmt: v => v != null && !isNaN(Number(v)) ? `T+${v}` : '-' },
  { key: 'minimumQuantitySales',label: 'Min. Alım',          fmt: v => { const s = safe(v); return s !== '-' && s !== 'NaN' ? s : '-'; } },
  { key: 'managerName',         label: 'Fon Yöneticisi',     fmt: v => safe(v) },
  { key: 'founderName',         label: 'Kurucu',             fmt: v => safe(v) },
  { key: 'benchmarkName',       label: 'Kıstas',             fmt: v => safe(v) },
];

export default function FundCompareInfoTable({ detailMap, selectedCodes }) {
  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100">
        <h2 className="font-bold text-gray-900">Fon Bilgileri</h2>
        <p className="text-xs text-gray-400 mt-0.5">Temel fon özellikleri karşılaştırması</p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[500px]">
          <thead className="bg-gray-50">
            <tr>
              <th className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 w-36">
                Özellik
              </th>
              {selectedCodes.map((code, idx) => (
                <th key={code} className="text-left px-5 py-3 text-xs font-bold border-b border-gray-200 whitespace-nowrap">
                  <span className="flex items-center gap-1.5">
                    <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: COMPARE_COLORS[idx % COMPARE_COLORS.length] }} />
                    <span style={{ color: COMPARE_COLORS[idx % COMPARE_COLORS.length] }}>{code}</span>
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {INFO_ROWS
              .filter(row => {
                // En az bir fonda dolu değer varsa satırı göster
                return selectedCodes.some(code => {
                  const formatted = row.fmt(detailMap[code]?.[row.key]);
                  return formatted !== '-';
                });
              })
              .map((row, ri) => (
              <tr key={row.key} className={`border-t border-gray-100 ${ri % 2 === 0 ? 'bg-white' : 'bg-gray-50/40'}`}>
                <td className="px-5 py-3 text-sm font-semibold text-gray-600 whitespace-nowrap">{row.label}</td>
                {selectedCodes.map(code => (
                  <td key={code} className="px-5 py-3 text-sm text-gray-800">
                    {row.fmt(detailMap[code]?.[row.key])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
