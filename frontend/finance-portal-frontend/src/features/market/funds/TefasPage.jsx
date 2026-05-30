import { useEffect, useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { BarChart2 } from 'lucide-react';
import { getAllTefasFunds, getAllBesFunds, getAllOksFunds, getOsmanliFundBulletin } from '../../../api/marketApi';
import { useSortable } from '../../../hooks/useSortable';
import SortableTh from '../../../components/common/SortableTh';
import Pagination from '../../../components/common/Pagination';
import WatchlistStar from '../../../components/instrument/WatchlistStar';
import { Dropdown } from '../../../components/shared/Dropdown';
import { useTranslation } from '../../../context/LanguageContext';

const PAGE_SIZE = 20;

const TABS = [
  { key: 'tefas',   label: 'TEFAS',          sub: 'Yatırım Fonları',    color: '#093eaa', border: 'border-[#093eaa]',   text: 'text-[#093eaa]',   bg: 'bg-[#093eaa]',   hover: 'hover:bg-blue-50'    },
  { key: 'bes',     label: 'BES',             sub: 'Bireysel Emeklilik', color: '#059669', border: 'border-emerald-600', text: 'text-emerald-600', bg: 'bg-emerald-600', hover: 'hover:bg-emerald-50' },
  { key: 'oks',     label: 'OKS',             sub: 'Otomatik Katılım',   color: '#7c3aed', border: 'border-violet-600',  text: 'text-violet-600',  bg: 'bg-violet-600',  hover: 'hover:bg-violet-50'  },
  { key: 'osmanli', label: 'Osmanlı Portföy', sub: 'Fon Bülteni',        color: '#d97706', border: 'border-amber-500',   text: 'text-amber-600',   bg: 'bg-amber-500',   hover: 'hover:bg-amber-50'   },
];

function toFloat(v) {
  if (v == null) return null;
  const n = parseFloat(v);
  return isNaN(n) ? null : n;
}

function fmtPct(v) {
  const n = toFloat(v);
  if (n == null) return '-';
  const cls = n >= 0 ? 'text-emerald-600' : 'text-rose-600';
  return <span className={`font-semibold ${cls}`}>{n >= 0 ? '+' : ''}{n.toFixed(2)}%</span>;
}

function RiskBadge({ level }) {
  if (level == null) return <span className="text-gray-300">-</span>;
  const colors = ['', 'bg-emerald-100 text-emerald-700', 'bg-green-100 text-green-700',
    'bg-yellow-100 text-yellow-700', 'bg-orange-100 text-orange-700',
    'bg-red-100 text-red-700', 'bg-rose-100 text-rose-700', 'bg-purple-100 text-purple-700'];
  return <span className={`px-1.5 py-0.5 rounded text-xs font-bold ${colors[level] ?? 'bg-gray-100 text-gray-600'}`}>{level}</span>;
}

function TypeBadge({ type }) {
  const { t } = useTranslation();
  if (!type) return null;
  const map = { Yatirim: 'bg-blue-100 text-blue-700', Bes: 'bg-emerald-100 text-emerald-700', Oks: 'bg-violet-100 text-violet-700', Serbest: 'bg-amber-100 text-amber-700' };
  const labels = { Yatirim: t('Yatırım'), Bes: 'BES', Oks: 'OKS', Serbest: t('Serbest') };
  return <span className={`px-2 py-0.5 rounded-full text-xs font-bold ${map[type] ?? 'bg-gray-100 text-gray-600'}`}>{labels[type] ?? type}</span>;
}

// ── Standart fon tablosu (TEFAS / BES / OKS) ─────────────────────────────────
function FundTable({ funds, accentColor, loading, error, showFounder = false }) {
  const { t } = useTranslation();
  const [search, setSearch]           = useState('');
  const [typeFilter, setTypeFilter]   = useState('');
  const [founderFilter, setFounderFilter] = useState('');
  const [page, setPage]               = useState(0);

  const fundTypes = useMemo(() => [...new Set(funds.map(f => f.fundType).filter(Boolean))].sort(), [funds]);
  const founders  = useMemo(() => [...new Set(funds.map(f => f.founderName).filter(Boolean))].sort(), [funds]);

  const filtered = useMemo(() => {
    let r = funds;
    if (search.trim()) {
      const q = search.toLowerCase();
      r = r.filter(f => f.code?.toLowerCase().includes(q) || f.name?.toLowerCase().includes(q) || f.managerName?.toLowerCase().includes(q) || f.founderName?.toLowerCase().includes(q));
    }
    if (typeFilter)    r = r.filter(f => f.fundType    === typeFilter);
    if (founderFilter) r = r.filter(f => f.founderName === founderFilter);
    return r;
  }, [funds, search, typeFilter, founderFilter]);

  const { sorted, sortKey, sortDir, handleSort } = useSortable(filtered, 'code', 'asc');
  const totalPages = Math.ceil(sorted.length / PAGE_SIZE);
  const paged = sorted.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const thProps = (key, label, align = 'left') => ({ label, sortKey: key, currentKey: sortKey, currentDir: sortDir, onSort: k => { handleSort(k); setPage(0); }, align, onColor: true });

  const headerBg = { backgroundColor: accentColor };

  if (loading) return (
    <div className="p-10 text-center">
      <div className="flex items-center justify-center gap-2 mb-2">
        <div className="w-2 h-2 rounded-full animate-bounce" style={{ backgroundColor: accentColor }} />
        <div className="w-2 h-2 rounded-full animate-bounce [animation-delay:100ms]" style={{ backgroundColor: accentColor + '99' }} />
        <div className="w-2 h-2 rounded-full animate-bounce [animation-delay:200ms]" style={{ backgroundColor: accentColor + '44' }} />
      </div>
      <p className="text-gray-400 text-sm">{t('Fonlar yükleniyor...')}</p>
    </div>
  );
  if (error) return <div className="p-6 text-rose-500 text-sm">{error}</div>;

  return (
    <>
      {/* Filtreler */}
      <div className="p-4 border-b border-gray-100 flex items-center gap-3 flex-wrap">
        <input type="text" placeholder={t('Fon kodu, adı veya yönetici ara...')}
          value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
          className="px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 min-w-[260px]"
          style={{ '--tw-ring-color': accentColor + '50' }} />
        {showFounder && founders.length > 0 && (
          <Dropdown
            className="w-52" menuWidth="w-64"
            value={founderFilter}
            options={[{ label: t('Tüm Şirketler'), value: '' }, ...founders.map(f => ({ label: f, value: f }))]}
            onChange={v => { setFounderFilter(v); setPage(0); }}
            placeholder={t('Tüm Şirketler')}
          />
        )}
        {fundTypes.length > 0 && (
          <Dropdown
            className="w-44" menuWidth="w-56"
            value={typeFilter}
            options={[{ label: t('Tüm Tipler'), value: '' }, ...fundTypes.map(ft => ({ label: ft, value: ft }))]}
            onChange={v => { setTypeFilter(v); setPage(0); }}
            placeholder={t('Tüm Tipler')}
          />
        )}
        <span className="ml-auto text-xs text-gray-400">
          {sorted.length > 0 ? `${sorted.length} ${t('kayıttan')} ${page * PAGE_SIZE + 1}–${Math.min((page + 1) * PAGE_SIZE, sorted.length)} ${t('arası')}` : `0 ${t('kayıt')}`}
        </span>
      </div>

      {/* Tablo */}
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr style={headerBg} className="text-white">
              <SortableTh {...thProps('code', t('Fon Kodu'))} className="text-white" />
              <SortableTh {...thProps('name', t('Fon Adı'))} className="text-white" />
              {showFounder && <SortableTh {...thProps('founderName', t('Şirket'))} className="text-white" />}
              <SortableTh {...thProps('fundType', t('Tip'))} className="text-white" />
              <SortableTh {...thProps('riskLevel', t('Risk'), 'center')} className="text-white" />
              <SortableTh {...thProps('price', t('Fiyat'), 'right')} className="text-white" />
              <SortableTh {...thProps('returnOneMonth', t('1 Ay %'), 'right')} className="text-white" />
              <SortableTh {...thProps('returnThreeMonths', t('3 Ay %'), 'right')} className="text-white" />
              <SortableTh {...thProps('returnOneYear', t('1 Yıl %'), 'right')} className="text-white" />
              <SortableTh {...thProps('returnThreeYears', t('3 Yıl %'), 'right')} className="text-white" />
              <th className="px-3 py-3 w-10 border-b border-black/10" />
              <th className="px-2 py-3 w-8 border-b border-black/10" aria-label={t('İzle')} />
            </tr>
          </thead>
          <tbody>
            {paged.length === 0 ? (
              <tr><td colSpan={showFounder ? 12 : 11} className="px-4 py-8 text-center text-gray-400 text-sm">{t('Sonuç bulunamadı.')}</td></tr>
            ) : paged.map((r, i) => (
              <tr key={r.code} className={`border-t border-gray-100 transition-colors ${i % 2 === 0 ? 'bg-white' : 'bg-gray-50/40'}`}
                style={{ '--hover-bg': accentColor + '10' }}
                onMouseEnter={e => e.currentTarget.style.backgroundColor = accentColor + '08'}
                onMouseLeave={e => e.currentTarget.style.backgroundColor = ''}>
                <td className="px-4 py-3">
                  <Link to={`/market/tefas/${r.code}`} state={{ listItem: r }}
                    className="font-bold text-sm hover:underline font-mono" style={{ color: accentColor }}>
                    {r.code}
                  </Link>
                </td>
                <td className="px-4 py-3 text-sm text-gray-800 max-w-[240px]">
                  <div className="truncate" title={r.name}>{r.name ?? '-'}</div>
                  {r.managerName && <div className="text-xs text-gray-400 truncate">{r.managerName}</div>}
                </td>
                {showFounder && <td className="px-4 py-3 text-xs text-gray-600 whitespace-nowrap">{r.founderName ?? '-'}</td>}
                <td className="px-4 py-3 text-xs text-gray-600 whitespace-nowrap">{r.fundType ?? '-'}</td>
                <td className="px-4 py-3 text-center"><RiskBadge level={r.riskLevel} /></td>
                <td className="px-4 py-3 text-sm text-right font-mono text-gray-700">
                  {toFloat(r.price) != null ? toFloat(r.price).toFixed(6) : '-'}
                </td>
                <td className="px-4 py-3 text-sm text-right">{fmtPct(r.returnOneMonth)}</td>
                <td className="px-4 py-3 text-sm text-right">{fmtPct(r.returnThreeMonths)}</td>
                <td className="px-4 py-3 text-sm text-right">{fmtPct(r.returnOneYear)}</td>
                <td className="px-4 py-3 text-sm text-right">{fmtPct(r.returnThreeYears)}</td>
                <td className="px-2 py-3">
                  <Link to={`/market/tefas/compare?codes=${r.code}`} onClick={e => e.stopPropagation()} title={t('Karşılaştır')}
                    className="p-1.5 rounded-lg bg-gray-100 text-gray-400 transition-all inline-flex hover:text-white"
                    onMouseEnter={e => { e.currentTarget.style.backgroundColor = accentColor; e.currentTarget.style.color = 'white'; }}
                    onMouseLeave={e => { e.currentTarget.style.backgroundColor = ''; e.currentTarget.style.color = ''; }}>
                    <BarChart2 className="w-3.5 h-3.5" />
                  </Link>
                </td>
                <td className="px-2 py-3 text-center" onClick={e => e.stopPropagation()}>
                  <WatchlistStar assetType="FUND" symbol={r.code} name={r.name} price={toFloat(r.price)} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Pagination
        page={page}
        totalPages={totalPages}
        totalElements={sorted.length}
        unitLabel="fon"
        onChange={(p) => setPage(p)}
      />
    </>
  );
}

// ── Osmanlı bülten tablosu ────────────────────────────────────────────────────
function OsmanliBulletinTable({ funds, loading, error }) {
  const { t } = useTranslation();
  const [search, setSearch]       = useState('');
  const [typeFilter, setTypeFilter]   = useState('');
  const [groupFilter, setGroupFilter] = useState('');

  const types  = useMemo(() => [...new Set(funds.map(f => f.type).filter(Boolean))].sort(), [funds]);
  const groups = useMemo(() => [...new Set(funds.map(f => f.group).filter(Boolean))].sort(), [funds]);

  const filtered = useMemo(() => {
    let r = funds;
    if (search.trim()) { const q = search.toLowerCase(); r = r.filter(f => f.code?.toLowerCase().includes(q) || f.name?.toLowerCase().includes(q) || f.group?.toLowerCase().includes(q)); }
    if (typeFilter)  r = r.filter(f => f.type  === typeFilter);
    if (groupFilter) r = r.filter(f => f.group === groupFilter);
    return r;
  }, [funds, search, typeFilter, groupFilter]);

  const { sorted, sortKey, sortDir, handleSort } = useSortable(filtered, 'code', 'asc');
  const thProps = (key, label, align = 'left') => ({ label, sortKey: key, currentKey: sortKey, currentDir: sortDir, onSort: handleSort, align, onColor: true });
  const AC = '#d97706';

  if (loading) return (
    <div className="p-10 text-center">
      <div className="flex items-center justify-center gap-2 mb-2">
        <div className="w-2 h-2 bg-amber-500 rounded-full animate-bounce" />
        <div className="w-2 h-2 bg-amber-500/60 rounded-full animate-bounce [animation-delay:100ms]" />
        <div className="w-2 h-2 bg-amber-500/30 rounded-full animate-bounce [animation-delay:200ms]" />
      </div>
      <p className="text-gray-400 text-sm">{t('Fon bülteni yükleniyor...')}</p>
    </div>
  );
  if (error) return <div className="p-6 text-rose-500 text-sm">{error}</div>;

  return (
    <>
      <div className="p-4 border-b border-gray-100 flex items-center gap-3 flex-wrap">
        <input type="text" placeholder={t('Fon kodu, adı veya grup ara...')}
          value={search} onChange={e => setSearch(e.target.value)}
          className="px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none min-w-[240px]" />
        {types.length > 0 && (
          <Dropdown
            className="w-44" menuWidth="w-56"
            value={typeFilter}
            options={[{ label: t('Tüm Tipler'), value: '' }, ...types.map(ty => ({ label: ty, value: ty }))]}
            onChange={v => setTypeFilter(v)}
            placeholder={t('Tüm Tipler')}
          />
        )}
        {groups.length > 0 && (
          <Dropdown
            className="w-52" menuWidth="w-64"
            value={groupFilter}
            options={[{ label: t('Tüm Gruplar'), value: '' }, ...groups.map(g => ({ label: g, value: g }))]}
            onChange={v => setGroupFilter(v)}
            placeholder={t('Tüm Gruplar')}
          />
        )}
        <span className="ml-auto text-xs text-gray-400">{sorted.length} {t('fon')}</span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="bg-amber-600 text-white">
              <SortableTh {...thProps('code', t('Kod'))} className="text-white" />
              <SortableTh {...thProps('name', t('Fon Adı'))} className="text-white" />
              <SortableTh {...thProps('group', t('Grup'))} className="text-white" />
              <SortableTh {...thProps('type', t('Tip'))} className="text-white" />
              <SortableTh {...thProps('riskLevel', t('Risk'), 'center')} className="text-white" />
              <SortableTh {...thProps('dailyReturn', t('Günlük %'), 'right')} className="text-white" />
              <SortableTh {...thProps('weeklyReturn', t('Haftalık %'), 'right')} className="text-white" />
              <SortableTh {...thProps('monthlyReturn', t('Aylık %'), 'right')} className="text-white" />
              <SortableTh {...thProps('yearlyReturn', t('Yıllık %'), 'right')} className="text-white" />
              <th className="px-3 py-3 border-b border-black/10 w-10" />
              <th className="px-2 py-3 border-b border-black/10 w-8" aria-label={t('İzle')} />
            </tr>
          </thead>
          <tbody>
            {sorted.length === 0 ? (
              <tr><td colSpan={11} className="px-4 py-8 text-center text-gray-400 text-sm">{t('Sonuç bulunamadı.')}</td></tr>
            ) : sorted.map((r, i) => (
              <tr key={r.code} className={`border-t border-gray-100 transition-colors ${i % 2 === 0 ? 'bg-white' : 'bg-gray-50/40'}`}
                onMouseEnter={e => e.currentTarget.style.backgroundColor = '#fef3c7'}
                onMouseLeave={e => e.currentTarget.style.backgroundColor = ''}>
                <td className="px-4 py-3">
                  <Link
                    to={`/market/tefas/${r.code}`}
                    state={{ listItem: { ...r, uniqueCode: r.uniqueCode } }}
                    className="font-bold text-amber-700 text-sm font-mono hover:underline">
                    {r.code}
                  </Link>
                </td>
                <td className="px-4 py-3 text-sm text-gray-800 max-w-[240px]"><div className="truncate" title={r.name}>{r.name ?? '-'}</div></td>
                <td className="px-4 py-3 text-xs text-gray-600 max-w-[160px]"><div className="truncate" title={r.group}>{r.group ?? '-'}</div></td>
                <td className="px-4 py-3"><TypeBadge type={r.type} /></td>
                <td className="px-4 py-3 text-center"><RiskBadge level={r.riskLevel} /></td>
                <td className="px-4 py-3 text-sm text-right">{fmtPct(r.dailyReturn)}</td>
                <td className="px-4 py-3 text-sm text-right">{fmtPct(r.weeklyReturn)}</td>
                <td className="px-4 py-3 text-sm text-right">{fmtPct(r.monthlyReturn)}</td>
                <td className="px-4 py-3 text-sm text-right">{fmtPct(r.yearlyReturn)}</td>
                <td className="px-2 py-3">
                  <Link to={`/market/tefas/compare?codes=${r.code}`}
                    onClick={e => e.stopPropagation()} title={t('Karşılaştır')}
                    className="p-1.5 rounded-lg bg-gray-100 text-gray-400 transition-all inline-flex hover:text-white"
                    onMouseEnter={e => { e.currentTarget.style.backgroundColor = '#d97706'; e.currentTarget.style.color = 'white'; }}
                    onMouseLeave={e => { e.currentTarget.style.backgroundColor = ''; e.currentTarget.style.color = ''; }}>
                    <BarChart2 className="w-3.5 h-3.5" />
                  </Link>
                </td>
                <td className="px-2 py-3 text-center" onClick={e => e.stopPropagation()}>
                  <WatchlistStar assetType="FUND" symbol={r.code} name={r.name} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

// ── Ana sayfa ─────────────────────────────────────────────────────────────────
export default function TefasPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('tefas');
  const [data, setData]   = useState({});
  const [loading, setLoading] = useState({});
  const [errors, setErrors]   = useState({});

  function fetchTab(key) {
    if (data[key] !== undefined) return; // cache hit
    setLoading(p => ({ ...p, [key]: true }));
    setErrors(p => ({ ...p, [key]: '' }));
    const fetchers = {
      tefas:   getAllTefasFunds,
      bes:     getAllBesFunds,
      oks:     getAllOksFunds,
      osmanli: getOsmanliFundBulletin,
    };
    fetchers[key]()
      .then(funds => setData(p => ({ ...p, [key]: Array.isArray(funds) ? funds : [] })))
      .catch(e => setErrors(p => ({ ...p, [key]: !e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})` })))
      .finally(() => setLoading(p => ({ ...p, [key]: false })));
  }

  // İlk tab'ı yükle
  useEffect(() => { fetchTab('tefas'); }, []);

  function handleTab(key) {
    setActiveTab(key);
    fetchTab(key);
  }

  const tab = TABS.find(tb => tb.key === activeTab);
  const funds = data[activeTab] ?? [];

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-1 border-l-4 pl-4" style={{ borderColor: tab.color }}>
        {t('Yatırım Fonları')}
      </h1>
      <p className="text-sm text-gray-500 mb-5 pl-5">{t('Kaynak: Rasyonet / YatırımDirekt')}</p>

      {/* Tab butonları — Material 3: aktif "filled" (yükseltili), pasif "outlined" + state-layer hover */}
      <div className="flex gap-2.5 mb-4 flex-wrap">
        {TABS.map(tb => {
          const count = data[tb.key]?.length;
          const isActive = activeTab === tb.key;
          return (
            <button key={tb.key} onClick={() => handleTab(tb.key)}
              className={`flex flex-col items-start px-5 py-2.5 rounded-xl text-left transition-all duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 ${
                isActive ? 'shadow-md hover:shadow-lg' : 'bg-white border border-gray-300 hover:shadow-sm'
              }`}
              style={isActive
                ? { backgroundColor: tb.color, '--tw-ring-color': tb.color + '66' }
                : { '--tw-ring-color': tb.color + '66' }}
              onMouseEnter={isActive ? undefined : e => { e.currentTarget.style.backgroundColor = tb.color + '14'; }}
              onMouseLeave={isActive ? undefined : e => { e.currentTarget.style.backgroundColor = ''; }}>
              <span className={`text-sm font-bold ${isActive ? 'text-white' : tb.text}`}>{tb.label}</span>
              <span className={`text-xs ${isActive ? 'text-white/80' : 'text-gray-500'}`}>
                {t(tb.sub)}{count != null ? ` · ${count} ${t('fon')}` : ''}
              </span>
            </button>
          );
        })}
      </div>

      {/* İçerik */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {activeTab === 'osmanli' ? (
          <OsmanliBulletinTable
            funds={funds}
            loading={!!loading[activeTab]}
            error={errors[activeTab] ?? ''}
          />
        ) : (
          <FundTable
            funds={funds}
            accentColor={tab.color}
            loading={!!loading[activeTab]}
            error={errors[activeTab] ?? ''}
            showFounder={activeTab === 'bes' || activeTab === 'oks'}
          />
        )}
      </div>
    </div>
  );
}
