import { Link } from 'react-router-dom';

function fmtNum(v, decimals = 2) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (isNaN(n)) return null;
  return n.toLocaleString('tr-TR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

// SimilarBonds artık kullanılmıyor (BondDetailPage'den kaldırıldı)
// Gerekirse ileride EVDS listesinden benzer vadeli kıymetler göstermek için kullanılabilir
export default function SimilarBonds({ bond, allBonds = [] }) {
  const days = bond.remainingDays ?? null;
  if (days === null || allBonds.length === 0) return null;

  const similar = allBonds
    .filter(b => b.instrumentCode !== bond.instrumentCode && b.remainingDays != null)
    .map(b => ({ ...b, diff: Math.abs(b.remainingDays - days) }))
    .sort((a, b) => a.diff - b.diff)
    .slice(0, 5);

  if (similar.length === 0) return null;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="text-base font-bold text-gray-900 mb-4">Benzer Vadeli Kıymetler</h2>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="bg-gray-50">
              <th className="text-left px-4 py-2.5 text-xs font-bold text-gray-500 uppercase tracking-wider">Kıymet</th>
              <th className="text-left px-4 py-2.5 text-xs font-bold text-gray-500 uppercase tracking-wider">Tür</th>
              <th className="text-right px-4 py-2.5 text-xs font-bold text-gray-500 uppercase tracking-wider">Vade</th>
              <th className="text-right px-4 py-2.5 text-xs font-bold text-gray-500 uppercase tracking-wider">Kalan Gün</th>
              <th className="text-right px-4 py-2.5 text-xs font-bold text-gray-500 uppercase tracking-wider">Gösterge Değeri</th>
            </tr>
          </thead>
          <tbody>
            {similar.map((b, i) => (
              <tr key={i} className="border-t border-gray-100 hover:bg-blue-50 transition-colors">
                <td className="px-4 py-3">
                  <Link
                    to={`/market/bonds/${encodeURIComponent(b.instrumentCode)}`}
                    state={{ bond: b }}
                    className="font-bold text-[#093eaa] text-sm font-mono hover:underline"
                  >
                    {b.instrumentCode}
                  </Link>
                </td>
                <td className="px-4 py-3 text-xs text-gray-500">{b.type ?? '-'}</td>
                <td className="px-4 py-3 text-sm text-gray-600 text-right whitespace-nowrap">{b.maturityDate ?? '-'}</td>
                <td className="px-4 py-3 text-right">
                  <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                    (b.remainingDays ?? 9999) <= 180 ? 'bg-amber-100 text-amber-700' :
                    (b.remainingDays ?? 9999) <= 365 ? 'bg-blue-100 text-blue-700'   :
                    'bg-gray-100 text-gray-600'
                  }`}>
                    {b.remainingDays ?? '-'}
                  </span>
                </td>
                <td className="px-4 py-3 text-sm font-semibold text-right text-gray-900 font-mono">
                  {fmtNum(b.indicatorValue, 2) ?? '-'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
