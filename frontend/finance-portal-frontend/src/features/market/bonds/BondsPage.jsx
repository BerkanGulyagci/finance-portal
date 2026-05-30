import { useEffect, useState, useCallback, useRef } from 'react';
import { Link } from 'react-router-dom';
import { ChevronUp, ChevronDown, ChevronsUpDown } from 'lucide-react';
import { getEvdsBonds, getEvdsBondCategoryCounts } from '../../../api/marketApi';
import { useTranslation } from '../../../context/LanguageContext';
import Pagination from '../../../components/common/Pagination';
import WatchlistStar from '../../../components/instrument/WatchlistStar';
import EurobondList from './components/EurobondList';

// ── Format yardımcıları ───────────────────────────────────────────────────────

function fmtNum(v, d = 2) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: d, maximumFractionDigits: d });
}

function fmtPct(v, d = 2) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  if (n > 0) return `+%${fmtNum(n, d)}`;
  if (n < 0) return `-%${fmtNum(Math.abs(n), d)}`;
  return `%${fmtNum(0, d)}`;
}

// ── Kalan gün badge ───────────────────────────────────────────────────────────

function DaysBadge({ days }) {
  const { t } = useTranslation();
  if (days == null) return <span className="text-gray-300">-</span>;
  const color =
    days <= 90  ? 'bg-amber-100 text-amber-700' :
    days <= 365 ? 'bg-blue-100 text-blue-700'   :
                  'bg-gray-100 text-gray-600';
  return (
    <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${color}`}>
      {days} {t('gün')}
    </span>
  );
}

// ── Değişim badge ─────────────────────────────────────────────────────────────

function ChangeBadge({ value }) {
  const n = value != null ? parseFloat(value) : null;
  if (n == null || isNaN(n)) return <span className="text-gray-300">-</span>;
  const pos = n >= 0;
  return (
    <span className={`text-xs font-semibold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
      {fmtPct(n, 2)}
    </span>
  );
}

// ── Sıralanabilir kolon başlığı ───────────────────────────────────────────────

function SortTh({ label, field, sortBy, sortDir, onSort, align = 'left' }) {
  const active = sortBy === field;
  return (
    <th
      className={`px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider cursor-pointer select-none hover:bg-gray-100 transition-colors ${align === 'right' ? 'text-right' : 'text-left'}`}
      onClick={() => onSort(field)}
    >
      <span className="inline-flex items-center gap-1">
        {label}
        {active
          ? sortDir === 'asc'
            ? <ChevronUp className="w-3 h-3 text-[#093eaa]" />
            : <ChevronDown className="w-3 h-3 text-[#093eaa]" />
          : <ChevronsUpDown className="w-3 h-3 text-gray-300" />
        }
      </span>
    </th>
  );
}

// ── Vade filtresi seçenekleri ─────────────────────────────────────────────────

const MATURITY_FILTERS = [
  { label: 'Tümü',      min: null, max: null },
  { label: '0–90 gün',  min: 0,    max: 90   },
  { label: '91–365 gün',min: 91,   max: 365  },
  { label: '365+ gün',  min: 366,  max: null },
];

const TYPE_OPTIONS = ['', 'DİBS', 'Devlet Tahvili', 'Hazine Bonosu'];
const SIZE_OPTIONS = [25, 50, 100];

// CBRT kod + ISIN suffix bazlı kategoriler (backend BondCategory ile birebir uyumlu)
const CATEGORY_LABELS = {
  ZERO_COUPON_BILL:              'Hazine Bonosu (Kuponsuz)',
  ZERO_COUPON_BOND:              'Devlet Tahvili (Kuponsuz)',
  FIXED_COUPON_BOND:             'Kuponlu Devlet Tahvili',
  PRINCIPAL_STRIP:               'Ana Para Stripi',
  COUPON_STRIP:                  'Kupon Stripi',
  TLREF_INDEXED_BOND:            'TLREF-Endeksli',
  INFLATION_INDEXED_BOND:        'TÜFE-Endeksli (Tam Bond)',
  INFLATION_PRINCIPAL_STRIP:     'TÜFE-Endeksli Ana Para Stripi',
  INFLATION_COUPON_STRIP:        'TÜFE-Endeksli Kupon Stripi',
  GOLD_INDEXED_BOND:             'Altına Dayalı Senet',
  FX_DENOMINATED_BOND:           'Yabancı Para Cinsli (EUR/USD)',
  LEASE_CERTIFICATE:             'Kira Sertifikası',
  INFLATION_INDEXED_LEASE_CERTIFICATE: 'TÜFE-Endeksli Kira Sertifikası',
  GOLD_INDEXED_LEASE_CERTIFICATE:'Altına Dayalı Kira Sertifikası',
  FX_LEASE_CERTIFICATE:          'Yabancı Para Cinsli Kira Sertifikası',
  UNKNOWN:                       'Sınıflandırılmamış',
};

const CATEGORY_FILTER_OPTIONS = [
  '',
  'ZERO_COUPON_BILL',
  'ZERO_COUPON_BOND',
  'FIXED_COUPON_BOND',
  'PRINCIPAL_STRIP',
  'COUPON_STRIP',
  'INFLATION_INDEXED_BOND',
  'INFLATION_PRINCIPAL_STRIP',
  'INFLATION_COUPON_STRIP',
  'TLREF_INDEXED_BOND',
  'GOLD_INDEXED_BOND',
  'FX_DENOMINATED_BOND',
  // Tier 3 — Kira Sertifikaları (Sukuk)
  'LEASE_CERTIFICATE',
  'INFLATION_INDEXED_LEASE_CERTIFICATE',
  'GOLD_INDEXED_LEASE_CERTIFICATE',
  'FX_LEASE_CERTIFICATE',
];

// ── Ana bileşen ───────────────────────────────────────────────────────────────

export default function BondsPage() {
  const { t } = useTranslation();
  const [tab, setTab] = useState('evds');   // 'evds' (DİBS) | 'global' (Eurobond)
  // Filtre state'leri
  const [search,       setSearch]       = useState('');
  const [category,     setCategory]     = useState('');  // BondCategory frontend filtresi
  const [maturityIdx,  setMaturityIdx]  = useState(0);  // MATURITY_FILTERS index
  const [sortBy,       setSortBy]       = useState('maturityDate');
  const [sortDir,      setSortDir]      = useState('asc');
  const [page,         setPage]         = useState(0);
  const [size,         setSize]         = useState(50);

  // Veri state'leri
  const [items,        setItems]        = useState([]);
  const [totalItems,   setTotalItems]   = useState(0);
  const [totalPages,   setTotalPages]   = useState(0);
  const [loading,      setLoading]      = useState(true);
  const [error,        setError]        = useState('');
  const [categoryCounts, setCategoryCounts] = useState({});  // { CATEGORY: count }

  // Kategori sayımlarını tek seferlik çek (cache'den hızlı gelir)
  useEffect(() => {
    getEvdsBondCategoryCounts().then(setCategoryCounts).catch(() => setCategoryCounts({}));
  }, []);

  // Arama debounce
  const searchTimer = useRef(null);
  const [debouncedSearch, setDebouncedSearch] = useState('');

  const handleSearchChange = (val) => {
    setSearch(val);
    clearTimeout(searchTimer.current);
    searchTimer.current = setTimeout(() => {
      setDebouncedSearch(val);
      setPage(0);
    }, 400);
  };

  // Veri çek
  const fetchData = useCallback(() => {
    const mf = MATURITY_FILTERS[maturityIdx];
    setLoading(true);
    setError('');
    getEvdsBonds({
      page,
      size,
      search: debouncedSearch,
      category,
      minRemainingDays: mf.min,
      maxRemainingDays: mf.max,
      sortBy,
      sortDir,
    })
      .then(data => {
        setItems(data.items ?? []);
        setTotalItems(data.totalItems ?? 0);
        setTotalPages(data.totalPages ?? 0);
      })
      .catch(e => setError(!e.response ? t('TCMB EVDS verileri şu anda alınamadı.') : `${t('Hata')} (${e.response.status})`))
      .finally(() => setLoading(false));
  }, [page, size, debouncedSearch, category, maturityIdx, sortBy, sortDir]);

  useEffect(() => { fetchData(); }, [fetchData]);

  // Sıralama toggle
  const handleSort = (field) => {
    if (sortBy === field) {
      setSortDir(d => d === 'asc' ? 'desc' : 'asc');
    } else {
      setSortBy(field);
      setSortDir('asc');
    }
    setPage(0);
  };

  const thProps = (label, field, align = 'left') => ({
    label, field, sortBy, sortDir, onSort: handleSort, align,
  });

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">
        {t('Tahvil / Bono')}
      </h1>
      {/* ── Sekmeler ── */}
      <div className="flex gap-2 mb-5 flex-wrap">
        <button onClick={() => setTab('evds')}
          className={`px-4 py-2 rounded-xl text-sm font-bold transition-all ${tab === 'evds' ? 'bg-[#093eaa] text-white shadow-sm' : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'}`}>
          {t('Devlet İç Borç (DİBS)')}
        </button>
        <button onClick={() => setTab('global')}
          className={`px-4 py-2 rounded-xl text-sm font-bold transition-all ${tab === 'global' ? 'bg-[#093eaa] text-white shadow-sm' : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'}`}>
          {t('Eurobond (Dış Borç)')}
        </button>
      </div>

      {tab === 'global' && (
        <>
          <p className="text-sm text-gray-500 mb-5 pl-5">
            {t('Hazine dış borç tahvilleri (Eurobond) · Kaynak: HMB ISIN listesi + Business Insider')}
          </p>
          <EurobondList />
        </>
      )}

      {tab === 'evds' && (
      <>
      <p className="text-sm text-gray-500 mb-5 pl-5">
        {t('Devlet İç Borçlanma Senetleri (DİBS) · TCMB EVDS Gösterge Değerleri · Kaynak: TCMB EVDS')}
      </p>

      {/* ── Filtre paneli ── */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-4 mb-5">
        <div className="flex flex-wrap gap-3 items-end">
          {/* Arama */}
          <div className="flex-1 min-w-[180px] max-w-xs">
            <label className="block text-xs font-semibold text-gray-500 mb-1">{t('Kıymet Kodu Ara')}</label>
            <div className="relative">
              <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              <input
                type="text"
                placeholder="TRD07..."
                value={search}
                onChange={e => handleSearchChange(e.target.value)}
                className="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]"
              />
            </div>
          </div>

          {/* Kategori (CBRT kodu bazlı detaylı tasnif) */}
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">{t('Kategori')}</label>
            <select
              value={category}
              onChange={e => { setCategory(e.target.value); setPage(0); }}
              className="px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 bg-white"
            >
              {CATEGORY_FILTER_OPTIONS.map(opt => {
                const cnt = opt ? (categoryCounts[opt] ?? 0) : Object.values(categoryCounts).reduce((a, b) => a + b, 0);
                return (
                  <option key={opt || 'all'} value={opt} disabled={opt && cnt === 0}>
                    {opt ? t(CATEGORY_LABELS[opt]) : t('Tüm Kategoriler')} ({cnt})
                  </option>
                );
              })}
            </select>
          </div>

          {/* Vade filtresi */}
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">{t('Vade')}</label>
            <div className="flex gap-1">
              {MATURITY_FILTERS.map((f, i) => (
                <button
                  key={i}
                  onClick={() => { setMaturityIdx(i); setPage(0); }}
                  className={`px-3 py-2 text-xs font-semibold rounded-lg border transition-all ${
                    maturityIdx === i
                      ? 'bg-[#093eaa] text-white border-[#093eaa]'
                      : 'bg-gray-50 text-gray-600 border-gray-200 hover:border-[#093eaa] hover:text-[#093eaa]'
                  }`}
                >
                  {t(f.label)}
                </button>
              ))}
            </div>
          </div>

          {/* Sayfa boyutu */}
          <div className="ml-auto">
            <label className="block text-xs font-semibold text-gray-500 mb-1">{t('Sayfa Boyutu')}</label>
            <select
              value={size}
              onChange={e => { setSize(Number(e.target.value)); setPage(0); }}
              className="px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 bg-white"
            >
              {SIZE_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
        </div>
      </div>

      {/* ── Stats ── */}
      {!loading && !error && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-5">
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400 mb-1">{t('Toplam Aktif Kıymet')}</p>
            <p className="text-xl font-bold text-gray-900">{totalItems}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400 mb-1">{t('Bu Sayfada')}</p>
            <p className="text-xl font-bold text-[#093eaa]">{items.length}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400 mb-1">{t('Toplam Sayfa')}</p>
            <p className="text-xl font-bold text-gray-900">{totalPages}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400 mb-1">{t('Kaynak')}</p>
            <p className="text-sm font-bold text-gray-900">TCMB EVDS</p>
          </div>
        </div>
      )}

      {/* ── Tablo ── */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {loading && (
          <div className="p-8 text-center">
            <div className="flex items-center justify-center gap-2 mb-2">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
            <p className="text-gray-400 text-sm">{t('EVDS tahvil/bono verileri yükleniyor...')}</p>
          </div>
        )}

        {error && (
          <div className="p-6 text-center">
            <p className="text-rose-500 text-sm">{error}</p>
          </div>
        )}

        {!loading && !error && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <SortTh {...thProps(t('Kıymet Kodu'), 'instrumentCode')} />
                    <th className="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Kategori')}</th>
                    <SortTh {...thProps(t('Vade Tarihi'), 'maturityDate')} />
                    <SortTh {...thProps(t('Kalan Gün'), 'remainingDays', 'right')} />
                    <SortTh {...thProps(t('EVDS Gösterge Değeri'), 'indicatorValue', 'right')} />
                    <SortTh {...thProps(t('Günlük Değişim %'), 'dailyChangePercent', 'right')} />
                    <SortTh {...thProps(t('Kupon Faiz Oranı'), 'couponRate', 'right')} />
                    <th className="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Kaynak')}</th>
                    <th className="px-2 py-3 w-8" aria-label={t('İzle')} />
                  </tr>
                </thead>
                <tbody>
                  {(() => {
                    const view = items; // backend zaten category ile filtreliyor
                    if (view.length === 0) {
                      return (
                        <tr>
                          <td colSpan={9} className="px-4 py-8 text-center text-gray-400 text-sm">
                            {t('Gösterilecek EVDS tahvil/bono verisi bulunamadı.')}
                          </td>
                        </tr>
                      );
                    }
                    return view.map((b, i) => (
                    <tr key={i} className="border-t border-gray-100 hover:bg-blue-50 transition-colors">
                      <td className="px-4 py-3 font-bold text-[#093eaa] text-sm font-mono">
                        <Link
                          to={`/market/bonds/${encodeURIComponent(b.instrumentCode)}`}
                          state={{ bond: b }}
                          className="hover:underline"
                        >
                          {b.instrumentCode}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-sm">
                        <span
                          className="px-2 py-0.5 bg-indigo-50 text-indigo-700 rounded-full text-xs font-semibold"
                          title={b.cbrtCode ? `${t('CBRT kodu')}: ${b.cbrtCode}` : (b.type ?? '')}
                        >
                          {b.category && CATEGORY_LABELS[b.category]
                            ? t(CATEGORY_LABELS[b.category])
                            : (b.type ?? '-')}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-700 whitespace-nowrap">{b.maturityDate ?? '-'}</td>
                      <td className="px-4 py-3 text-right">
                        <DaysBadge days={b.remainingDays} />
                      </td>
                      <td className="px-4 py-3 text-sm text-right font-mono font-semibold text-gray-900">
                        {fmtNum(b.indicatorValue, 2)}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <ChangeBadge value={b.dailyChangePercent} />
                      </td>
                      <td className="px-4 py-3 text-sm text-right text-gray-700">
                        {b.couponRate != null ? `%${fmtNum(b.couponRate, 2)}` : <span className="text-gray-300">-</span>}
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-400 whitespace-nowrap">
                        {b.source ?? 'TCMB EVDS'}
                      </td>
                      <td className="px-2 py-3 text-center" onClick={e => e.stopPropagation()}>
                        <WatchlistStar assetType="BOND" symbol={b.instrumentCode} name={b.type} price={b.indicatorValue} />
                      </td>
                    </tr>
                    ));
                  })()}
                </tbody>
              </table>
            </div>

            <Pagination
              page={page}
              totalPages={totalPages}
              totalElements={totalItems}
              unitLabel="kıymet"
              onChange={p => setPage(p)}
            />

            <div className="px-4 py-2 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
              {t('* Gösterge değerleri TCMB EVDS kaynaklıdır. Alış/satış fiyatı değildir. Yatırım tavsiyesi niteliği taşımaz.')}
            </div>
          </>
        )}
      </div>
      </>
      )}
    </div>
  );
}
