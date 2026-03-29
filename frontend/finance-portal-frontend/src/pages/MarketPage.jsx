import { useEffect, useState, useMemo } from 'react';
import { getCryptos, getAllStocks, getFutures, getFunds, getTefasFunds, getFxTcmb } from '../api/marketApi';

const TABS = [
  { key: 'crypto',  label: '🪙 Kripto' },
  { key: 'stocks',  label: '📈 Hisse' },
  { key: 'futures', label: '📊 Vadeli' },
  { key: 'funds',   label: '🏦 Global Fonlar' },
  { key: 'tefas',   label: '🇹🇷 TEFAS Fonları' },
  { key: 'fx',      label: '💱 Döviz' },
];

const PAGE_SIZE = 20;

const s = {
  tabBar: { display: 'flex', gap: '8px', marginBottom: '20px', borderBottom: '1px solid #ddd', paddingBottom: '8px', flexWrap: 'wrap' },
  tab: (a) => ({
    padding: '7px 18px', borderRadius: '4px 4px 0 0', cursor: 'pointer',
    border: '1px solid #ccc', background: a ? '#1a73e8' : 'transparent',
    color: a ? '#fff' : 'inherit', fontWeight: a ? 'bold' : 'normal',
  }),
  table: { width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' },
  th: { textAlign: 'left', padding: '8px 10px', borderBottom: '2px solid #ddd', whiteSpace: 'nowrap' },
  td: { padding: '7px 10px', borderBottom: '1px solid #eee' },
  pos: { color: '#16a34a' },
  neg: { color: '#dc2626' },
  searchBar: { display: 'flex', gap: '8px', marginBottom: '12px', alignItems: 'center' },
  input: { padding: '6px 10px', borderRadius: '4px', border: '1px solid #ccc', minWidth: '220px' },
  pageBar: { display: 'flex', gap: '6px', marginTop: '12px', alignItems: 'center', flexWrap: 'wrap' },
  pageBtn: (a) => ({
    padding: '4px 10px', borderRadius: '4px', cursor: a ? 'default' : 'pointer',
    border: '1px solid #ccc', background: a ? '#1a73e8' : 'transparent',
    color: a ? '#fff' : 'inherit',
  }),
};

function pct(v) {
  if (v == null) return '-';
  const n = parseFloat(v);
  return <span style={n >= 0 ? s.pos : s.neg}>{n >= 0 ? '+' : ''}{n.toFixed(2)}%</span>;
}

function num(v, dec = 2) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function Pagination({ page, totalPages, onPage }) {
  if (totalPages <= 1) return null;
  const visible = Math.min(totalPages, 10);
  return (
    <div style={s.pageBar}>
      <button style={s.pageBtn(false)} disabled={page === 0} onClick={() => onPage(page - 1)}>‹</button>
      {Array.from({ length: visible }, (_, i) => (
        <button key={i} style={s.pageBtn(i === page)} onClick={() => onPage(i)}>{i + 1}</button>
      ))}
      {totalPages > 10 && <span style={{ color: '#888', fontSize: '0.8rem' }}>... {totalPages} sayfa</span>}
      <button style={s.pageBtn(false)} disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>›</button>
    </div>
  );
}

function CryptoTable({ items }) {
  return (
    <table style={s.table}>
      <thead><tr>
        {['#', 'Coin', 'Fiyat (TRY)', '24s %', '24s Yüksek', '24s Düşük', 'Hacim'].map(h => <th key={h} style={s.th}>{h}</th>)}
      </tr></thead>
      <tbody>
        {items.map(c => (
          <tr key={c.id}>
            <td style={s.td}>{c.marketCapRank ?? '-'}</td>
            <td style={s.td}>
              {c.image && <img src={c.image} alt="" style={{ width: 20, height: 20, marginRight: 6, verticalAlign: 'middle' }} />}
              <strong>{c.name}</strong> <span style={{ color: '#888' }}>{c.symbol?.toUpperCase()}</span>
            </td>
            <td style={s.td}>{num(c.currentPrice)}</td>
            <td style={s.td}>{pct(c.priceChangePercentage24h)}</td>
            <td style={s.td}>{num(c.high24h)}</td>
            <td style={s.td}>{num(c.low24h)}</td>
            <td style={s.td}>{num(c.totalVolume, 0)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function StockTable({ items }) {
  return (
    <table style={s.table}>
      <thead><tr>
        {['Sembol', 'Ad', 'Fiyat', 'Değişim', '%', 'Yüksek', 'Düşük', 'Hacim', 'Borsa'].map(h => <th key={h} style={s.th}>{h}</th>)}
      </tr></thead>
      <tbody>
        {items.map(r => (
          <tr key={r.symbol}>
            <td style={s.td}><strong>{r.symbol}</strong></td>
            <td style={s.td}>{r.name ?? '-'}</td>
            <td style={s.td}>{num(r.price)} <span style={{ color: '#888', fontSize: '0.75rem' }}>{r.currency}</span></td>
            <td style={s.td}>{r.change == null ? '-' : <span style={parseFloat(r.change) >= 0 ? s.pos : s.neg}>{num(r.change)}</span>}</td>
            <td style={s.td}>{pct(r.changePercent)}</td>
            <td style={s.td}>{num(r.dayHigh)}</td>
            <td style={s.td}>{num(r.dayLow)}</td>
            <td style={s.td}>{r.volume == null ? '-' : Number(r.volume).toLocaleString('tr-TR')}</td>
            <td style={s.td}>{r.exchange ?? '-'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function TefasTable({ items }) {
  return (
    <table style={s.table}>
      <thead><tr>
        {['Kod', 'Fon Adı', 'Fiyat (TL)', 'Günlük %', 'Portföy Büyüklüğü', 'Yatırımcı', 'Tarih'].map(h => <th key={h} style={s.th}>{h}</th>)}
      </tr></thead>
      <tbody>
        {items.map(r => (
          <tr key={r.code}>
            <td style={s.td}><strong>{r.code}</strong></td>
            <td style={s.td}>{r.title ?? '-'}</td>
            <td style={s.td}>{num(r.price, 6)}</td>
            <td style={s.td}>{pct(r.dailyReturnPercent)}</td>
            <td style={s.td}>{r.marketCap == null ? '-' : num(r.marketCap, 0)}</td>
            <td style={s.td}>{r.numberOfInvestors == null ? '-' : Number(r.numberOfInvestors).toLocaleString('tr-TR')}</td>
            <td style={s.td}>{r.date ?? '-'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function FxTable({ data }) {
  if (!data?.rates?.length) return <p>Veri yok.</p>;
  return (
    <>
      <p style={{ color: '#888', fontSize: '0.8rem', marginBottom: 8 }}>
        Kaynak: {data.provider} · Baz: {data.base} · {data.asOf}
      </p>
      <table style={s.table}>
        <thead><tr>
          {['Döviz', 'Alış', 'Satış', 'Birim'].map(h => <th key={h} style={s.th}>{h}</th>)}
        </tr></thead>
        <tbody>
          {data.rates.map(r => (
            <tr key={r.symbol}>
              <td style={s.td}><strong>{r.symbol}</strong></td>
              <td style={s.td}>{num(r.buy, 4)}</td>
              <td style={s.td}>{num(r.sell, 4)}</td>
              <td style={s.td}>{r.unit}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

function SearchableList({ items, searchPlaceholder, searchFn, renderTable }) {
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
      <div style={s.searchBar}>
        <input style={s.input} type="text" placeholder={searchPlaceholder}
          value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} />
        <span style={{ color: '#888', fontSize: '0.8rem' }}>{filtered.length} sonuç</span>
      </div>
      {paged.length > 0 ? renderTable(paged) : <p>Sonuç bulunamadı.</p>}
      <Pagination page={page} totalPages={totalPages} onPage={setPage} />
    </>
  );
}

export default function MarketPage() {
  const [activeTab, setActiveTab] = useState('crypto');
  const [data, setData]           = useState({});
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState('');

  function fetchTab(tab) {
    setLoading(true);
    setError('');
    const fetchers = {
      crypto:  () => getCryptos(0, 50),
      stocks:  () => getAllStocks(),
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
      <h2>Piyasalar</h2>

      <div style={s.tabBar}>
        {TABS.map(t => (
          <button key={t.key} style={s.tab(activeTab === t.key)} onClick={() => handleTab(t.key)}>
            {t.label}
          </button>
        ))}
      </div>

      {loading && <p>Yükleniyor...</p>}
      {error   && <p style={{ color: 'red' }}>{error}</p>}

      {!loading && !error && current != null && (
        <>
          {activeTab === 'crypto' && <CryptoTable items={current} />}
          {activeTab === 'futures' && <StockTable items={current} />}
          {activeTab === 'funds' && <StockTable items={current} />}
          {activeTab === 'fx' && <FxTable data={current} />}
          {activeTab === 'stocks' && (
            <SearchableList items={current} searchPlaceholder="Sembol veya isim ara..."
              searchFn={(s, q) => s.symbol?.toLowerCase().includes(q) || s.name?.toLowerCase().includes(q)}
              renderTable={(items) => <StockTable items={items} />} />
          )}
          {activeTab === 'tefas' && (
            <SearchableList items={current} searchPlaceholder="Fon kodu veya adı ara..."
              searchFn={(f, q) => f.code?.toLowerCase().includes(q) || f.title?.toLowerCase().includes(q)}
              renderTable={(items) => <TefasTable items={items} />} />
          )}
        </>
      )}
      {!loading && !error && current == null && <p>Veri yok.</p>}
    </div>
  );
}
