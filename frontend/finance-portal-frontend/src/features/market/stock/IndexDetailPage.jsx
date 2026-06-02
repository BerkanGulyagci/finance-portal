import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, TrendingUp, TrendingDown, BarChart2 } from 'lucide-react';
import { getIndex, getIndexConstituents } from '../../../api/marketApi';
import InstrumentLogo from '../../../components/instrument/InstrumentLogo';
import WatchlistStar from '../../../components/instrument/WatchlistStar';
import CandlestickChart from './components/CandlestickChart';
import LineChart from './components/LineChart';
import { useTranslation } from '../../../context/LanguageContext';

const CATEGORY_STYLE = {
  Ana: 'bg-[#093eaa]/10 text-[#093eaa]',
  Sektör: 'bg-violet-100 text-violet-700',
  Katılım: 'bg-emerald-100 text-emerald-700',
  Tema: 'bg-amber-100 text-amber-700',
};

const num = (v, dec = 2) =>
  v == null ? '-' : Number(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });

function pct(v) {
  if (v == null) return <span className="text-gray-400">-</span>;
  const n = Number(v);
  return (
    <span className={n >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>
      {n >= 0 ? '+' : ''}{n.toFixed(2)}%
    </span>
  );
}

export default function IndexDetailPage() {
  const { t } = useTranslation();
  const { code } = useParams();
  const upperCode = (code ?? '').toUpperCase();
  const symbol = `${upperCode}.IS`;

  const [index, setIndex] = useState(null);
  const [constituents, setConstituents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [chartMode, setChartMode] = useState('tv');

  useEffect(() => {
    setLoading(true);
    setError('');
    getIndex(upperCode)
      .then(setIndex)
      .catch(e => setError(!e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})`))
      .finally(() => setLoading(false));
  }, [upperCode]);

  useEffect(() => {
    let cancelled = false;
    setConstituents([]);
    getIndexConstituents(upperCode)
      .then(rows => { if (!cancelled) setConstituents(rows ?? []); })
      .catch(() => { if (!cancelled) setConstituents([]); });
    return () => { cancelled = true; };
  }, [upperCode]);

  const isPos = index?.changePercent != null && Number(index.changePercent) >= 0;

  return (
    <div className="max-w-4xl mx-auto space-y-3 sm:space-y-6">
      {/* Back + Title */}
      <div className="flex items-center gap-3">
        <Link to="/market/stocks?view=indices" className="text-gray-400 hover:text-[#093eaa] transition-colors">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="text-xl sm:text-2xl font-bold text-gray-900">{index?.name ?? upperCode}</h1>
            {index?.category && (
              <span className={`px-2 py-0.5 rounded-full text-xs font-bold ${CATEGORY_STYLE[index.category] || 'bg-gray-100 text-gray-600'}`}>
                {t(index.category)}
              </span>
            )}
          </div>
          <p className="text-xs text-gray-400 mt-0.5">
            {upperCode} · {t('Borsa İstanbul Endeksi')} · {t('Kaynak: Yahoo Finance')}
          </p>
        </div>
      </div>

      {loading && (
        <div className="flex items-center justify-center py-20">
          <div className="flex gap-2">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
        </div>
      )}

      {error && <div className="bg-red-50 border border-red-200 text-red-600 text-sm px-6 py-4 rounded-2xl">{error}</div>}

      {!loading && !error && (
        <>
          {/* ── Price + Chart ── */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
            <div className="grid grid-cols-2 sm:grid-cols-4 divide-x divide-gray-100">
              <div className="p-4 sm:p-6 col-span-2 sm:col-span-1">
                <p className="text-3xl sm:text-4xl font-bold text-gray-900">{num(index?.price)}</p>
                <p className="text-sm text-gray-400 mt-1">{t('Son Değer')}</p>
              </div>
              <div className="p-4 sm:p-6 flex flex-col justify-center">
                <span className={`text-lg font-bold flex items-center gap-1 ${isPos ? 'text-emerald-600' : 'text-rose-600'}`}>
                  {isPos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                  {pct(index?.changePercent)}
                </span>
                <p className="text-sm text-gray-400 mt-1">{t('Günlük')}</p>
              </div>
              <div className="p-4 sm:p-6 flex flex-col justify-center">
                <p className="text-base font-bold text-gray-900">{num(index?.dayHigh)}</p>
                <p className="text-sm text-gray-400 mt-1">{t('Yüksek')}</p>
              </div>
              <div className="p-4 sm:p-6 flex flex-col justify-center">
                <p className="text-base font-bold text-gray-900">{num(index?.dayLow)}</p>
                <p className="text-sm text-gray-400 mt-1">{t('Düşük')}</p>
              </div>
            </div>

            <div className="px-4 pt-2 pb-2 border-t border-gray-100">
              <div className="flex items-center justify-between gap-2 mb-3 flex-wrap">
                <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5">
                  {[{ key: 'tv', label: 'Mum Grafik' }, { key: 'line', label: 'Çizgi' }].map(m => (
                    <button key={m.key} onClick={() => setChartMode(m.key)}
                      className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${chartMode === m.key ? 'bg-white text-[#093eaa] shadow-sm' : 'text-gray-500 hover:text-gray-800'}`}>
                      {t(m.label)}
                    </button>
                  ))}
                </div>
                <Link
                  to={`/market/stocks/compare?add=${encodeURIComponent(symbol)}`}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold border border-[#093eaa]/30 text-[#093eaa] bg-[#093eaa]/5 hover:bg-[#093eaa]/10 transition-colors"
                >
                  <BarChart2 className="w-3.5 h-3.5" />
                  {t('Karşılaştır')}
                </Link>
              </div>

              {chartMode === 'tv' && <CandlestickChart symbol={symbol} />}
              {chartMode === 'line' && <LineChart symbol={symbol} />}
            </div>
          </div>

          {/* ── İlgili Hisseler ── */}
          {constituents.length > 0 && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
              <div className="p-4 border-b border-gray-100">
                <h2 className="text-base font-bold text-gray-900">{t('Endeksteki Hisseler')}</h2>
                <p className="text-xs text-gray-400 mt-0.5">{constituents.length} {t('hisse')}</p>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Sembol')}</th>
                      <th className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Şirket')}</th>
                      <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Fiyat')}</th>
                      <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">%</th>
                      <th className="px-2 py-3 w-8" aria-label={t('İzle')} />
                    </tr>
                  </thead>
                  <tbody>
                    {constituents.map(r => (
                      <tr key={r.symbol} className="border-t border-gray-100 hover:bg-blue-50 transition-colors">
                        <td className="px-4 py-3 font-bold text-sm">
                          <div className="flex items-center gap-2.5">
                            <InstrumentLogo symbol={r.symbol} name={r.name} size={22} />
                            <Link to={`/market/stocks/${r.symbol}`} className="text-[#093eaa] hover:underline">
                              {r.symbol?.replace('.IS', '')}
                            </Link>
                          </div>
                        </td>
                        <td className="px-4 py-3 text-sm text-gray-700">{r.name ?? '-'}</td>
                        <td className="px-4 py-3 text-sm font-semibold text-right">
                          {num(r.price)} <span className="text-gray-400 text-xs">{r.currency}</span>
                        </td>
                        <td className="px-4 py-3 text-sm text-right">{pct(r.changePercent)}</td>
                        <td className="px-2 py-3 text-center" onClick={e => e.stopPropagation()}>
                          <WatchlistStar assetType="STOCK" symbol={r.symbol} name={r.name} price={r.price} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
