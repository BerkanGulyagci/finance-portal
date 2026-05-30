import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { BarChart2, Search, X, ArrowRight, Banknote, Landmark, Globe2 } from 'lucide-react';
import { getFxTcmb, getFxOpen, getBankCurrencyRates, getBankCurrencyRatesByCurrency } from '../../../api/marketApi.js';
import { useSortable } from '../../../hooks/useSortable.js';
import SortableTh from '../../../components/common/SortableTh.jsx';
import WatchlistStar from '../../../components/instrument/WatchlistStar';
import { FX_META, FlagImg } from './utils/fxMeta';
import { useTranslation } from '../../../context/LanguageContext';

function num(v, dec = 4) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

// Değere göre otomatik ondalık basamak — küçük değerler (JPY gibi) için daha fazla basamak
function numAuto(v) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  if (n < 1) return n.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
  return n.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
}

function numChange(v) {
  if (v == null) return '-';
  const n = parseFloat(v);
  const color = n > 0 ? 'text-emerald-600' : n < 0 ? 'text-rose-500' : 'text-gray-500';
  const sign = n > 0 ? '+' : '';
  return <span className={color}>{sign}{n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%</span>;
}

function formatUpdatedAt(isoStr) {
  if (!isoStr) return '-';
  try {
    return new Date(isoStr).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '-';
  }
}

// TCMB resmi bülten tarzı iki satırlı başlık: Türkçe terim + İngilizce kaynak terimi
function FxColLabel({ tr, en, align = 'right' }) {
  return (
    <span className={`inline-flex flex-col leading-tight ${align === 'right' ? 'items-end' : 'items-start'}`}>
      <span>{tr}</span>
      <span className="text-[10px] font-medium normal-case text-[#9aa6b6]">{en}</span>
    </span>
  );
}

const OPEN_BASES = ['USD', 'EUR', 'GBP', 'TRY'];
const BANK_CURRENCIES = ['Tümü', 'USD', 'EUR', 'GBP', 'CHF', 'JPY', 'SAR', 'NOK', 'DKK', 'AUD', 'CAD', 'SEK'];
const POPULAR = ['USD', 'EUR', 'GBP', 'CHF', 'JPY', 'SAR'];

// Material 3 — yükleme noktaları
function Dots() {
  return (
    <div className="flex justify-center gap-1.5 p-10">
      <div className="w-2.5 h-2.5 bg-[#093eaa] rounded-full animate-bounce" />
      <div className="w-2.5 h-2.5 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:120ms]" />
      <div className="w-2.5 h-2.5 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:240ms]" />
    </div>
  );
}

// Popüler döviz kartı (Material 3 — surface-container, elevation, state-layer)
function PopularCard({ rate, onClick }) {
  const meta = FX_META[rate.symbol];
  return (
    <button
      onClick={onClick}
      className="m3-state group text-left rounded-3xl bg-white border border-[#e2e8f0] p-4 min-w-[160px] flex-1
                 shadow-[0_1px_2px_rgba(15,23,42,0.06)] hover:shadow-[0_6px_18px_-6px_rgba(9,62,170,0.30)]
                 hover:-translate-y-0.5 transition-all duration-300 text-[#093eaa]"
    >
      <div className="flex items-center gap-2 mb-3">
        <span className="rounded-full ring-2 ring-[#dbe6ff] overflow-hidden inline-flex">
          <FlagImg cc={meta?.cc} size={26} />
        </span>
        <div className="leading-tight">
          <p className="font-extrabold text-[#1a1c1e] text-sm">{rate.symbol}<span className="text-[#5a6472] font-semibold">/TRY</span></p>
          <p className="text-[11px] text-[#5a6472] truncate max-w-[110px]">{meta?.ad ?? rate.symbol}</p>
        </div>
      </div>
      <p className="text-2xl font-black text-[#1a1c1e] tabular-nums tracking-tight">{num(rate.sell)}</p>
      <div className="flex items-center justify-between mt-1">
        <span className="text-[11px] text-[#5a6472]">{num(rate.buy)}</span>
        <ArrowRight className="w-4 h-4 text-[#9aa6b6] group-hover:text-[#093eaa] group-hover:translate-x-0.5 transition-all" />
      </div>
    </button>
  );
}

export default function FxPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('tcmb');
  const [searchParams] = useSearchParams();
  const [query, setQuery] = useState('');

  // URL'den tab parametresi oku (?tab=banks gibi)
  useEffect(() => {
    const tab = searchParams.get('tab');
    if (tab === 'banks' || tab === 'open' || tab === 'tcmb') {
      setActiveTab(tab);
    }
  }, [searchParams]);

  // TCMB
  const [tcmbData, setTcmbData] = useState(null);

  // Open FX
  const [openData, setOpenData] = useState(null);
  const [openBase, setOpenBase] = useState('USD');

  // Banka kurları
  const [bankRates, setBankRates] = useState([]);
  const [bankCurrency, setBankCurrency] = useState('Tümü');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  // TCMB — sayfa açılışında yükle
  useEffect(() => {
    setLoading(true);
    getFxTcmb()
      .then(setTcmbData)
      .catch(e => setError(!e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})`))
      .finally(() => setLoading(false));
  }, []);

  // Open FX — sekme açıldığında yükle
  useEffect(() => {
    if (activeTab !== 'open') return;
    setLoading(true);
    setError('');
    getFxOpen(openBase)
      .then(setOpenData)
      .catch(e => setError(!e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})`))
      .finally(() => setLoading(false));
  }, [activeTab, openBase]);

  // Banka kurları — sekme açıldığında veya döviz filtresi değiştiğinde yükle
  useEffect(() => {
    if (activeTab !== 'banks') return;
    setLoading(true);
    setError('');
    const fetchFn = bankCurrency === 'Tümü'
      ? getBankCurrencyRates()
      : getBankCurrencyRatesByCurrency(bankCurrency);
    fetchFn
      .then(setBankRates)
      .catch(e => setError(!e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})`))
      .finally(() => setLoading(false));
  }, [activeTab, bankCurrency]);

  const tcmbRates = tcmbData?.rates ?? [];
  const openRates = openData?.rates ?? [];

  const { sorted: sortedTcmb, sortKey: tcmbSortKey, sortDir: tcmbSortDir, handleSort: handleTcmbSort } = useSortable(tcmbRates, 'symbol', 'asc');
  const { sorted: sortedOpen, sortKey: openSortKey, sortDir: openSortDir, handleSort: handleOpenSort } = useSortable(openRates, 'symbol', 'asc');
  const { sorted: sortedBanks, sortKey: bankSortKey, sortDir: bankSortDir, handleSort: handleBankSort } = useSortable(bankRates, 'bankName', 'asc');

  // ── Arama filtresi (aktif sekmeye uygulanır) ──────────────────────────────
  const q = query.trim().toLowerCase();
  const matchFx = (sym) => {
    if (!q) return true;
    const meta = FX_META[sym];
    return sym.toLowerCase().includes(q) || (meta?.ad ?? '').toLowerCase().includes(q);
  };
  const filteredTcmb = useMemo(() => sortedTcmb.filter(r => matchFx(r.symbol)), [sortedTcmb, q]);
  const filteredOpen = useMemo(() => sortedOpen.filter(r => matchFx(r.symbol)), [sortedOpen, q]);
  const filteredBanks = useMemo(() => sortedBanks.filter(r => {
    if (!q) return true;
    return (r.bankName ?? '').toLowerCase().includes(q)
      || (r.currencyCode ?? '').toLowerCase().includes(q)
      || (r.currencyName ?? '').toLowerCase().includes(q);
  }), [sortedBanks, q]);

  // Popüler kartlar — TCMB verisinden
  const popularRates = useMemo(() => {
    const map = new Map(tcmbRates.map(r => [r.symbol, r]));
    return POPULAR.map(s => map.get(s)).filter(Boolean);
  }, [tcmbRates]);

  const tcmbTh = (key, label, align = 'left') => ({ label, sortKey: key, currentKey: tcmbSortKey, currentDir: tcmbSortDir, onSort: handleTcmbSort, align });
  const openTh  = (key, label, align = 'left') => ({ label, sortKey: key, currentKey: openSortKey,  currentDir: openSortDir,  onSort: handleOpenSort,  align });
  const bankTh  = (key, label, align = 'left') => ({ label, sortKey: key, currentKey: bankSortKey,  currentDir: bankSortDir,  onSort: handleBankSort,  align });

  const tabs = [
    { key: 'tcmb',  label: 'TCMB Resmi Kurlar', icon: Landmark },
    { key: 'open',  label: 'Open Exchange Rates', icon: Globe2 },
    { key: 'banks', label: 'Banka Kurları', icon: Banknote },
  ];

  const activeCount = activeTab === 'tcmb' ? filteredTcmb.length : activeTab === 'open' ? filteredOpen.length : filteredBanks.length;

  return (
    <div className="m3-fade-in">
      {/* ── Sade başlık ──────────────────────────────────────────────────────── */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-[#1a1c1e] border-l-4 border-[#093eaa] pl-3">{t('Döviz Kurları')}</h1>
        <p className="text-sm text-[#5a6472] mt-1 pl-4">
          {activeTab === 'tcmb'  && t('TCMB resmi döviz kurları (günlük güncellenir) — Türk Lirası bazlı')}
          {activeTab === 'open'  && t('Open Exchange Rates — gerçek zamanlıya yakın kurlar')}
          {activeTab === 'banks' && t('Türk bankalarının anlık döviz alış/satış kurları — Hesapkurdu')}
        </p>
      </div>

      {/* ── Popüler dövizler (yatay kart şeridi) ─────────────────────────────── */}
      {activeTab === 'tcmb' && popularRates.length > 0 && !q && (
        <div className="mb-6">
          <p className="text-xs font-bold text-[#5a6472] uppercase tracking-wider mb-2.5 pl-1">{t('Popüler Dövizler')}</p>
          <div className="flex gap-3 overflow-x-auto pb-1 m3-stagger">
            {popularRates.map(r => (
              <PopularCard key={r.symbol} rate={r} onClick={() => navigate('/market/fx/' + r.symbol)} />
            ))}
          </div>
        </div>
      )}

      {/* ── Sekmeler (Material 3 segmented) + ince arama ─────────────────────── */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-4 sm:flex-wrap">
        <div className="inline-flex items-center gap-1 p-1 rounded-full bg-[#eef2f8] border border-[#e2e8f0] flex-wrap">
          {tabs.map(tab => {
            const Icon = tab.icon;
            const active = activeTab === tab.key;
            return (
              <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                className={`m3-state inline-flex items-center gap-2 px-4 sm:px-5 py-2 rounded-full text-sm font-semibold transition-all duration-300 ${
                  active ? 'bg-[#093eaa] text-white shadow-[0_4px_12px_-4px_rgba(9,62,170,0.6)]' : 'text-[#5a6472] hover:text-[#093eaa]'
                }`}>
                <Icon className="w-4 h-4" />
                {t(tab.label)}
              </button>
            );
          })}
        </div>

        {/* İnce, sade arama alanı */}
        <div className="relative w-full sm:w-56">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[#9aa6b6]" />
          <input
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder={t('Ara')}
            className="w-full h-9 pl-9 pr-8 rounded-full bg-white border border-[#e2e8f0] text-sm text-[#1a1c1e]
                       placeholder:text-[#9aa6b6] focus:outline-none focus:border-[#093eaa] focus:ring-2 focus:ring-[#093eaa]/15 transition-all"
          />
          {query && (
            <button
              onClick={() => setQuery('')}
              className="absolute right-2 top-1/2 -translate-y-1/2 p-2 sm:p-0.5 rounded-full text-[#9aa6b6] hover:bg-[#eef2f8] transition-colors"
              aria-label={t('Temizle')}
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Open FX base selector */}
      {activeTab === 'open' && (
        <div className="flex items-center gap-3 mb-4 flex-wrap">
          <span className="text-sm text-[#5a6472] font-semibold">{t('Baz Para Birimi:')}</span>
          <div className="flex gap-2 flex-wrap">
            {OPEN_BASES.map(b => (
              <button key={b} onClick={() => setOpenBase(b)}
                className={`m3-state px-3.5 py-1.5 rounded-full text-sm font-bold transition-all border flex items-center gap-1.5 ${
                  openBase === b ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'bg-white text-[#5a6472] border-[#e2e8f0] hover:border-[#093eaa]/40'
                }`}>
                <FlagImg cc={FX_META[b]?.cc} size={16} /> {b}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Banka kurları döviz filtresi */}
      {activeTab === 'banks' && (
        <div className="flex items-center gap-3 mb-4 flex-wrap">
          <span className="text-sm text-[#5a6472] font-semibold">{t('Döviz:')}</span>
          <div className="flex gap-2 flex-wrap">
            {BANK_CURRENCIES.map(c => (
              <button key={c} onClick={() => setBankCurrency(c)}
                className={`m3-state px-3.5 py-1.5 rounded-full text-sm font-bold transition-all border ${
                  bankCurrency === c ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'bg-white text-[#5a6472] border-[#e2e8f0] hover:border-[#093eaa]/40'
                }`}>
                {c === 'Tümü' ? t('Tümü') : c}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="bg-white rounded-3xl border border-[#e2e8f0] shadow-[0_1px_3px_rgba(15,23,42,0.06)] overflow-hidden">
        {/* Meta info */}
        {!loading && !error && (
          <div className="px-3 sm:px-6 py-3 border-b border-[#eef2f8] bg-[#f6f8fc] text-xs text-[#5a6472] flex items-center justify-between flex-wrap gap-2">
            {activeTab === 'tcmb' && tcmbData && (
              <>
                <span>{t('Kaynak:')} TCMB · {t('Baz:')} TRY · {activeCount} {t('döviz')}{q ? ` · "${query}"` : ''}</span>
                <span>{tcmbData.asOf}</span>
              </>
            )}
            {activeTab === 'open' && openData && (
              <>
                <span>{t('Kaynak:')} Open Exchange Rates · {t('Baz:')} {openData.base} · {activeCount} {t('döviz')}{q ? ` · "${query}"` : ''}</span>
                <span>{openData.asOf}</span>
              </>
            )}
            {activeTab === 'banks' && (
              <span>{t('Kaynak:')} Hesapkurdu · {activeCount} {t('kayıt')}{bankCurrency !== 'Tümü' ? ` · ${bankCurrency}` : ''}{q ? ` · "${query}"` : ''}</span>
            )}
          </div>
        )}

        {loading && <Dots />}
        {error && <div className="p-3 sm:p-6 text-rose-500 text-sm">{error}</div>}

        {/* TCMB Table */}
        {!loading && !error && activeTab === 'tcmb' && tcmbData?.rates && (
          filteredTcmb.length === 0 ? (
            <EmptyState query={query} t={t} />
          ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-[#f6f8fc]">
                <tr>
                  <SortableTh {...tcmbTh('symbol', <FxColLabel tr={t('Döviz Kodu')} en="Currency Code" align="left" />)} />
                  <th className="text-left px-4 py-3 text-xs font-bold text-[#5a6472] uppercase tracking-wider border-b border-[#e2e8f0]">
                    <FxColLabel tr={t('Döviz Cinsi')} en="Currency" align="left" />
                  </th>
                  <SortableTh {...tcmbTh('unit', <FxColLabel tr={t('Birim')} en="Unit" />, 'right')} />
                  <SortableTh {...tcmbTh('buy', <FxColLabel tr={t('Döviz Alış')} en="Forex Buying" />, 'right')} />
                  <SortableTh {...tcmbTh('sell', <FxColLabel tr={t('Döviz Satış')} en="Forex Selling" />, 'right')} />
                  <SortableTh {...tcmbTh('effectiveBuy', <FxColLabel tr={t('Efektif Alış')} en="Banknote Buying" />, 'right')} />
                  <SortableTh {...tcmbTh('effectiveSell', <FxColLabel tr={t('Efektif Satış')} en="Banknote Selling" />, 'right')} />
                  <th className="px-4 py-3 border-b border-[#e2e8f0] w-10" />
                  <th className="px-2 py-3 border-b border-[#e2e8f0] w-8" aria-label={t('İzle')} />
                </tr>
              </thead>
              <tbody key={`tcmb-${q}`} className="m3-stagger">
                {filteredTcmb.map((r, i) => {
                  const meta = FX_META[r.symbol];
                  return (
                    <tr
                      key={r.symbol}
                      onClick={() => navigate('/market/fx/' + r.symbol)}
                      className={`border-t border-[#eef2f8] hover:bg-[#eef4ff] transition-colors cursor-pointer ${i % 2 === 0 ? '' : 'bg-[#fafbfd]'}`}
                    >
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2.5">
                          <span className="rounded-full ring-1 ring-[#e2e8f0] overflow-hidden inline-flex">
                            <FlagImg cc={meta?.cc} size={22} />
                          </span>
                          <Link
                            to={`/market/fx/${r.symbol}`}
                            onClick={e => e.stopPropagation()}
                            className="font-bold text-[#093eaa] text-sm hover:underline"
                          >
                            {r.symbol}/TRY
                          </Link>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-sm text-[#5a6472]">{meta?.ad ?? r.symbol}</td>
                      <td className="px-4 py-3 text-sm text-[#9aa6b6] text-right tabular-nums">{r.unit ?? 1}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-[#1a1c1e] text-right tabular-nums">{num(r.buy)}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-[#1a1c1e] text-right tabular-nums">{num(r.sell)}</td>
                      <td className="px-4 py-3 text-sm text-[#5a6472] text-right tabular-nums">{num(r.effectiveBuy)}</td>
                      <td className="px-4 py-3 text-sm text-[#5a6472] text-right tabular-nums">{num(r.effectiveSell)}</td>
                      <td className="px-2 py-3">
                        <Link
                          to={`/market/compare?symbols=${r.symbol}`}
                          onClick={e => e.stopPropagation()}
                          title={t('Karşılaştır')}
                          className="m3-state p-2 sm:p-1.5 rounded-full bg-[#eef2f8] hover:bg-[#093eaa] hover:text-white text-[#5a6472] transition-all inline-flex text-[#5a6472]"
                        >
                          <BarChart2 className="w-3.5 h-3.5" />
                        </Link>
                      </td>
                      <td className="px-2 py-3 text-center" onClick={e => e.stopPropagation()}>
                        <WatchlistStar assetType="FX" symbol={r.symbol} name={meta?.ad ?? r.symbol} price={r.sell} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            <p className="px-4 py-2.5 text-[11px] text-[#9aa6b6] bg-[#f6f8fc] border-t border-[#eef2f8]">
              {t('Efektif kurlar banknot (nakit) alış/satış kurlarıdır; bazı dövizlerde bulunmayabilir.')}
            </p>
          </div>
          )
        )}

        {/* Open FX Table */}
        {!loading && !error && activeTab === 'open' && openData?.rates && (
          filteredOpen.length === 0 ? (
            <EmptyState query={query} t={t} />
          ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-[#f6f8fc]">
                <tr>
                  <SortableTh {...openTh('symbol', t('Döviz'))} />
                  <th className="text-left px-4 py-3 text-xs font-bold text-[#5a6472] uppercase tracking-wider border-b border-[#e2e8f0]">
                    {t('Döviz Cinsi')}
                  </th>
                  <SortableTh {...openTh('sell', t('Kur'), 'right')} />
                  <th className="px-2 py-3 border-b border-[#e2e8f0] w-8" aria-label={t('İzle')} />
                </tr>
              </thead>
              <tbody key={`open-${q}`} className="m3-stagger">
                {filteredOpen.map((r, i) => {
                  const meta = FX_META[r.symbol];
                  return (
                    <tr key={r.symbol} className={`border-t border-[#eef2f8] hover:bg-[#eef4ff] transition-colors ${i % 2 === 0 ? '' : 'bg-[#fafbfd]'}`}>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2.5">
                          <span className="rounded-full ring-1 ring-[#e2e8f0] overflow-hidden inline-flex">
                            <FlagImg cc={meta?.cc} size={22} />
                          </span>
                          <span className="font-bold text-[#093eaa] text-sm">{r.symbol}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-sm text-[#5a6472]">{meta?.ad ?? r.symbol}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-[#1a1c1e] text-right tabular-nums">{num(r.sell ?? r.buy, 6)}</td>
                      <td className="px-2 py-3 text-center" onClick={e => e.stopPropagation()}>
                        <WatchlistStar assetType="FX" symbol={r.symbol} name={meta?.ad ?? r.symbol} price={r.sell ?? r.buy} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          )
        )}

        {/* Banka Kurları Table */}
        {!loading && !error && activeTab === 'banks' && (
          <>
            {filteredBanks.length === 0 ? (
              <EmptyState query={query} t={t} />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-[#f6f8fc]">
                    <tr>
                      <SortableTh {...bankTh('bankName', t('Banka'))} />
                      <SortableTh {...bankTh('currencyCode', t('Döviz'))} />
                      <SortableTh {...bankTh('buyRate', t('Alış'), 'right')} />
                      <SortableTh {...bankTh('sellRate', t('Satış'), 'right')} />
                      <SortableTh {...bankTh('spread', t('Makas'), 'right')} />
                      <SortableTh {...bankTh('dailyChangePercent', t('Günlük %'), 'right')} />
                      <th className="text-right px-4 py-3 text-xs font-bold text-[#5a6472] uppercase tracking-wider border-b border-[#e2e8f0]">
                        {t('Son Güncelleme')}
                      </th>
                    </tr>
                  </thead>
                  <tbody key={`banks-${q}`} className="m3-stagger">
                    {filteredBanks.map((r, i) => {
                      const meta = FX_META[r.currencyCode];
                      return (
                        <tr
                          key={`${r.bankName}-${r.currencyCode}`}
                          className={`border-t border-[#eef2f8] hover:bg-[#eef4ff] transition-colors ${i % 2 === 0 ? '' : 'bg-[#fafbfd]'}`}
                        >
                          <td className="px-4 py-3">
                            <span className="font-semibold text-[#1a1c1e] text-sm">{r.bankName ?? '-'}</span>
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-2.5">
                              <span className="rounded-full ring-1 ring-[#e2e8f0] overflow-hidden inline-flex">
                                <FlagImg cc={meta?.cc} size={22} />
                              </span>
                              <div>
                                <span className="font-bold text-[#093eaa] text-sm">{r.currencyCode}</span>
                                <span className="text-xs text-[#9aa6b6] ml-1">{r.currencyName}</span>
                              </div>
                            </div>
                          </td>
                          <td className="px-4 py-3 text-sm font-semibold text-[#1a1c1e] text-right tabular-nums">{numAuto(r.buyRate)}</td>
                          <td className="px-4 py-3 text-sm font-semibold text-[#1a1c1e] text-right tabular-nums">{numAuto(r.sellRate)}</td>
                          <td className="px-4 py-3 text-sm text-[#5a6472] text-right tabular-nums">{numAuto(r.spread)}</td>
                          <td className="px-4 py-3 text-sm text-right tabular-nums">{numChange(r.dailyChangePercent)}</td>
                          <td className="px-4 py-3 text-xs text-[#9aa6b6] text-right">{formatUpdatedAt(r.sourceUpdatedAt)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
                {/* JPY uyarısı */}
                {(bankCurrency === 'JPY' || bankCurrency === 'Tümü') && filteredBanks.some(r => r.currencyCode === 'JPY') && (
                  <div className="px-4 py-2 bg-amber-50 border-t border-amber-100 text-xs text-amber-700">
                    {t('⚠️ JPY kurları bankaya göre farklı birimde gösterilebilir: bazı bankalar 1 JPY, bazıları 100 JPY bazında fiyatlar.')}
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

// Boş sonuç durumu (Material 3)
function EmptyState({ query, t }) {
  return (
    <div className="p-6 sm:p-12 flex flex-col items-center justify-center text-center m3-fade-in">
      <div className="w-14 h-14 rounded-full bg-[#eef2f8] flex items-center justify-center mb-3">
        <Search className="w-6 h-6 text-[#9aa6b6]" />
      </div>
      <p className="text-sm font-semibold text-[#1a1c1e]">{t('Sonuç bulunamadı')}</p>
      {query && <p className="text-xs text-[#5a6472] mt-1">{t('"{q}" için eşleşme bulunamadı.', { q: query })}</p>}
    </div>
  );
}
