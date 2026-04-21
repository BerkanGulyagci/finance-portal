import { useEffect, useState, useMemo } from 'react';
import { getCryptos, getStocks, getFutures, getFunds, getTefasFunds, getFxTcmb } from '../api/marketApi';

const TABS = [
  { key: 'crypto',  label: '🪙 Kripto' },
  { key: 'stocks',  label: '📈 Hisse' },
  { key: 'futures', label: '📊 Vadeli' },
  { key: 'funds',   label: '🏦 Global Fonlar' },
  { key: 'tefas',   label: '🇹🇷 TEFAS Fonları' },
  { key: 'fx',      label: '💱 Döviz' },
];

const PAGE_SIZE = 20;

function pct(v) {
  if (v == null) return <span className="text-gray-400">-</span>;
  const n = parseFloat(v);
  return <span className={n >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>
    {n >= 0 ? '+' : ''}{n.toFixed(2)}%
  </span>;
}

function num(v, dec = 2) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function Th({ children }) {
  return <th className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider whitespace-nowrap border-b border-gray-200">{children}</th>;
}
function Td({ children, className = '' }) {
  return <td className={`px-4 py-3 text-sm border-b border-gray-100 ${className}`}>{children}</td>;
}

function CryptoTable({ items }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead className="bg-gray-50"><tr>
          {['#', 'Coin', 'Fiyat (TRY)', '24s %', '24s Yüksek', '24s Düşük', 'Hacim'].map(h => <Th key={h}>{h}</Th>)}
        </tr></thead>
        <tbody className="bg-white">
          {items.map(c => (
            <tr key={c.id} className="hover:bg-gray-50 transition-colors">
              <Td className="text-gray-400">{c.marketCapRank ?? '-'}</Td>
              <Td>
                <div className="flex items-center gap-2">
                  {c.image && <img src={c.image} alt="" className="w-6 h-6 rounded-full" />}
                  <span className="font-bold text-gray-900">{c.name}</span>
                  <span className="text-gray-400 text-xs">{c.symbol?.toUpperCase()}</span>
                </div>
              </Td>
              <Td className="font-semibold">{num(c.currentPrice)}</Td>
              <Td>{pct(c.priceChangePercentage24h)}</Td>
              <Td className="text-gray-600">{num(c.high24h)}</Td>
              <Td className="text-gray-600">{num(c.low24h)}</Td>
              <Td className="text-gray-600">{num(c.totalVolume, 0)}</Td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StockTable({ items }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead className="bg-gray-50"><tr>
          {['Sembol', 'Ad', 'Fiyat', 'Değişim', '%', 'Yüksek', 'Düşük', 'Hacim', 'Borsa'].map(h => <Th key={h}>{h}</Th>)}
        </tr></thead>
        <tbody className="bg-white">
          {items.map(r => (
            <tr key={r.symbol} className="hover:bg-gray-50 transition-colors">
              <Td><span className="font-bold text-[#093eaa]">{r.symbol}</span></Td>
              <Td className="text-gray-600">{r.name ?? '-'}</Td>
              <Td className="font-semibold">{num(r.price)} <span className="text-gray-400 text-xs">{r.currency}</span></Td>
              <Td>{r.change == null ? '-' : <span className={parseFloat(r.change) >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{num(r.change)}</span>}</Td>
              <Td>{pct(r.changePercent)}</Td>
              <Td className="text-gray-600">{num(r.dayHigh)}</Td>
              <Td className="text-gray-600">{num(r.dayLow)}</Td>
              <Td className="text-gray-600">{r.volume == null ? '-' : Number(r.volume).toLocaleString('tr-TR')}</Td>
              <Td className="text-gray-400 text-xs">{r.exchange ?? '-'}</Td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TefasTable({ items }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead className="bg-gray-50"><tr>
          {['Kod', 'Fon Adı', 'Fiyat (TL)', 'Günlük %', 'Portföy Büyüklüğü', 'Yatırımcı', 'Tarih'].map(h => <Th key={h}>{h}</Th>)}
        </tr></thead>
        <tbody className="bg-white">
          {items.map(r => (
            <tr key={r.code} className="hover:bg-gray-50 transition-colors">
              <Td><span className="font-bold text-[#093eaa]">{r.code}</span></Td>
              <Td className="text-gray-700 max-w-xs truncate">{r.title ?? '-'}</Td>
              <Td className="font-semibold">{num(r.price, 6)}</Td>
              <Td>{pct(r.dailyReturnPercent)}</Td>
              <Td className="text-gray-600">{r.marketCap == null ? '-' : num(r.marketCap, 0)}</Td>
              <Td className="text-gray-600">{r.numberOfInvestors == null ? '-' : Number(r.numberOfInvestors).toLocaleString('tr-TR')}</Td>
              <Td className="text-gray-400 text-xs">{r.date ?? '-'}</Td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function FxTable({ data }) {
  if (!data?.rates?.length) return <p className="text-gray-400 p-4">Veri yok.</p>;
  return (
    <div>
      <p className="text-xs text-gray-400 mb-3 px-1">Kaynak: {data.provider} · Baz: {data.base} · {data.asOf}</p>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="bg-gray-50"><tr>
            {['Döviz', 'Alış', 'Satış', 'Birim'].map(h => <Th key={h}>{h}</Th>)}
          </tr></thead>
          <tbody className="bg-white">
            {data.rates.map(r => (
              <tr key={r.symbol} className="hover:bg-gray-50 transition-colors">
                <Td><span className="font-bold text-[#093eaa]">{r.symbol}</span></Td>
                <Td className="font-semibold">{num(r.buy, 4)}</Td>
                <Td className="font-semibold">{num(r.sell, 4)}</Td>
                <Td className="text-gray-400">{r.unit}</Td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function SearchableList({ items, placeholder, searchFn, renderTable }) {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  const filtered = useMemo(() => {
    if (!search.trim()) return items;
    return items.filter(i => searchFn(i, search.toLowerCase()));
  }, [items, search, searchFn]);

  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
  const paged = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  return (
    <>
      <div className="flex items-center gap-3 mb-4">
        <input
          type="text"
          placeholder={placeholder}
          value={search}
          onChange={e => { setSearch(e.target.value); setPage(0); }}
          className="px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] min-w-[240px]"
        />
        <span className="text-xs text-gray-400">{filtered.length} sonuç</span>
      </div>
      {paged.length > 0 ? renderTable(paged) : <p className="text-gray-400 p-4">Sonuç bulunamadı.</p>}
      {totalPages > 1 && (
        <div className="flex gap-2 mt-4 flex-wrap">
          <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
            className="px-3 py-1.5 rounded-lg border border-gray-200 text-sm hover:bg-gray-50 disabled:opacity-40">‹</button>
          {Array.from({ length: Math.min(totalPages, 10) }, (_, i) => (
            <button key={i} onClick={() => setPage(i)}
              className={`px-3 py-1.5 rounded-lg border text-sm ${i === page ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'border-gray-200 hover:bg-gray-50'}`}>
              {i + 1}
            </button>
          ))}
          {totalPages > 10 && <span className="text-xs text-gray-400 self-center">... {totalPages} sayfa</span>}
          <button disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}
            className="px-3 py-1.5 rounded-lg border border-gray-200 text-sm hover:bg-gray-50 disabled:opacity-40">›</button>
        </div>
      )}
    </>
  );
}

export default function MarketPage() {
  const [activeTab, setActiveTab] = useState('crypto');
  const [data, setData] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function fetchTab(tab) {
    setLoading(true);
    setError('');
    const fetchers = {
      crypto:  () => getCryptos(0, 50),
      stocks:  () => getStocks(0, 20).then(r => r.content ?? []),
      futures: () => getFutures(0, 20).then(r => r.content ?? []),
      funds:   () => getFunds(0, 30).then(r => r.content ?? []),
      tefas:   () => getTefasFunds('YAT', 0, 500).then(r => r.content ?? []),
      fx:      () => getFxTcmb(),
    };
    fetchers[tab]()
      .then(result => setData(prev => ({ ...prev, [tab]: result })))
      .catch(err => setError(!err.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${err.response.status})`))
      .finally(() => setLoading(false));
  }

  useEffect(() => { fetchTab('crypto'); }, []);

  function handleTab(tab) {
    setActiveTab(tab);
    if (!data[tab]) fetchTab(tab);
  }

  const current = data[activeTab];

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6 border-l-4 border-[#093eaa] pl-4">Piyasalar</h1>

      {/* Tabs */}
      <div className="flex gap-2 mb-6 flex-wrap">
        {TABS.map(t => (
          <button key={t.key} onClick={() => handleTab(t.key)}
            className={`px-4 py-2 rounded-xl text-sm font-semibold transition-all ${
              activeTab === t.key
                ? 'bg-[#093eaa] text-white shadow-sm'
                : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
            }`}>
            {t.label}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {loading && (
          <div className="p-8 text-center">
            <div className="flex items-center justify-center gap-2">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
            <p className="text-gray-400 text-sm mt-3">Yükleniyor...</p>
          </div>
        )}
        {error && <p className="text-rose-500 p-6 text-sm">{error}</p>}

        {!loading && !error && current != null && (
          <div className="p-4">
            {activeTab === 'crypto' && <CryptoTable items={current} />}
            {activeTab === 'futures' && <StockTable items={current} />}
            {activeTab === 'funds' && <StockTable items={current} />}
            {activeTab === 'fx' && <FxTable data={current} />}
            {activeTab === 'stocks' && (
              <SearchableList items={current} placeholder="Sembol veya isim ara..."
                searchFn={(s, q) => s.symbol?.toLowerCase().includes(q) || s.name?.toLowerCase().includes(q)}
                renderTable={items => <StockTable items={items} />} />
            )}
            {activeTab === 'tefas' && (
              <SearchableList items={current} placeholder="Fon kodu veya adı ara..."
                searchFn={(f, q) => f.code?.toLowerCase().includes(q) || f.title?.toLowerCase().includes(q)}
                renderTable={items => <TefasTable items={items} />} />
            )}
          </div>
        )}
        {!loading && !error && current == null && <p className="text-gray-400 p-6 text-sm">Veri yok.</p>}
      </div>
    </div>
  );
}
