import { useEffect, useState } from 'react';
import { getFxTcmb } from '../../api/marketApi';

function num(v, dec = 4) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

export default function FxPage() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getFxTcmb().then(setData).catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`)).finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">Döviz Kurları</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">
        TCMB resmi döviz kurları
        {data?.asOf && <span className="ml-2 text-gray-400">· {data.asOf}</span>}
      </p>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {loading && <div className="p-8 text-center text-gray-400 text-sm">Yükleniyor...</div>}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}
        {!loading && !error && data?.rates && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>{['Döviz', 'Alış', 'Satış', 'Birim'].map(h =>
                  <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200">{h}</th>
                )}</tr>
              </thead>
              <tbody>
                {data.rates.map(r => (
                  <tr key={r.symbol} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{r.symbol}</td>
                    <td className="px-4 py-3 text-sm font-semibold">{num(r.buy)}</td>
                    <td className="px-4 py-3 text-sm font-semibold">{num(r.sell)}</td>
                    <td className="px-4 py-3 text-sm text-gray-400">{r.unit}</td>
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
