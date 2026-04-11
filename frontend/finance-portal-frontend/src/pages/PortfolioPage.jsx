import { useEffect, useState } from 'react';
import { getMyPortfolios } from '../api/portfolioApi';
import { TrendingUp, TrendingDown, Plus } from 'lucide-react';

function fmt(value, fallback = '-') {
  if (value === null || value === undefined) return fallback;
  return typeof value === 'number' ? value.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : value;
}

export default function PortfolioPage() {
  const [portfolios, setPortfolios] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getMyPortfolios()
      .then(setPortfolios)
      .catch(err => {
        if (!err.response) setError('Sunucuya ulaşılamıyor.');
        else if (err.response.status === 401 || err.response.status === 403) setError('Bu sayfayı görüntülemek için giriş yapmanız gerekiyor.');
        else setError(`Portföyler yüklenemedi (${err.response.status}).`);
      })
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">Portföylerim</h1>
        <button className="flex items-center gap-2 bg-[#093eaa] text-white px-4 py-2 rounded-xl text-sm font-semibold hover:bg-[#093eaa]/90 transition-all">
          <Plus className="w-4 h-4" /> Yeni Portföy
        </button>
      </div>

      {loading && (
        <div className="bg-white rounded-2xl border border-gray-200 p-8 text-center">
          <div className="flex items-center justify-center gap-2">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
          <p className="text-gray-400 text-sm mt-3">Yükleniyor...</p>
        </div>
      )}

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-600 text-sm px-6 py-4 rounded-2xl">{error}</div>
      )}

      {!loading && !error && portfolios.length === 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center">
          <div className="w-16 h-16 bg-blue-50 rounded-full flex items-center justify-center mx-auto mb-4">
            <TrendingUp className="w-8 h-8 text-[#093eaa]" />
          </div>
          <h3 className="font-bold text-gray-900 mb-2">Henüz portföy yok</h3>
          <p className="text-gray-500 text-sm">İlk portföyünüzü oluşturun ve yatırımlarınızı takip edin.</p>
        </div>
      )}

      {!loading && !error && portfolios.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {portfolios.map(p => {
            const pnl = p.totalProfitLoss ?? 0;
            const isPos = pnl >= 0;
            return (
              <div key={p.id} className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 hover:shadow-md transition-shadow cursor-pointer">
                <div className="flex items-start justify-between mb-4">
                  <div>
                    <h3 className="font-bold text-gray-900">{p.name}</h3>
                    {p.description && <p className="text-xs text-gray-400 mt-0.5">{p.description}</p>}
                  </div>
                  <span className="text-xs font-bold bg-blue-50 text-[#093eaa] px-2 py-1 rounded-lg">{p.currency}</span>
                </div>

                <div className="space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-500">Toplam Maliyet</span>
                    <span className="font-semibold">{fmt(p.totalCost)}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-500">Piyasa Değeri</span>
                    <span className="font-semibold">{fmt(p.totalMarketValue)}</span>
                  </div>
                  <div className="flex justify-between text-sm pt-2 border-t border-gray-100">
                    <span className="text-gray-500">Kar / Zarar</span>
                    <span className={`font-bold flex items-center gap-1 ${isPos ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {isPos ? <TrendingUp className="w-3.5 h-3.5" /> : <TrendingDown className="w-3.5 h-3.5" />}
                      {fmt(pnl)}
                    </span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
