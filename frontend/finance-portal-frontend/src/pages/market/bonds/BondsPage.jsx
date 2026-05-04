import { useEffect, useState, useCallback, useRef } from 'react';
import { Link } from 'react-router-dom';
import { ChevronUp, ChevronDown, ChevronsUpDown } from 'lucide-react';
import { getEvdsBonds } from '../../../api/marketApi';

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
  if (days == null) return <span className="text-gray-300">-</span>;
  const color =
    days <= 90  ? 'bg-amber-100 text-amber-700' :
    days <= 365 ? 'bg-blue-100 text-blue-700'   :
                  'bg-gray-100 text-gray-600';
  return (
    <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${color}`}>
      {days} gün
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

// ── Pagination ────────────────────────────────────────────────────────────────

function Pagination({ page, totalPages, totalItems, size, onChange }) {
  if (totalPages <= 1) return null;
  const from = page * size + 1;
  const to   = Math.min((page + 1) * size, totalItems);

  const pages = [];
  const start = Math.max(0, page - 2);
  const end   = Math.min(totalPages - 1, page + 2);
  for (let i = start; i <= end; i++) pages.push(i);

  return (
    <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100 flex-wrap gap-2">
      <p className="text-xs text-gray-400">
        <span className="font-semibold text-gray-600">{totalItems}</span> aktif kıymetten{' '}
        <span className="font-semibold text-gray-600">{from}–{to}</span> arası gösteriliyor
      </p>
      <div className="flex items-center gap-1">
        <button onClick={() => onChange(0)} disabled={page === 0}
          className="px-2 py-1 text-xs rounded border border-gray-200 disabled:opacity-40 hover:bg-gray-50">«</button>
        <button onClick={() => onChange(page - 1)} disabled={page === 0}
          className="px-2 py-1 text-xs rounded border border-gray-200 disabled:opacity-40 hover:bg-gray-50">‹</button>
        {pages.map(p => (
          <button key={p} onClick={() => onChange(p)}
            className={`px-3 py-1 text-xs rounded border ${p === page ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'border-gray-200 hover:bg-gray-50'}`}>
            {p + 1}
          </button>
        ))}
        <button onClick={() => onChange(page + 1)} disabled={page === totalPages - 1}
          className="px-2 py-1 text-xs rounded border border-gray-200 disabled:opacity-40 hover:bg-gray-50">›</button>
        <button onClick={() => onChange(totalPages - 1)} disabled={page === totalPages - 1}
          className="px-2 py-1 text-xs rounded border border-gray-200 disabled:opacity-40 hover:bg-gray-50">»</button>
      </div>
    </div>
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

// ── Ana bileşen ───────────────────────────────────────────────────────────────

export default function BondsPage() {
  // Filtre state'leri
  const [search,       setSearch]       = useState('');
  const [type,         setType]         = useState('');
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
      type,
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
      .catch(e => setError(!e.response ? 'TCMB EVDS verileri şu anda alınamadı.' : `Hata (${e.response.status})`))
      .finally(() => setLoading(false));
  }, [page, size, debouncedSearch, type, maturityIdx, sortBy, sortDir]);

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
        Tahvil / Bono
      </h1>
      <p className="text-sm text-gray-500 mb-5 pl-5">
        Devlet İç Borçlanma Senetleri (DİBS) · TCMB EVDS Gösterge Değerleri · Kaynak: TCMB EVDS
      </p>

      {/* ── Filtre paneli ── */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-4 mb-5">
        <div className="flex flex-wrap gap-3 items-end">
          {/* Arama */}
          <div className="flex-1 min-w-[180px] max-w-xs">
            <label className="block text-xs font-semibold text-gray-500 mb-1">Kıymet Kodu Ara</label>
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

          {/* Tür */}
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">Tür</label>
            <select
              value={type}
              onChange={e => { setType(e.target.value); setPage(0); }}
              className="px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 bg-white"
            >
              {TYPE_OPTIONS.map(t => (
                <option key={t} value={t}>{t || 'Tümü'}</option>
              ))}
            </select>
          </div>

          {/* Vade filtresi */}
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">Vade</label>
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
                  {f.label}
                </button>
              ))}
            </div>
          </div>

          {/* Sayfa boyutu */}
          <div className="ml-auto">
            <label className="block text-xs font-semibold text-gray-500 mb-1">Sayfa Boyutu</label>
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
            <p className="text-xs text-gray-400 mb-1">Toplam Aktif Kıymet</p>
            <p className="text-xl font-bold text-gray-900">{totalItems}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400 mb-1">Bu Sayfada</p>
            <p className="text-xl font-bold text-[#093eaa]">{items.length}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400 mb-1">Toplam Sayfa</p>
            <p className="text-xl font-bold text-gray-900">{totalPages}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400 mb-1">Kaynak</p>
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
            <p className="text-gray-400 text-sm">EVDS tahvil/bono verileri yükleniyor...</p>
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
                    <SortTh {...thProps('Kıymet Kodu', 'instrumentCode')} />
                    <th className="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Tür</th>
                    <SortTh {...thProps('Vade Tarihi', 'maturityDate')} />
                    <SortTh {...thProps('Kalan Gün', 'remainingDays', 'right')} />
                    <SortTh {...thProps('EVDS Gösterge Değeri', 'indicatorValue', 'right')} />
                    <SortTh {...thProps('Günlük Değişim %', 'dailyChangePercent', 'right')} />
                    <SortTh {...thProps('Kupon Faiz Oranı', 'couponRate', 'right')} />
                    <th className="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Kaynak</th>
                  </tr>
                </thead>
                <tbody>
                  {items.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="px-4 py-8 text-center text-gray-400 text-sm">
                        Gösterilecek EVDS tahvil/bono verisi bulunamadı.
                      </td>
                    </tr>
                  ) : items.map((b, i) => (
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
                        <span className="px-2 py-0.5 bg-indigo-50 text-indigo-700 rounded-full text-xs font-semibold">
                          {b.type ?? '-'}
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
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <Pagination
              page={page}
              totalPages={totalPages}
              totalItems={totalItems}
              size={size}
              onChange={p => setPage(p)}
            />

            <div className="px-4 py-2 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
              * Gösterge değerleri TCMB EVDS kaynaklıdır. Alış/satış fiyatı değildir. Yatırım tavsiyesi niteliği taşımaz.
            </div>
          </>
        )}
      </div>
    </div>
  );
}
