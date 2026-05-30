import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Clock, TrendingUp } from 'lucide-react';
import {
  getStocks, getAllCryptoCoins, getFxTcmb, getCommodityList,
  getAllTefasFunds, getAllBesFunds, getAllOksFunds, getOsmanliFundBulletin,
  getViopContracts, getEvdsBonds, getGlobalBonds,
} from '../../api/marketApi';
import { useTranslation } from '../../context/LanguageContext';

const TYPE_LABEL = {
  STOCK: 'Hisse', CRYPTO: 'Kripto', FX: 'Döviz', COMMODITY: 'Emtia',
  GOLD: 'Altın', FUND: 'Fon', FUTURE: 'Vadeli', BOND: 'DİBS', EUROBOND: 'Eurobond',
};
const TYPE_BADGE = {
  STOCK: 'bg-blue-50 text-blue-700',
  CRYPTO: 'bg-amber-50 text-amber-700',
  FX: 'bg-emerald-50 text-emerald-700',
  COMMODITY: 'bg-purple-50 text-purple-700',
  GOLD: 'bg-yellow-50 text-yellow-700',
  FUND: 'bg-indigo-50 text-indigo-700',
  FUTURE: 'bg-rose-50 text-rose-700',
  BOND: 'bg-teal-50 text-teal-700',
  EUROBOND: 'bg-cyan-50 text-cyan-700',
};
const POPULAR_SYMBOLS = ['THYAO', 'ASELS', 'GARAN', 'BIMAS', 'TUPRS', 'BTC', 'ETH', 'USD'];
const RECENT_KEY = 'site_recent_searches';

// Statik altın listesi — per-item detay sayfası yok, hepsi /market/gold'a gider
const STATIC_GOLD = [
  { symbol: 'GRAM',   name: 'Gram Altın' },
  { symbol: 'CEYREK', name: 'Çeyrek Altın' },
  { symbol: 'YARIM',  name: 'Yarım Altın' },
  { symbol: 'ATA',    name: 'Ata Lira (Cumhuriyet)' },
  { symbol: 'TAM',    name: 'Tam Altın (Ziynet)' },
  { symbol: 'GOLD',   name: 'Ons Altın' },
  { symbol: '14AYAR', name: '14 Ayar Bilezik' },
  { symbol: '22AYAR', name: '22 Ayar Bilezik' },
];

// ── Kademeli (progressive) veri yükleme ───────────────────────────────────────
// Modül seviyesinde biriken tek veri seti. Her kaynak hazır olunca eklenir ve
// abonelere (açık SearchBox'lara) haber verilir. Yavaş kaynaklar (DİBS) hızlı
// olanları (hisse/kripto) bekletmez.
let DATASET = [];
let started = false;
const subscribers = new Set();

function notify() {
  for (const fn of subscribers) fn(DATASET);
}
function subscribe(fn) {
  subscribers.add(fn);
  return () => subscribers.delete(fn);
}
function add(items) {
  if (!items?.length) return;
  DATASET = DATASET.concat(items);
  notify();
}

/** Tüm BIST hisselerini sayfa sayfa çeker (backend size'ı kısıtladığı için totalPages'e göre döner). */
async function loadAllStocks() {
  const first = await getStocks(0, 200);
  const size = first?.size || first?.content?.length || 50;
  const totalPages = first?.totalPages || 1;
  const all = [...(first?.content ?? [])];
  if (totalPages > 1) {
    const reqs = [];
    for (let p = 1; p < totalPages; p++) {
      reqs.push(getStocks(p, size).then(r => r?.content ?? []).catch(() => []));
    }
    const pages = await Promise.all(reqs);
    for (const c of pages) all.push(...c);
  }
  return all;
}

/** 4 fon kaynağını paralel çek, koda göre tekilleştir. */
async function loadAllFunds() {
  const [tefas, bes, oks, osmanli] = await Promise.allSettled([
    getAllTefasFunds(), getAllBesFunds(), getAllOksFunds(), getOsmanliFundBulletin(),
  ]);
  const val = (r) => (r.status === 'fulfilled' ? (r.value ?? []) : []);
  const raw = [...val(tefas), ...val(bes), ...val(oks), ...val(osmanli)];
  const seen = new Set();
  const out = [];
  for (const f of raw) {
    const code = f.code ?? f.uniqueCode;
    if (!code || seen.has(code)) continue;
    seen.add(code);
    out.push({
      type: 'FUND',
      symbol: String(code).toUpperCase(),
      name: f.name || code,
      exchange: 'Fon',
      path: `/market/tefas/${encodeURIComponent(code)}`,
      // Detay sayfası sourceCode'u state.listItem'dan çıkarır (BES/OKS/Osmanlı)
      state: { listItem: { code, name: f.name, fundCategory: f.fundCategory, uniqueCode: f.uniqueCode, price: f.price } },
    });
  }
  return out;
}

/** Tüm kaynakları kademeli yükle: her biri hazır olunca DATASET'e ekler. */
function loadDataset() {
  if (started) return;
  started = true;

  // Altın statik — anında ekle
  add(STATIC_GOLD.map(g => ({
    type: 'GOLD', symbol: g.symbol, name: g.name, exchange: 'Altın', path: '/market/gold',
  })));

  loadAllStocks().then(content => add(content.reduce((acc, x) => {
    if (!x?.symbol) return acc;
    const name = x.name || x.companyName || x.longName || x.shortName || x.symbol;
    acc.push({ type: 'STOCK', symbol: String(x.symbol).toUpperCase(), name, exchange: 'İstanbul', path: `/market/stocks/${encodeURIComponent(x.symbol)}` });
    return acc;
  }, []))).catch(() => {});

  getAllCryptoCoins().then(c => add((c ?? []).reduce((acc, x) => {
    if (!x?.symbol || !x?.id) return acc;
    acc.push({ type: 'CRYPTO', symbol: String(x.symbol).toUpperCase(), name: x.name || x.symbol, exchange: 'Kripto', path: `/market/crypto/${encodeURIComponent(x.id)}` });
    return acc;
  }, []))).catch(() => {});

  getFxTcmb().then(fx => add((fx?.rates ?? []).reduce((acc, r) => {
    if (!r?.symbol) return acc;
    acc.push({ type: 'FX', symbol: String(r.symbol).toUpperCase(), name: `${r.symbol}/TRY`, exchange: 'Döviz', path: `/market/fx/${encodeURIComponent(r.symbol)}` });
    return acc;
  }, []))).catch(() => {});

  getCommodityList().then(com => add((com ?? []).reduce((acc, x) => {
    if (!x?.symbol) return acc;
    acc.push({ type: 'COMMODITY', symbol: String(x.symbol).toUpperCase(), name: x.displayNameTr || x.displayNameEn || x.name || x.symbol, exchange: 'Emtia', path: `/market/commodities/${encodeURIComponent(x.symbol)}` });
    return acc;
  }, []))).catch(() => {});

  loadAllFunds().then(add).catch(() => {});

  getViopContracts().then(list => add((list ?? []).reduce((acc, c) => {
    const fullName = (c.name ?? c.contractName ?? '').trim();
    if (!fullName) return acc;
    const shortLabel = fullName.includes(' (') ? fullName.split(' (')[0].trim() : fullName;
    acc.push({ type: 'FUTURE', symbol: shortLabel || fullName, name: fullName, exchange: 'VİOP', path: `/market/futures/${encodeURIComponent(fullName)}` });
    return acc;
  }, []))).catch(() => {});

  loadAllBonds().then(add).catch(() => {});

  loadAllEurobonds().then(add).catch(() => {});
}

/** Eurobond (Hazine dış borç) — HMB ISIN + BI. hasDetail ise detay sayfasına, değilse listeye gider. */
async function loadAllEurobonds() {
  const list = await getGlobalBonds();
  return (list ?? []).reduce((acc, b) => {
    if (!b?.isin) return acc;
    acc.push({
      type: 'EUROBOND',
      symbol: String(b.isin).toUpperCase(),
      name: b.name || b.issuer || 'Eurobond',
      exchange: 'Eurobond',
      path: b.hasDetail ? `/market/bonds/global/${encodeURIComponent(b.isin)}` : '/market/bonds',
    });
    return acc;
  }, []);
}

/** Tüm DİBS'leri sayfa sayfa çeker (backend size'ı 100'le sınırlar; tek sayfa eksik kalır). */
async function loadAllBonds() {
  const bondParams = (p) => ({ page: p, size: 100, sortBy: 'maturityDate', sortDir: 'asc' });
  const first = await getEvdsBonds(bondParams(0));
  const totalPages = first?.totalPages || 1;
  const items = [...(first?.items ?? [])];
  if (totalPages > 1) {
    const pages = await Promise.all(
      Array.from({ length: totalPages - 1 }, (_, i) =>
        getEvdsBonds(bondParams(i + 1)).then(r => r?.items ?? []).catch(() => [])),
    );
    for (const c of pages) items.push(...c);
  }
  return items.reduce((acc, b) => {
    if (!b?.instrumentCode) return acc;
    acc.push({ type: 'BOND', symbol: String(b.instrumentCode).toUpperCase(), name: b.type || 'DİBS', exchange: 'DİBS', path: `/market/bonds/${encodeURIComponent(b.instrumentCode)}` });
    return acc;
  }, []);
}

const normalize = (s) => String(s || '').toLocaleLowerCase('tr');

function searchDataset(ds, q) {
  const query = normalize(q).trim();
  if (!query) return [];
  const scored = [];
  for (const x of ds) {
    const sym = normalize(x.symbol);
    const nm = normalize(x.name);
    let score = -1;
    if (sym === query) score = 0;
    else if (sym.startsWith(query)) score = 1;
    else if (nm.startsWith(query)) score = 2;
    else if (sym.includes(query)) score = 3;
    else if (nm.includes(query)) score = 4;
    if (score >= 0) scored.push({ x, score });
  }
  scored.sort((a, b) => a.score - b.score || a.x.symbol.length - b.x.symbol.length);
  return scored.slice(0, 40).map(s => s.x);
}

function buildPopular(ds) {
  if (!ds.length) return [];
  return POPULAR_SYMBOLS
    .map(sym => ds.find(x => x.symbol.replace('.IS', '') === sym))
    .filter(Boolean);
}

function loadRecent() {
  try { return JSON.parse(localStorage.getItem(RECENT_KEY) || '[]'); } catch { return []; }
}
function saveRecent(item) {
  try {
    const cur = loadRecent().filter(x => x.path !== item.path);
    cur.unshift({ type: item.type, symbol: item.symbol, name: item.name, exchange: item.exchange, path: item.path, state: item.state });
    localStorage.setItem(RECENT_KEY, JSON.stringify(cur.slice(0, 6)));
  } catch { /* yoksay */ }
}

function TypeBadge({ type, t }) {
  return (
    <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded shrink-0 ${TYPE_BADGE[type] || 'bg-gray-100 text-gray-600'}`}>
      {t(TYPE_LABEL[type] || type)}
    </span>
  );
}

function ResultRow({ item, active, onClick, t }) {
  return (
    <button
      type="button"
      onMouseDown={(e) => { e.preventDefault(); onClick(); }}
      className={`w-full flex items-center gap-3 px-3 py-2 text-left transition-colors ${active ? 'bg-blue-50' : 'hover:bg-gray-50'}`}
    >
      <TypeBadge type={item.type} t={t} />
      <span className="font-bold text-sm text-[#093eaa] w-24 shrink-0 truncate">{item.symbol}</span>
      <span className="text-sm text-gray-600 flex-1 truncate">{item.name}</span>
      <span className="text-xs text-gray-400 shrink-0 hidden sm:block">{item.exchange}</span>
    </button>
  );
}

export default function SearchBox() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);
  const [ds, setDs] = useState(DATASET);
  const [recent, setRecent] = useState(loadRecent());
  const [activeIdx, setActiveIdx] = useState(0);
  const boxRef = useRef(null);

  useEffect(() => {
    function onDoc(e) { if (boxRef.current && !boxRef.current.contains(e.target)) setOpen(false); }
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  // Kademeli yüklemeye abone ol — kaynaklar geldikçe listeyi tazeler
  useEffect(() => {
    const unsub = subscribe(setDs);
    return unsub;
  }, []);

  function ensureData() { loadDataset(); }

  const results = useMemo(() => searchDataset(ds, q), [ds, q]);
  const popular = useMemo(() => buildPopular(ds), [ds]);
  const hasQuery = q.trim().length > 0;

  function go(item) {
    saveRecent(item);
    setRecent(loadRecent());
    setOpen(false);
    setQ('');
    navigate(item.path, item.state ? { state: item.state } : undefined);
  }

  function onKeyDown(e) {
    if (!open) return;
    const items = hasQuery ? results : [...recent, ...popular];
    if (e.key === 'ArrowDown') { e.preventDefault(); setActiveIdx(i => Math.min(i + 1, items.length - 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActiveIdx(i => Math.max(i - 1, 0)); }
    else if (e.key === 'Enter') { if (items[activeIdx]) go(items[activeIdx]); }
    else if (e.key === 'Escape') { setOpen(false); }
  }

  return (
    <div ref={boxRef} className="relative hidden lg:block">
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-4 h-4 pointer-events-none" />
      <input
        type="text"
        value={q}
        onChange={e => { setQ(e.target.value); setActiveIdx(0); setOpen(true); }}
        onFocus={() => { setOpen(true); ensureData(); }}
        onKeyDown={onKeyDown}
        placeholder={t('Sitede ara...')}
        className="pl-9 pr-4 py-1.5 bg-gray-100 border-none rounded-full text-sm w-44 focus:outline-none focus:ring-2 focus:ring-[#093eaa] focus:w-72 transition-all"
      />

      {open && (
        <div className="absolute right-0 mt-2 w-[460px] max-w-[88vw] bg-white border border-gray-200 rounded-xl shadow-xl z-50 overflow-hidden">
          <div className="max-h-[440px] overflow-y-auto">
            {hasQuery ? (
              results.length ? (
                results.map((item, i) => (
                  <ResultRow key={item.path} item={item} active={i === activeIdx} onClick={() => go(item)} t={t} />
                ))
              ) : (
                <div className="px-4 py-8 text-center text-sm text-gray-400">
                  {ds.length ? t('Sonuç bulunamadı.') : t('Yükleniyor...')}
                </div>
              )
            ) : (
              <>
                {recent.length > 0 && (
                  <div>
                    <div className="flex items-center gap-1.5 px-3 pt-3 pb-1 text-xs font-bold text-gray-400 uppercase tracking-wider">
                      <Clock className="w-3.5 h-3.5" /> {t('Son Aramalarım')}
                    </div>
                    {recent.map((item, i) => (
                      <ResultRow key={`r-${item.path}`} item={item} active={i === activeIdx} onClick={() => go(item)} t={t} />
                    ))}
                  </div>
                )}
                <div>
                  <div className="flex items-center gap-1.5 px-3 pt-3 pb-1 text-xs font-bold text-gray-400 uppercase tracking-wider">
                    <TrendingUp className="w-3.5 h-3.5" /> {t('Popüler Aramalar')}
                  </div>
                  {popular.length ? (
                    popular.map((item, i) => (
                      <ResultRow key={`p-${item.path}`} item={item} active={recent.length + i === activeIdx} onClick={() => go(item)} t={t} />
                    ))
                  ) : (
                    <div className="px-4 py-6 text-center text-sm text-gray-400">{t('Yükleniyor...')}</div>
                  )}
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
