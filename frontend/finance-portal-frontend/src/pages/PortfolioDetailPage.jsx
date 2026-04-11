import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, TrendingUp, TrendingDown } from 'lucide-react';
import { getPortfolioById } from '../api/portfolioApi';

function fmt(v, dec = 2) {
  if (v == null) return '-';
  return typeof v === 'number' ? v.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec }) : v;
}

export default function PortfolioDetailPage() {
  const { id } = useParams();
  const [portfolio, setPortfolio] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getPortfolioById(id)
      .then(setPortfolio)
      .catch(err => setError(!err.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${err.response.status})`))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return (
    <div className="flex items-center justify-center py-20">
      <div className="flex gap-2">
        <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
        <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
        <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
      </div>
    </div>
  );

  if (error) return (
    <div className="bg-red-50 border border-red-200 text-red-600 text-sm px-6 py-4 rounded-2xl">{error}</div>
  );

  if (!portfolio) return null;

  const pnl = portfolio.totalProfitLoss ?? 0;
  const isPos = pnl >= 0;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Link to="/portfolio" className="text-gray-400 hover:text-[#093eaa] transition-colors">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <h1 className="text-2xl font-bold text-gray-900">{portfolio.name}</h1>
        <span className="text-xs font-bold bg-blue-50 text-[#093eaa] px-2 py-1 rounded-lg">{portfolio.currency}</span>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
          <p className="text-xs text-gray-500 font-semibold mb-1">Toplam Maliyet</p>
          <p className="text-xl font-bold text-gray-900">{fmt(portfolio.totalCost)}</p>
        </div>
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
          <p className="text-xs text-gray-500 font-semibold mb-1">Piyasa Değeri</p>
          <p className="text-xl font-bold text-gray-900">{fmt(portfolio.totalMarketValue)}</p>
        </div>
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
          <p className="text-xs text-gray-500 font-semibold mb-1">Kar / Zarar</p>
          <p className={`text-xl font-bold flex items-center gap-1 ${isPos ? 'text-emerald-600' : 'text-rose-600'}`}>
            {isPos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
            {fmt(pnl)}
          </p>
        </div>
      </div>

      {/* Holdings */}
      {portfolio.holdings?.length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100">
            <h2 className="font-bold text-gray-900">Varlıklar</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  {['Sembol', 'Tür', 'Miktar', 'Ort. Maliyet', 'Güncel Fiyat', 'Toplam Değer', 'K/Z'].map(h => (
                    <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {portfolio.holdings.map((h, i) => {
                  const hpnl = h.profitLoss ?? 0;
                  return (
                    <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{h.symbol}</td>
                      <td className="px-4 py-3 text-xs text-gray-400">{h.assetType}</td>
                      <td className="px-4 py-3 text-sm">{fmt(h.quantity, 4)}</td>
                      <td className="px-4 py-3 text-sm">{fmt(h.averageCost)}</td>
                      <td className="px-4 py-3 text-sm">{fmt(h.currentPrice)}</td>
                      <td className="px-4 py-3 text-sm font-semibold">{fmt(h.totalValue)}</td>
                      <td className={`px-4 py-3 text-sm font-bold ${hpnl >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>{fmt(hpnl)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Transactions */}
      {portfolio.transactions?.length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100">
            <h2 className="font-bold text-gray-900">İşlem Geçmişi</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  {['Tarih', 'Sembol', 'Tür', 'İşlem', 'Miktar', 'Fiyat', 'Komisyon'].map(h => (
                    <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {portfolio.transactions.map((t, i) => (
                  <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-xs text-gray-400">{t.transactionDate?.split('T')[0] ?? '-'}</td>
                    <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{t.symbol}</td>
                    <td className="px-4 py-3 text-xs text-gray-400">{t.assetType}</td>
                    <td className="px-4 py-3">
                      <span className={`text-xs font-bold px-2 py-0.5 rounded ${t.transactionType === 'BUY' ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'}`}>
                        {t.transactionType === 'BUY' ? 'ALIŞ' : 'SATIŞ'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm">{fmt(t.quantity, 4)}</td>
                    <td className="px-4 py-3 text-sm">{fmt(t.price)}</td>
                    <td className="px-4 py-3 text-sm text-gray-400">{fmt(t.commission)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
