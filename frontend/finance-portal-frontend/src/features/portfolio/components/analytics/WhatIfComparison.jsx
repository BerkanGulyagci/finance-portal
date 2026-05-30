import { useEffect, useMemo, useState } from 'react';
import { Sparkles, Plus, X } from 'lucide-react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine,
} from 'recharts';
import { getPortfolioWhatIfSeries } from '../../../../api/portfolioApi';
import { formatMoney } from '../../utils/portfolioFormatUtils';
import { useTranslation } from '../../../../i18n/LanguageContext';
import InstrumentSearchModal from '../../../../components/instrument/InstrumentSearchModal';

const SERIES = [
  { key: 'actual', label: 'Gerçek', color: '#093eaa' },
  { key: 'inflation', label: 'Enflasyon (TÜFE)', color: '#9333ea' },
  { key: 'usInflation', label: 'ABD Enflasyonu', color: '#be185d' },
  { key: 'gold', label: 'Altın (gram)', color: '#f59e0b' },
  { key: 'usd', label: 'Dolar (USD)', color: '#10b981' },
  { key: 'deposit', label: 'Mevduat', color: '#6366f1' },
  { key: 'bist100', label: 'BIST100', color: '#0891b2' },
  { key: 'bitcoin', label: 'Bitcoin', color: '#ea580c' },
  { key: 'sp500', label: 'S&P 500', color: '#65a30d' },
];
const DEFAULT_HIDDEN = ['bist100', 'bitcoin', 'sp500'];

const ASSET_LABELS = {
  STOCK: 'Hisse', CRYPTO: 'Kripto', FX: 'Döviz', FUND: 'Fon',
  FUTURE: 'Vadeli', GOLD: 'Altın', COMMODITY: 'Emtia', BOND: 'DİBS',
};

const MAX_CUSTOM = 3;
const CUSTOM_COLORS = ['#db2777', '#0d9488', '#7c3aed'];

function num(v) {
  const n = typeof v === 'string' ? parseFloat(v) : v;
  return Number.isFinite(n) ? n : null;
}
function pctOf(value, cost) {
  const v = num(value); const c = num(cost);
  if (v == null || c == null || c <= 0) return null;
  return (v / c - 1) * 100;
}
function formatTick(d) {
  if (!d) return '';
  const [y, m] = d.split('-');
  return `${m}/${y?.slice(2)}`;
}
function formatLongDate(d) {
  if (!d) return '';
  try {
    return new Date(d).toLocaleDateString('tr-TR', { day: 'numeric', month: 'long', year: 'numeric' });
  } catch {
    return d;
  }
}
const TODAY_STR = new Date().toISOString().slice(0, 10);
function safeKey(id) {
  return id.replace(/[^a-zA-Z0-9]/g, '_');
}

export default function WhatIfComparison({ portfolioId, holdings = [], valuesHidden = false }) {
  const { t } = useTranslation();
  const [scope, setScope] = useState('ALL');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [hidden, setHidden] = useState(() => new Set(DEFAULT_HIDDEN));
  const [custom, setCustom] = useState([]); // [{id, safe, assetType, symbol, label, color}]
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchFor, setSearchFor] = useState('benchmark'); // 'benchmark' | 'sim'
  const [activeRow, setActiveRow] = useState(null); // grafikte hover edilen nokta (alt panel için)

  // Özel/simülasyon modu
  const [mode, setMode] = useState('PORTFOLIO'); // 'PORTFOLIO' | 'SIM'
  const [simInstrument, setSimInstrument] = useState(null); // {assetType, symbol, name}
  const [simAmount, setSimAmount] = useState('10000');
  const [simDate, setSimDate] = useState(() => {
    const d = new Date(); d.setFullYear(d.getFullYear() - 1);
    return d.toISOString().slice(0, 10);
  });

  function toggleSeries(key) {
    if (key === 'actual') return;
    setHidden(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }

  function addCustom(assetType, symbol, name) {
    if (!assetType || !symbol) return;
    const id = `${assetType}|${symbol}`;
    if (custom.some(c => c.id === id) || custom.length >= MAX_CUSTOM) return;
    const color = CUSTOM_COLORS[custom.length % CUSTOM_COLORS.length];
    const label = String(symbol).replace(/\.IS$/i, '');
    setCustom(prev => [...prev, { id, safe: safeKey(id), assetType, symbol, label, name: name || symbol, color }]);
    setSearchOpen(false);
  }

  function removeCustom(id) {
    setCustom(prev => prev.filter(c => c.id !== id));
    setHidden(prev => { const n = new Set(prev); n.delete(id); return n; });
  }

  const assetOptions = useMemo(() => {
    const opts = [{ value: 'ALL', label: t('Tüm Portföy'), assetType: null, symbol: null }];
    for (const h of holdings) {
      if (!h.symbol || !h.assetType) continue;
      const typeLabel = ASSET_LABELS[h.assetType] ? t(ASSET_LABELS[h.assetType]) : h.assetType;
      opts.push({
        value: `${h.assetType}|${h.symbol}`,
        label: `${h.name || h.symbol} · ${typeLabel}`,
        assetType: h.assetType, symbol: h.symbol,
      });
    }
    return opts;
  }, [holdings, t]);

  const customIds = custom.map(c => c.id).join(',');
  const simReady = mode === 'SIM' && simInstrument && Number(simAmount) > 0 && simDate;

  function onPick(sel) {
    if (searchFor === 'sim') {
      setSimInstrument({ assetType: sel.assetType, symbol: sel.symbol, name: sel.name });
      setSearchOpen(false);
    } else {
      addCustom(sel.assetType, sel.symbol, sel.name);
    }
  }

  useEffect(() => {
    if (!portfolioId) return;
    if (mode === 'SIM' && !simReady) { setData(null); setLoading(false); setError(''); return; }
    let cancelled = false;
    setLoading(true);
    setError('');
    const benchIds = custom.map(c => c.id);
    const req = mode === 'SIM'
      ? getPortfolioWhatIfSeries(portfolioId, null, null, benchIds, {
          assetType: simInstrument.assetType, symbol: simInstrument.symbol,
          amount: Number(simAmount), date: simDate,
        })
      : (() => {
          const sel = assetOptions.find(o => o.value === scope) ?? assetOptions[0];
          return getPortfolioWhatIfSeries(portfolioId, sel?.assetType, sel?.symbol, benchIds);
        })();
    req
      .then(res => { if (!cancelled) setData(res); })
      .catch(() => { if (!cancelled) setError(t('Senaryo hesaplanamadı.')); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [portfolioId, mode, scope, assetOptions, customIds, simReady,
      simInstrument, simAmount, simDate, t]);

  const points = data?.points ?? [];
  const available = data?.availableScenarios ?? [];
  const availBench = data?.availableBenchmarks ?? [];
  const availableSeries = SERIES.filter(s => available.includes(s.key));
  const availableCustom = custom.filter(c => availBench.includes(c.id));
  const visibleSeries = availableSeries.filter(s => !hidden.has(s.key));
  const visibleCustom = availableCustom.filter(c => !hidden.has(c.id));

  const chartData = useMemo(() => points.map(p => {
    const row = { date: p.date, cost: num(p.cost) };
    for (const s of SERIES) {
      row[s.key] = num(p[s.key]);
      row[`${s.key}Pct`] = pctOf(p[s.key], p.cost);
    }
    for (const c of custom) {
      const v = p.extra ? num(p.extra[c.id]) : null;
      row[c.safe] = v;
      row[`${c.safe}Pct`] = pctOf(v, p.cost);
    }
    return row;
  }), [points, custom]);

  const lastRow = chartData.length ? chartData[chartData.length - 1] : null;

  function TooltipBox({ active, payload, label }) {
    if (!active || !payload?.length) return null;
    return (
      <div className="bg-white border border-gray-200 rounded-lg shadow-lg px-3 py-2 text-xs min-w-[200px]">
        <p className="text-gray-500 font-semibold mb-1.5 border-b border-gray-100 pb-1">{formatTick(label)}</p>
        <div className="space-y-1">
          {payload.map(p => {
            const key = String(p.dataKey).replace(/Pct$/, '');
            const abs = p.payload?.[key];
            const pct = p.value;
            return (
              <div key={p.dataKey} className="flex justify-between gap-3 items-center">
                <span className="flex items-center gap-1.5 text-gray-600">
                  <span className="w-2 h-2 rounded-full shrink-0" style={{ background: p.color }} />
                  {p.name}
                </span>
                <span className="font-bold tabular-nums" style={{ color: p.color }}>
                  {pct != null ? `${pct >= 0 ? '+' : ''}${pct.toFixed(1)}%` : '—'}
                  {abs != null && !valuesHidden && (
                    <span className="ml-1 font-normal text-gray-400">· {formatMoney(abs, 'TRY', false)}</span>
                  )}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    );
  }

  const Header = (
    <div className="flex items-start justify-between gap-3 flex-wrap">
      <div className="flex items-center gap-2">
        <span className="inline-flex items-center justify-center w-7 h-7 rounded-md bg-[#093eaa]/10 text-[#093eaa]">
          <Sparkles className="w-4 h-4" />
        </span>
        <div>
          <p className="text-sm font-bold text-gray-900">{t('Ne Olurdu?')}</p>
          <p className="text-xs text-gray-400">
            {t('Yatırdığın parayı o tarihten bugüne başka araçlara koysaydın nasıl bir getiri izlerdi.')}
          </p>
        </div>
      </div>
      <div className="flex flex-col items-end gap-2">
        {/* Mod: Portföyüm / Özel */}
        <div className="inline-flex rounded-lg border border-gray-200 overflow-hidden text-xs font-semibold">
          {['PORTFOLIO', 'SIM'].map(m => (
            <button
              key={m}
              type="button"
              onClick={() => setMode(m)}
              className={`px-3 py-1.5 transition-colors ${mode === m ? 'bg-[#093eaa] text-white' : 'bg-white text-gray-600 hover:bg-gray-50'}`}
            >
              {m === 'PORTFOLIO' ? t('Portföyüm') : t('Özel')}
            </button>
          ))}
        </div>

        {mode === 'PORTFOLIO' ? (
          <select
            value={scope}
            onChange={e => setScope(e.target.value)}
            className="text-xs font-semibold border border-gray-200 rounded-lg px-2.5 py-1.5 bg-white text-gray-700 focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 max-w-[220px]"
            aria-label={t('Varlık seç')}
          >
            {assetOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        ) : (
          <div className="flex flex-wrap items-center gap-1.5 justify-end">
            <button
              type="button"
              onClick={() => { setSearchFor('sim'); setSearchOpen(true); }}
              className="text-xs font-semibold border border-gray-200 rounded-lg px-2.5 py-1.5 bg-white text-gray-700 hover:border-[#093eaa]/40 max-w-[160px] truncate"
              title={t('Varlık seç')}
            >
              {simInstrument ? (simInstrument.name || simInstrument.symbol) : `+ ${t('Varlık seç')}`}
            </button>
            <input
              type="number"
              value={simAmount}
              min="0"
              onChange={e => setSimAmount(e.target.value)}
              className="text-xs border border-gray-200 rounded-lg px-2 py-1.5 w-24 focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30"
              placeholder="₺ tutar"
            />
            <input
              type="date"
              value={simDate}
              max={TODAY_STR}
              onChange={e => setSimDate(e.target.value)}
              className="text-xs border border-gray-200 rounded-lg px-2 py-1.5 focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30"
            />
          </div>
        )}
      </div>
    </div>
  );

  return (
    <div className="rounded-xl border border-gray-200 p-5 space-y-4">
      {Header}

      {searchOpen && (
        <InstrumentSearchModal
          onSelect={onPick}
          onClose={() => setSearchOpen(false)}
        />
      )}

      {mode === 'SIM' && !simReady && !loading && (
        <p className="text-sm text-gray-400 py-6">
          {t('Özel mod: bir varlık seç, tutar ve tarih gir — o tarihte o varlığı alsaydın ne olurdu görelim.')}
        </p>
      )}

      {loading && (
        <div className="flex items-center justify-center gap-2 py-12">
          <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
          <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
          <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
        </div>
      )}

      {!loading && error && <p className="text-sm text-rose-600 py-6">{error}</p>}

      {!loading && !error && chartData.length === 0 && !(mode === 'SIM' && !simReady) && (
        <p className="text-sm text-gray-400 py-6">
          {t('Senaryo için yeterli veri yok (pozisyon çok yeni ya da fiyat geçmişi bulunamadı).')}
        </p>
      )}

      {!loading && !error && chartData.length > 0 && (
        <>
          {/* Tıklanabilir efsane (aç/kapat) + son % getiri + serbest kıyaslar */}
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
            {availableSeries.map(s => {
              const isHidden = hidden.has(s.key);
              const pct = lastRow?.[`${s.key}Pct`];
              const toggleable = s.key !== 'actual';
              return (
                <button
                  key={s.key}
                  type="button"
                  onClick={() => toggleSeries(s.key)}
                  title={toggleable ? t('Çizgiyi aç/kapat') : t('Her zaman görünür')}
                  className={`text-xs font-semibold flex items-center gap-1.5 transition-opacity ${isHidden ? 'opacity-35' : ''} ${toggleable ? 'cursor-pointer hover:opacity-70' : 'cursor-default'}`}
                >
                  <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: s.color }} />
                  <span className={isHidden ? 'text-gray-500 line-through' : 'text-gray-600'}>{t(s.label)}</span>
                  {pct != null && !isHidden && (
                    <span className={pct >= 0 ? 'text-emerald-600' : 'text-rose-600'}>
                      {pct >= 0 ? '+' : ''}{pct.toFixed(1)}%
                    </span>
                  )}
                </button>
              );
            })}

            {/* Serbest kıyas chip'leri */}
            {custom.map(c => {
              const isHidden = hidden.has(c.id);
              const hasData = availBench.includes(c.id);
              const pct = lastRow?.[`${c.safe}Pct`];
              return (
                <span key={c.id} className={`text-xs font-semibold flex items-center gap-1 ${isHidden ? 'opacity-35' : ''}`}>
                  <button
                    type="button"
                    onClick={() => hasData && toggleSeries(c.id)}
                    className="flex items-center gap-1.5 cursor-pointer hover:opacity-70"
                    title={hasData ? t('Çizgiyi aç/kapat') : t('Veri bulunamadı')}
                  >
                    <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: c.color }} />
                    <span className={isHidden ? 'text-gray-500 line-through' : 'text-gray-600'}>{c.label}</span>
                    {!hasData && <span className="text-gray-400 font-normal">({t('veri yok')})</span>}
                    {hasData && pct != null && !isHidden && (
                      <span className={pct >= 0 ? 'text-emerald-600' : 'text-rose-600'}>
                        {pct >= 0 ? '+' : ''}{pct.toFixed(1)}%
                      </span>
                    )}
                  </button>
                  <button type="button" onClick={() => removeCustom(c.id)} title={t('Kaldır')} className="text-gray-400 hover:text-rose-500">
                    <X className="w-3 h-3" />
                  </button>
                </span>
              );
            })}

            {custom.length < MAX_CUSTOM && (
              <button
                type="button"
                onClick={() => setSearchOpen(true)}
                className="text-xs font-semibold flex items-center gap-1 text-[#093eaa] border border-dashed border-[#093eaa]/40 rounded-md px-2 py-0.5 hover:bg-[#093eaa]/5"
              >
                <Plus className="w-3 h-3" /> {t('Kıyas ekle')}
              </button>
            )}
          </div>

          <div className="h-[420px]">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart
                data={chartData}
                margin={{ top: 5, right: 12, left: 6, bottom: 5 }}
                onMouseMove={s => {
                  if (s && s.activePayload && s.activePayload.length) {
                    setActiveRow(s.activePayload[0].payload);
                  }
                }}
                onMouseLeave={() => setActiveRow(null)}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" vertical={false} />
                <XAxis
                  dataKey="date"
                  tick={{ fontSize: 10, fill: '#9ca3af' }}
                  tickFormatter={formatTick}
                  interval="preserveStartEnd"
                  axisLine={false}
                  tickLine={false}
                  minTickGap={24}
                />
                <YAxis
                  tick={{ fontSize: 10, fill: '#9ca3af' }}
                  tickFormatter={v => `${v >= 0 ? '+' : ''}${v.toFixed(0)}%`}
                  width={46}
                  axisLine={false}
                  tickLine={false}
                />
                <ReferenceLine y={0} stroke="#e5e7eb" strokeDasharray="4 2" />
                <Tooltip content={<TooltipBox />} />
                {visibleSeries.map(s => (
                  <Line
                    key={s.key}
                    type="monotone"
                    dataKey={`${s.key}Pct`}
                    name={t(s.label)}
                    stroke={s.color}
                    strokeWidth={s.key === 'actual' ? 2.5 : 1.8}
                    dot={false}
                    activeDot={{ r: 4, strokeWidth: 0 }}
                    connectNulls
                  />
                ))}
                {visibleCustom.map(c => (
                  <Line
                    key={c.id}
                    type="monotone"
                    dataKey={`${c.safe}Pct`}
                    name={c.label}
                    stroke={c.color}
                    strokeWidth={1.8}
                    strokeDasharray="5 3"
                    dot={false}
                    activeDot={{ r: 4, strokeWidth: 0 }}
                    connectNulls
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </div>

          {/* Hover detay paneli — grafiğin altında büyük/okunaklı */}
          {(() => {
            const row = activeRow ?? lastRow;
            if (!row) return null;
            const items = [
              ...visibleSeries.map(s => ({
                key: s.key, label: t(s.label), color: s.color,
                pct: row[`${s.key}Pct`], abs: row[s.key],
              })),
              ...visibleCustom.map(c => ({
                key: c.id, label: c.label, color: c.color,
                pct: row[`${c.safe}Pct`], abs: row[c.safe],
              })),
            ];
            return (
              <div className="rounded-lg border border-gray-200 bg-gray-50/60 p-3">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm font-bold text-gray-800">{formatLongDate(row.date)}</span>
                  <span className="text-[10px] text-gray-400">
                    {activeRow ? t('İmleç noktası') : t('Bugün')}
                    {row.cost != null && !valuesHidden && (
                      <span className="ml-2">{t('Yatırılan:')} {formatMoney(row.cost, 'TRY', false)}</span>
                    )}
                  </span>
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
                  {items.map(it => (
                    <div key={it.key} className="bg-white rounded-md border border-gray-100 p-2">
                      <div className="flex items-center gap-1.5 mb-0.5">
                        <span className="w-2 h-2 rounded-full shrink-0" style={{ background: it.color }} />
                        <span className="text-[11px] font-semibold text-gray-600 truncate">{it.label}</span>
                      </div>
                      <div className={`text-sm font-bold tabular-nums ${it.pct == null ? 'text-gray-400' : it.pct >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                        {it.pct != null ? `${it.pct >= 0 ? '+' : ''}${it.pct.toFixed(1)}%` : '—'}
                      </div>
                      {it.abs != null && !valuesHidden && (
                        <div className="text-[11px] text-gray-400 tabular-nums">{formatMoney(it.abs, 'TRY', false)}</div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            );
          })()}

          {/* Açıklama */}
          <div className="text-[11px] text-gray-400 leading-relaxed border-t border-gray-100 pt-3 space-y-1">
            <p>
              {t('Her çizgi, yatırdığın paranın o araçta alış tarihinden bugüne yüzde getirisini gösterir (0% = maliyet).')}
              {' '}
              <span className="font-semibold text-[#093eaa]">{t('Gerçek')}</span>{' '}
              {t('= varlığın kendi fiyatı.')} {t('TL tutarları için çizgilerin üzerine gel.')}
            </p>
            <p>{t('Enflasyon (TÜFE) = TL alım gücü; ABD Enflasyonu = doların alım gücü (ABD CPI × USD/TRY) TL cinsinden — TL varlıklar için de geçerlidir (kur dönüşümü içerir).')}</p>
            <p>{t('Renkli etiketlere tıklayarak çizgileri açıp kapatabilirsin (Gerçek hariç). "Kıyas ekle" ile herhangi bir enstrümanı (hisse, kripto, döviz, fon, altın, emtia…) en fazla {n} tane kıyasa ekleyebilirsin.', { n: MAX_CUSTOM })}</p>
          </div>
        </>
      )}
    </div>
  );
}
