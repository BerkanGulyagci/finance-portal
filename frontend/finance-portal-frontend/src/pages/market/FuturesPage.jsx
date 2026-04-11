import { useEffect, useState } from 'react';
import { getFutures } from '../../api/marketApi';

function pct(v) {
  if (v == null) return <span className="text-gray-400">-</span>;
  const n = parseFloat(v);
  return <span className={n >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{n >= 0 ? '+' : ''}{n.toFixed(2)}%</span>;
}
function num(v, dec = 2) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

export default function FuturesPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getFutures(0, 20).then(r => setItems(r.content ?? [])).catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`)).finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">Vadeli İşlemler</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">Küresel endeks, emtia ve döviz vadeli kontratları</p>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {loading && <div className="p-8 text-center text-gray-400 text-sm">Yükleniyor...</div>}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}
        {!loading && !error && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>{['Sembol', 'Ad', 'Fiyat', 'Değişim', '%', 'Yüksek', 'Düşük', 'Hacim', 'Borsa'].map(h =>
                  <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider whitespace-nowrap border-b border-gray-200">{h}</th>
                )}</tr>
              </thead>
              <tbody>
                {items.map(r => (
                  <tr key={r.symbol} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{r.symbol}</td>
                    <td className="px-4 py-3 text-sm text-gray-700">{r.name ?? '-'}</td>
                    <td className="px-4 py-3 text-sm font-semibold">{num(r.price)} <span className="text-gray-400 text-xs">{r.currency}</span></td>
                    <td className="px-4 py-3 text-sm">{r.change == null ? '-' : <span className={parseFloat(r.change) >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{num(r.change)}</span>}</td>
                    <td className="px-4 py-3 text-sm">{pct(r.changePercent)}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{num(r.dayHigh)}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{num(r.dayLow)}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{r.volume == null ? '-' : Number(r.volume).toLocaleString('tr-TR')}</td>
                    <td className="px-4 py-3 text-xs text-gray-400">{r.exchange ?? '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
