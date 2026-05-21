import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { TrendingUp, TrendingDown, Newspaper, BarChart3, Wallet } from 'lucide-react';
import { getMyPortfolios } from '../api/portfolioApi';
import { getCryptos, getFxTcmb } from '../api/marketApi';
import { getBloombergHtNews, proxyImageUrl } from '../api/newsApi';
import { useAuth } from '../context/AuthContext';
import { useTranslation } from '../i18n/LanguageContext';

function StatCard({ icon: Icon, label, value, sub, color = 'text-[#093eaa]' }) {
  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <div className="flex items-center gap-3 mb-3">
        <div className="w-10 h-10 bg-blue-50 rounded-xl flex items-center justify-center">
          <Icon className={`w-5 h-5 ${color}`} />
        </div>
        <span className="text-sm font-semibold text-gray-500">{label}</span>
      </div>
      <p className="text-2xl font-bold text-gray-900">{value}</p>
      {sub && <p className="text-xs text-gray-400 mt-1">{sub}</p>}
    </div>
  );
}

export default function DashboardPage() {
  const { t } = useTranslation();
  const { isAuthenticated } = useAuth();
  const [portfolios, setPortfolios] = useState([]);
  const [cryptos, setCryptos] = useState([]);
  const [fx, setFx] = useState(null);
  const [news, setNews] = useState([]);

  useEffect(() => {
    if (isAuthenticated) {
      getMyPortfolios().then(setPortfolios).catch(() => {});
    }
    getCryptos(0, 5).then(setCryptos).catch(() => {});
    getFxTcmb().then(setFx).catch(() => {});
    getBloombergHtNews().then(n => setNews(n.slice(0, 4))).catch(() => {});
  }, [isAuthenticated]);

  const usd = fx?.rates?.find(r => r.symbol === 'USD');
  const eur = fx?.rates?.find(r => r.symbol === 'EUR');

  const totalValue = portfolios.reduce((s, p) => s + (p.totalMarketValue ?? 0), 0);
  const totalPnl = portfolios.reduce((s, p) => s + (p.totalProfitLoss ?? 0), 0);

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">{t('Dashboard')}</h1>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard icon={Wallet} label={t('Toplam Portföy Değeri')}
          value={isAuthenticated ? totalValue.toLocaleString('tr-TR', { minimumFractionDigits: 2 }) : '—'}
          sub={isAuthenticated ? t('{count} portföy', { count: portfolios.length }) : t('Giriş yapın')} />
        <StatCard icon={TrendingUp} label={t('Toplam Kar/Zarar')}
          value={isAuthenticated ? totalPnl.toLocaleString('tr-TR', { minimumFractionDigits: 2 }) : '—'}
          color={totalPnl >= 0 ? 'text-emerald-600' : 'text-rose-600'} />
        <StatCard icon={BarChart3} label="USD/TRY"
          value={usd ? usd.sell.toLocaleString('tr-TR', { minimumFractionDigits: 2 }) : '—'}
          sub={t('TCMB satış kuru')} />
        <StatCard icon={BarChart3} label="EUR/TRY"
          value={eur ? eur.sell.toLocaleString('tr-TR', { minimumFractionDigits: 2 }) : '—'}
          sub={t('TCMB satış kuru')} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Top Cryptos */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900">{t('Kripto Piyasası')}</h2>
            <Link to="/market" className="text-xs text-[#093eaa] font-semibold hover:underline">{t('Tümü →')}</Link>
          </div>
          <div className="space-y-3">
            {cryptos.length === 0 && <p className="text-sm text-gray-400">{t('Yükleniyor...')}</p>}
            {cryptos.map(c => {
              const pct = parseFloat(c.priceChangePercentage24h ?? 0);
              return (
                <div key={c.id} className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    {c.image && <img src={c.image} alt="" className="w-6 h-6 rounded-full" />}
                    <div>
                      <p className="text-sm font-semibold text-gray-900">{c.name}</p>
                      <p className="text-xs text-gray-400">{c.symbol?.toUpperCase()}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-semibold">{c.currentPrice?.toLocaleString('tr-TR', { minimumFractionDigits: 2 })} ₺</p>
                    <p className={`text-xs font-semibold flex items-center justify-end gap-0.5 ${pct >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {pct >= 0 ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                      {Math.abs(pct).toFixed(2)}%
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Latest News */}
        <div className="lg:col-span-2 bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <Newspaper className="w-4 h-4 text-[#093eaa]" /> {t('Son Haberler')}
            </h2>
            <Link to="/news" className="text-xs text-[#093eaa] font-semibold hover:underline">{t('Tümü →')}</Link>
          </div>
          <div className="space-y-4">
            {news.length === 0 && <p className="text-sm text-gray-400">{t('Yükleniyor...')}</p>}
            {news.map((item, i) => (
              <div key={i} className={`flex gap-3 ${i > 0 ? 'border-t border-gray-100 pt-4' : ''}`}>
                {item.imageUrl && (
                  <img src={proxyImageUrl(item.imageUrl)} alt="" className="w-16 h-16 rounded-lg object-cover flex-shrink-0"
                    onError={e => { e.target.style.display = 'none'; }} />
                )}
                <div>
                  <p className="text-sm font-semibold text-gray-900 leading-snug hover:text-[#093eaa] transition-colors">
                    {item.url
                      ? <a href={item.url} target="_blank" rel="noopener noreferrer">{item.title}</a>
                      : item.title}
                  </p>
                  <p className="text-xs text-gray-400 mt-1">{item.source}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Portfolios preview */}
      {isAuthenticated && portfolios.length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-bold text-gray-900">{t('Portföylerim')}</h2>
            <Link to="/portfolio" className="text-xs text-[#093eaa] font-semibold hover:underline">{t('Tümü →')}</Link>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {portfolios.slice(0, 3).map(p => {
              const pnl = p.totalProfitLoss ?? 0;
              return (
                <div key={p.id} className="border border-gray-100 rounded-xl p-4">
                  <p className="font-bold text-gray-900 mb-2">{p.name}</p>
                  <div className="flex justify-between text-xs text-gray-500">
                    <span>{t('Değer')}</span>
                    <span className="font-semibold text-gray-800">{(p.totalMarketValue ?? 0).toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</span>
                  </div>
                  <div className="flex justify-between text-xs mt-1">
                    <span className="text-gray-500">{t('K/Z')}</span>
                    <span className={`font-bold ${pnl >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {pnl.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
