import { useEffect, useState, useMemo } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { getIndices } from '../../../api/marketApi';
import WatchlistStar from '../../../components/instrument/WatchlistStar';
import { useTranslation } from '../../../context/LanguageContext';

const CATEGORY_ORDER = ['Ana', 'Sektör', 'Katılım', 'Tema'];
const CATEGORY_LABEL = {
  Ana: 'Ana Endeksler',
  Sektör: 'Sektör Endeksleri',
  Katılım: 'Katılım Endeksleri',
  Tema: 'Tema Endeksleri',
};
const CATEGORY_DOT = {
  Ana: 'bg-[#093eaa]',
  Sektör: 'bg-violet-500',
  Katılım: 'bg-emerald-500',
  Tema: 'bg-amber-500',
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

export default function IndicesView() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [indices, setIndices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    getIndices()
      .then(d => setIndices(d ?? []))
      .catch(e => setError(!e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})`))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return indices;
    return indices.filter(i => i.code?.toLowerCase().includes(q) || i.name?.toLowerCase().includes(q));
  }, [indices, search]);

  const groups = useMemo(() => {
    const byCat = {};
    for (const i of filtered) (byCat[i.category] ??= []).push(i);
    return CATEGORY_ORDER.filter(c => byCat[c]?.length).map(c => ({ category: c, items: byCat[c] }));
  }, [filtered]);

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
      <div className="p-4 border-b border-gray-100 flex items-center gap-3 flex-wrap">
        <input
          type="text"
          placeholder={t('Endeks ara...')}
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] w-full sm:w-auto sm:min-w-[240px]"
        />
        {search && (
          <button onClick={() => setSearch('')}
            className="px-3 py-2 border border-gray-200 rounded-xl text-sm hover:bg-gray-50">✕</button>
        )}
        <span className="text-xs text-gray-400 ml-auto">{filtered.length} {t('endeks')}</span>
      </div>

      {loading && (
        <div className="p-4 sm:p-8 text-center">
          <div className="flex items-center justify-center gap-2 mb-2">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
          <p className="text-gray-400 text-sm">{t('Endeksler yükleniyor...')}</p>
        </div>
      )}
      {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

      {!loading && !error && (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Sembol')}</th>
                <th className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Endeks')}</th>
                <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Son Değer')}</th>
                <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">%</th>
                <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Yüksek')}</th>
                <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Düşük')}</th>
                <th className="px-2 py-3 w-8" aria-label={t('İzle')} />
              </tr>
            </thead>
            <tbody>
              {groups.length === 0 ? (
                <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-400 text-sm">{t('Sonuç bulunamadı.')}</td></tr>
              ) : (
                groups.map(g => (
                  <FragmentGroup key={g.category} group={g} navigate={navigate} t={t} />
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function FragmentGroup({ group, navigate, t }) {
  return (
    <>
      <tr className="bg-gray-50/60">
        <td colSpan={7} className="px-4 py-2">
          <span className="inline-flex items-center gap-2 text-xs font-bold text-gray-500 uppercase tracking-wider">
            <span className={`w-2 h-2 rounded-full ${CATEGORY_DOT[group.category] || 'bg-gray-400'}`} />
            {t(CATEGORY_LABEL[group.category] || group.category)}
          </span>
        </td>
      </tr>
      {group.items.map(i => (
        <tr key={i.code}
          onClick={() => navigate(`/market/indices/${i.code}`)}
          className="border-t border-gray-100 hover:bg-blue-50 transition-colors cursor-pointer">
          <td className="px-4 py-3 font-bold text-sm">
            <Link to={`/market/indices/${i.code}`} onClick={e => e.stopPropagation()} className="text-[#093eaa] hover:underline">
              {i.code}
            </Link>
          </td>
          <td className="px-4 py-3 text-sm text-gray-700">{i.name}</td>
          <td className="px-4 py-3 text-sm font-semibold text-right">{num(i.price)}</td>
          <td className="px-4 py-3 text-sm text-right">{pct(i.changePercent)}</td>
          <td className="px-4 py-3 text-sm text-gray-600 text-right">{num(i.dayHigh)}</td>
          <td className="px-4 py-3 text-sm text-gray-600 text-right">{num(i.dayLow)}</td>
          <td className="px-2 py-3 text-center" onClick={e => e.stopPropagation()}>
            <WatchlistStar assetType="STOCK" symbol={`${i.code}.IS`} name={i.name} price={i.price} watchlistOnly />
          </td>
        </tr>
      ))}
    </>
  );
}
