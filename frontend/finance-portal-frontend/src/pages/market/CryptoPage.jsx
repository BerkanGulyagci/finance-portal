import { useEffect, useState } from 'react';
import { getCryptos } from '../../api/marketApi';

function pct(v) {
  if (v == null) return <span className="text-gray-400">-</span>;
  const n = parseFloat(v);
  return <span className={n >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{n >= 0 ? '+' : ''}{n.toFixed(2)}%</span>;
}
function num(v, dec = 2) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

export default function CryptoPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getCryptos(0, 100).then(setItems).catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`)).finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">Kripto Para</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">CoinGecko verilerine göre TRY bazlı kripto para fiyatları</p>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {loading && <div className="p-8 text-center text-gray-400 text-sm">Yükleniyor...</div>}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}
        {!loading && !error && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>{['#', 'Coin', 'Fiyat (TRY)', '24s %', '24s Yüksek', '24s Düşük', 'Hacim'].map(h =>
                  <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider whitespace-nowrap border-b border-gray-200">{h}</th>
                )}</tr>
              </thead>
              <tbody>
                {items.map(c => (
                  <tr key={c.id} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-gray-400 text-sm">{c.marketCapRank ?? '-'}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        {c.image && <img src={c.image} alt="" className="w-6 h-6 rounded-full" />}
                        <span className="font-bold text-gray-900 text-sm">{c.name}</span>
                        <span className="text-gray-400 text-xs">{c.symbol?.toUpperCase()}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 font-semibold text-sm">{num(c.currentPrice)}</td>
                    <td className="px-4 py-3 text-sm">{pct(c.priceChangePercentage24h)}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{num(c.high24h)}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{num(c.low24h)}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{num(c.totalVolume, 0)}</td>
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
