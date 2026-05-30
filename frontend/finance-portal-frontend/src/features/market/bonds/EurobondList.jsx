import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getGlobalBonds } from '../../../api/marketApi';
import { useSortable } from '../../../hooks/useSortable';
import SortableTh from '../../../components/common/SortableTh';
import Pagination from '../../../components/common/Pagination';
import { Dropdown } from '../../../components/shared/Dropdown';
import { useTranslation } from '../../../i18n/LanguageContext';

const PAGE_SIZE = 20;

function num(v, dec = 2) {
  return v == null ? '-' : Number(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}
function pct(v) {
  if (v == null) return <span className="text-gray-400">-</span>;
  const n = Number(v);
  return <span className={n >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{n >= 0 ? '+' : ''}{n.toFixed(2)}%</span>;
}

/** Eurobond (Hazine dış borç) listesi — HMB ISIN + Business Insider canlı veri. */
export default function EurobondList() {
  const { t } = useTranslation();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [currency, setCurrency] = useState('');   // '' = Tümü
  const [page, setPage] = useState(0);

  useEffect(() => {
    setLoading(true);
    getGlobalBonds()
      .then(d => setItems(Array.isArray(d) ? d : []))
      .catch(e => setError(!e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})`))
      .finally(() => setLoading(false));
  }, []);

  const currencyOptions = useMemo(() => {
    const set = [...new Set(items.map(b => b.currency).filter(Boolean))].sort();
    return [{ label: t('Tüm Dövizler'), value: '' }, ...set.map(c => ({ label: c, value: c }))];
  }, [items, t]);

  const filtered = useMemo(() => {
    let r = items;
    if (currency) r = r.filter(b => b.currency === currency);
    if (search.trim()) {
      const q = search.toLowerCase();
      r = r.filter(b => b.isin?.toLowerCase().includes(q) || b.issuer?.toLowerCase().includes(q) || b.name?.toLowerCase().includes(q));
    }
    return r;
  }, [items, search, currency]);

  const { sorted, sortKey, sortDir, handleSort } = useSortable(filtered, 'maturityDate', 'asc');
  const totalPages = Math.ceil(sorted.length / PAGE_SIZE);
  const paged = sorted.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const thProps = (key, label, align = 'left') => ({
    label, sortKey: key, currentKey: sortKey, currentDir: sortDir,
    onSort: (k) => { handleSort(k); setPage(0); }, align,
  });

  const biDown = !loading && !error && items.length > 0 && items.every(b => !b.hasDetail);

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
      <div className="p-4 border-b border-gray-100 flex items-center gap-3 flex-wrap">
        <input
          type="text"
          placeholder={t('ISIN, ihraçcı veya isim ara...')}
          value={search}
          onChange={e => { setSearch(e.target.value); setPage(0); }}
          className="px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] min-w-[220px]"
        />
        <Dropdown
          value={currency}
          options={currencyOptions}
          onChange={(v) => { setCurrency(v); setPage(0); }}
          placeholder={t('Döviz')}
          menuWidth="w-44"
        />
        {(search || currency) && (
          <button onClick={() => { setSearch(''); setCurrency(''); }} className="px-3 py-2 border border-gray-200 rounded-xl text-sm hover:bg-gray-50">✕</button>
        )}
        <span className="text-xs text-gray-400 ml-auto">{sorted.length} {t('tahvil')}</span>
      </div>

      {biDown && (
        <div className="px-4 py-2 bg-amber-50 border-b border-amber-100 text-xs text-amber-700">
          {t('Canlı fiyat kaynağına şu an ulaşılamıyor — HMB künye listesi gösteriliyor.')}
        </div>
      )}

      {loading && (
        <div className="p-8 text-center">
          <div className="flex items-center justify-center gap-2 mb-2">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
          <p className="text-gray-400 text-sm">{t('Eurobond verileri yükleniyor...')}</p>
        </div>
      )}
      {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

      {!loading && !error && (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <SortableTh {...thProps('isin', 'ISIN')} />
                <SortableTh {...thProps('issuer', t('İhraçcı'))} />
                <SortableTh {...thProps('currency', t('Döviz'), 'center')} />
                <SortableTh {...thProps('couponRate', t('Kupon'), 'right')} />
                <SortableTh {...thProps('maturityDate', t('Vade'), 'right')} />
                <SortableTh {...thProps('lastPrice', t('Fiyat'), 'right')} />
                <SortableTh {...thProps('changePercent', '%', 'right')} />
              </tr>
            </thead>
            <tbody>
              {paged.length === 0
                ? <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-400 text-sm">{t('Sonuç bulunamadı.')}</td></tr>
                : paged.map(b => (
                  <tr key={b.isin} className="border-t border-gray-100 hover:bg-blue-50 transition-colors">
                    <td className="px-4 py-3 font-mono font-bold text-sm">
                      {b.hasDetail
                        ? <Link to={`/market/bonds/global/${encodeURIComponent(b.isin)}`} className="text-[#093eaa] hover:underline">{b.isin}</Link>
                        : <span className="text-gray-500" title={t('Canlı veri yok')}>{b.isin}</span>}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-700">
                      <div className="font-medium">{b.issuer ?? '-'}</div>
                      {b.name && <div className="text-xs text-gray-400 truncate max-w-[260px]" title={b.name}>{b.name}</div>}
                    </td>
                    <td className="px-4 py-3 text-sm text-center">
                      <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700">{b.currency ?? '-'}</span>
                    </td>
                    <td className="px-4 py-3 text-sm text-right font-semibold text-gray-800">{b.couponRate ?? '-'}</td>
                    <td className="px-4 py-3 text-sm text-right text-gray-600 whitespace-nowrap">{b.maturityDate ?? '-'}</td>
                    <td className="px-4 py-3 text-sm text-right font-semibold text-gray-900">{num(b.lastPrice)}</td>
                    <td className="px-4 py-3 text-sm text-right">{pct(b.changePercent)}</td>
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
          totalElements={sorted.length}
          unitLabel="tahvil"
          onChange={(p) => { setPage(p); window.scrollTo(0, 0); }}
        />
      )}
    </div>
  );
}
