import { useEffect, useMemo, useState } from 'react';
import { getFunds } from '../../../api/marketApi';
import { useTranslation } from '../../../context/LanguageContext';
import Pagination from '../../../components/common/Pagination';
import WatchlistStar from '../../../components/instrument/WatchlistStar';

const PAGE_SIZE = 20;

function pct(v) {
  if (v == null) return <span className="text-gray-400">-</span>;
  const n = parseFloat(v);
  return <span className={n >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{n >= 0 ? '+' : ''}{n.toFixed(2)}%</span>;
}
function num(v, dec = 2) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

export default function FundsPage() {
  const { t } = useTranslation();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  useEffect(() => {
    getFunds(0, 50).then(r => setItems(r.content ?? [])).catch(e => setError(!e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})`)).finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    if (!search.trim()) return items;
    const q = search.toLowerCase();
    return items.filter(r => r.symbol?.toLowerCase().includes(q) || r.name?.toLowerCase().includes(q));
  }, [items, search]);

  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
  const paged = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">{t('Global Fonlar')}</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">{t('Küresel ETF ve yatırım fonları')}</p>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-gray-100 flex items-center gap-3 flex-wrap">
          <input
            type="text"
            placeholder={t('Sembol veya isim ara...')}
            value={search}
            onChange={e => { setSearch(e.target.value); setPage(0); }}
            className="px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] min-w-[240px]"
          />
          {search && (
            <button onClick={() => setSearch('')} className="px-3 py-2 border border-gray-200 rounded-xl text-sm hover:bg-gray-50">✕</button>
          )}
          <span className="text-xs text-gray-400 ml-auto">{filtered.length} {t('fon')}</span>
        </div>

        {loading && <div className="p-8 text-center text-gray-400 text-sm">{t('Yükleniyor...')}</div>}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}
        {!loading && !error && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>{['Sembol', 'Ad', 'Fiyat', 'Değişim', '%', 'Yüksek', 'Düşük', 'Hacim', 'Borsa'].map(h =>
                  <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider whitespace-nowrap border-b border-gray-200">{t(h)}</th>
                )}<th className="px-2 py-3 w-8 border-b border-gray-200" aria-label={t('İzle')} /></tr>
              </thead>
              <tbody>
                {paged.length === 0
                  ? <tr><td colSpan={10} className="px-4 py-8 text-center text-gray-400 text-sm">{t('Sonuç bulunamadı.')}</td></tr>
                  : paged.map(r => (
                    <tr key={r.symbol} className="border-t border-gray-100 hover:bg-blue-50 transition-colors">
                      <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{r.symbol}</td>
                      <td className="px-4 py-3 text-sm text-gray-700">{r.name ?? '-'}</td>
                      <td className="px-4 py-3 text-sm font-semibold">{num(r.price)} <span className="text-gray-400 text-xs">{r.currency}</span></td>
                      <td className="px-4 py-3 text-sm">{r.change == null ? '-' : <span className={parseFloat(r.change) >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{num(r.change)}</span>}</td>
                      <td className="px-4 py-3 text-sm">{pct(r.changePercent)}</td>
                      <td className="px-4 py-3 text-sm text-gray-600">{num(r.dayHigh)}</td>
                      <td className="px-4 py-3 text-sm text-gray-600">{num(r.dayLow)}</td>
                      <td className="px-4 py-3 text-sm text-gray-600">{r.volume == null ? '-' : Number(r.volume).toLocaleString('tr-TR')}</td>
                      <td className="px-4 py-3 text-xs text-gray-400">{r.exchange ?? '-'}</td>
                      <td className="px-2 py-3 text-center" onClick={e => e.stopPropagation()}>
                        <WatchlistStar assetType="FUND" symbol={r.symbol} name={r.name} price={r.price} />
                      </td>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        )}

        {!loading && !error && (
          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={filtered.length}
            unitLabel="fon"
            onChange={(p) => { setPage(p); window.scrollTo(0, 0); }}
          />
        )}
      </div>
    </div>
  );
}
