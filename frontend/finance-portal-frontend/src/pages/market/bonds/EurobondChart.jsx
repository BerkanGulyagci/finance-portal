import { useEffect, useRef, useState, useCallback } from 'react';
import { Trash2, X, ChevronDown } from 'lucide-react';
import { init as klineInit, dispose as klineDispose, registerOverlay } from 'klinecharts';
import { getGlobalBondChart } from '../../../api/marketApi';
import { useTranslation } from '../../../i18n/LanguageContext';

// Custom overlay'leri bir kez kaydet (hisse sayfasıyla aynı; yeniden kayıt zararsız).
let overlaysReady = false;
function ensureOverlays() {
  if (overlaysReady) return;
  overlaysReady = true;
  registerOverlay({
    name: 'customRect', totalStep: 3, needDefaultPointFigure: true, needDefaultXAxisFigure: true, needDefaultYAxisFigure: true,
    createPointFigures: ({ coordinates }) => coordinates.length === 2 ? [{
      type: 'rect',
      attrs: { x: Math.min(coordinates[0].x, coordinates[1].x), y: Math.min(coordinates[0].y, coordinates[1].y),
        width: Math.abs(coordinates[1].x - coordinates[0].x), height: Math.abs(coordinates[1].y - coordinates[0].y) },
      styles: { style: 'stroke_fill', color: 'rgba(22,119,255,0.15)', borderColor: '#1677ff', borderSize: 1 },
    }] : [],
  });
  registerOverlay({
    name: 'customCircle', totalStep: 3, needDefaultPointFigure: true, needDefaultXAxisFigure: true, needDefaultYAxisFigure: true,
    createPointFigures: ({ coordinates }) => coordinates.length === 2 ? [{
      type: 'circle',
      attrs: { x: coordinates[0].x, y: coordinates[0].y,
        r: Math.sqrt(Math.pow(coordinates[1].x - coordinates[0].x, 2) + Math.pow(coordinates[1].y - coordinates[0].y, 2)) },
      styles: { style: 'stroke_fill', color: 'rgba(22,119,255,0.15)', borderColor: '#1677ff', borderSize: 1 },
    }] : [],
  });
}

// BI tahvil verisi GÜNLÜK → yatırım-vadeli (günlük) aralıklar; gün-içi yok.
const RANGES = [
  { label: '1A', value: '1M' },
  { label: '3A', value: '3M' },
  { label: '6A', value: '6M' },
  { label: '1Y', value: '1Y' },
  { label: '3Y', value: '3Y' },
  { label: '5Y', value: '5Y' },
  { label: 'Tüm', value: 'MAX' },
];

const MA_PERIODS = [
  { period: 20, color: '#f59e0b', label: 'MA20' },
  { period: 50, color: '#8b5cf6', label: 'MA50' },
  { period: 200, color: '#ef4444', label: 'MA200' },
];
function maLineStyles(periods) {
  return { lines: periods.map(p => ({ color: MA_PERIODS.find(m => m.period === p)?.color ?? '#888', size: 1.5 })) };
}
const SUB_INDICATORS = [
  { name: 'VOL', label: 'Hacim' }, { name: 'RSI', label: 'RSI' }, { name: 'MACD', label: 'MACD' },
  { name: 'KDJ', label: 'KDJ' }, { name: 'BOLL', label: 'BOLL' },
];
const DRAWING_TOOLS = [
  { group: 'Çizgiler', tools: [
    { id: 'segment', label: 'Çizgi Segmenti', icon: '╱' },
    { id: 'straightLine', label: 'Düz Çizgi (∞)', icon: '⟵⟶' },
    { id: 'rayLine', label: 'Işın', icon: '⟶' },
    { id: 'horizontalStraightLine', label: 'Yatay Çizgi', icon: '─' },
    { id: 'verticalStraightLine', label: 'Dikey Çizgi', icon: '│' },
    { id: 'priceLine', label: 'Fiyat Çizgisi', icon: '₺─' },
  ]},
  { group: 'Kanallar', tools: [
    { id: 'parallelStraightLine', label: 'Paralel Kanal', icon: '⫽' },
    { id: 'priceChannelLine', label: 'Fiyat Kanalı', icon: '▱' },
  ]},
  { group: 'Fibonacci', tools: [{ id: 'fibonacciLine', label: 'Fibonacci Retracement', icon: 'Fib' }] },
  { group: 'Şekiller', tools: [
    { id: 'customRect', label: 'Dikdörtgen', icon: '□' },
    { id: 'customCircle', label: 'Çember', icon: '○' },
  ]},
];

function DrawingToolbar({ activeTool, onSelectTool, onDeleteSelected, onClearAll, indicatorSlot }) {
  const { t } = useTranslation();
  const [openGroup, setOpenGroup] = useState(null);
  return (
    <div className="flex flex-wrap items-center gap-1.5 p-2 bg-gray-50 border border-gray-200 rounded-xl mb-2">
      {DRAWING_TOOLS.map(({ group, tools }) => (
        <div key={group} className="relative">
          <button onClick={() => setOpenGroup(openGroup === group ? null : group)}
            className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold transition-all border ${
              tools.some(tool => tool.id === activeTool) || openGroup === group
                ? 'border-[#093eaa] text-[#093eaa] bg-[#093eaa]/5'
                : 'bg-white text-gray-600 border-gray-200 hover:border-[#093eaa] hover:text-[#093eaa]'}`}>
            {t(group)}<ChevronDown className={`w-3 h-3 transition-transform ${openGroup === group ? 'rotate-180' : ''}`} />
          </button>
          {openGroup === group && (
            <div className="absolute top-full left-0 mt-1 z-50 bg-white border border-gray-200 rounded-xl shadow-lg p-1 min-w-[180px]">
              {tools.map(tool => (
                <button key={tool.id}
                  onClick={() => { onSelectTool(tool.id === activeTool ? null : tool.id); setOpenGroup(null); }}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs transition-all ${
                    tool.id === activeTool ? 'bg-[#093eaa] text-white' : 'text-gray-700 hover:bg-gray-50'}`}>
                  <span className="w-8 text-center font-mono text-[11px] opacity-70">{tool.icon}</span>
                  <span>{t(tool.label)}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      ))}
      {indicatorSlot && (<><div className="w-px h-6 bg-gray-200 mx-1" />{indicatorSlot}</>)}
      <div className="w-px h-6 bg-gray-200 mx-1" />
      <button onClick={onDeleteSelected} title={t('Seçili çizimi sil')}
        className="inline-flex items-center px-2 py-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-500 transition-all border border-gray-200"><Trash2 className="w-3.5 h-3.5" /></button>
      <button onClick={onClearAll} title={t('Tüm çizimleri temizle')}
        className="inline-flex items-center px-2 py-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-500 transition-all border border-gray-200"><X className="w-3.5 h-3.5" /></button>
    </div>
  );
}

/** Eurobond fiyat grafiği — klinecharts mum/çizgi, hisse detay sayfasıyla aynı tasarım (BI günlük OHLC). */
export default function EurobondChart({ isin }) {
  const { t } = useTranslation();
  const chartId = useRef(`kline_eb_${Date.now()}`);
  const chartRef = useRef(null);
  const paneIds = useRef({});
  const [rangeIdx, setRangeIdx] = useState(3); // 1Y
  const [mode, setMode] = useState('line');    // varsayılan çizgi grafik; candle | line
  const modeRef = useRef('line');
  useEffect(() => { modeRef.current = mode; }, [mode]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [activeMAs, setActiveMAs] = useState([]);
  const [activeSubs, setActiveSubs] = useState([]);
  const [activeTool, setActiveTool] = useState(null);
  const [indMenuOpen, setIndMenuOpen] = useState(false);

  const applyMA = useCallback((periods) => {
    const c = chartRef.current; if (!c) return;
    c.removeIndicator('candle_pane', 'MA');
    if (periods.length) c.createIndicator({ name: 'MA', calcParams: periods, styles: maLineStyles(periods) }, false, { id: 'candle_pane' });
  }, []);
  const toggleMA = useCallback((p) => setActiveMAs(prev => {
    const next = prev.includes(p) ? prev.filter(x => x !== p) : [...prev, p].sort((a, b) => a - b);
    applyMA(next); return next;
  }), [applyMA]);
  const toggleSub = useCallback((name) => setActiveSubs(prev => {
    const c = chartRef.current; if (!c) return prev;
    if (prev.includes(name)) {
      const id = paneIds.current[name];
      if (id) { try { c.removeIndicator(id, name); delete paneIds.current[name]; } catch {} }
      return prev.filter(n => n !== name);
    }
    try { const id = c.createIndicator({ name }, true, { height: 80 }); if (id) paneIds.current[name] = id; } catch {}
    return [...prev, name];
  }), []);

  const selectTool = useCallback((id) => { setActiveTool(id); if (id && chartRef.current) chartRef.current.createOverlay({ name: id }); }, []);
  const delSelected = useCallback(() => chartRef.current?.removeOverlay(), []);
  const clearAll = useCallback(() => { DRAWING_TOOLS.flatMap(g => g.tools).forEach(tool => { try { chartRef.current?.removeOverlay({ name: tool.id }); } catch {} }); setActiveTool(null); }, []);

  useEffect(() => { ensureOverlays(); }, []);

  useEffect(() => {
    const id = chartId.current;
    const chart = klineInit(id);
    chartRef.current = chart;
    paneIds.current = {};
    // Hover tooltip — mouse'un yanında floating kutu (BondEvdsHistoryChart ile aynı stil).
    chart.setStyles({
      candle: {
        type: mode === 'line' ? 'area' : 'candle_solid',
        tooltip: {
          showRule: 'follow_cross',
          showType: 'rect',
          text: { size: 12, marginTop: 4, marginBottom: 4, marginLeft: 8, marginRight: 8 },
          rect: {
            offsetLeft: 8, offsetTop: 8, offsetRight: 8,
            paddingLeft: 10, paddingRight: 10, paddingTop: 8, paddingBottom: 8,
            borderRadius: 8, borderSize: 1, borderColor: '#e5e7eb',
            color: 'rgba(255,255,255,0.94)',
          },
          custom: (data) => {
            const d = data?.current ?? {};
            const dateStr = d.timestamp
              ? new Date(d.timestamp).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' })
              : '';
            const fmt = (v) => v == null ? '—' : Number(v).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
            if (modeRef.current === 'line') {
              return [
                { title: '', value: { text: dateStr, color: '#6b7280' } },
                { title: { text: 'Fiyat:', color: '#1677ff' }, value: { text: fmt(d.close), color: '#1677ff' } },
              ];
            }
            return [
              { title: '', value: { text: dateStr, color: '#6b7280' } },
              { title: { text: 'Açılış:', color: '#6b7280' }, value: { text: fmt(d.open), color: '#111' } },
              { title: { text: 'Yüksek:', color: '#6b7280' }, value: { text: fmt(d.high), color: '#16a34a' } },
              { title: { text: 'Düşük:', color: '#6b7280' }, value: { text: fmt(d.low), color: '#dc2626' } },
              { title: { text: 'Kapanış:', color: '#6b7280' }, value: { text: fmt(d.close), color: '#111' } },
            ];
          },
        },
      },
      indicator: {
        tooltip: { showRule: 'follow_cross', showType: 'rect', showName: false, showParams: false, defaultValue: '—', text: { size: 12, marginTop: 4, marginBottom: 4, marginLeft: 8, marginRight: 8 } },
      },
      crosshair: {
        show: true,
        horizontal: { show: true, line: { show: true, style: 'dashed', dashedValue: [4, 2], size: 1, color: '#888' } },
        vertical: { show: true, line: { show: true, style: 'dashed', dashedValue: [4, 2], size: 1, color: '#888' } },
      },
    });
    setLoading(true); setError(false);
    getGlobalBondChart(isin, RANGES[rangeIdx].value)
      .then(pts => {
        if (!pts?.length) { setError(true); setLoading(false); return; }
        const data = pts
          .map(p => ({ timestamp: new Date(p.date).getTime(), open: +p.open, high: +p.high, low: +p.low, close: +p.close, volume: 0, turnover: 0 }))
          .filter(d => Number.isFinite(d.timestamp) && Number.isFinite(d.close) && d.close > 0)
          .sort((a, b) => a.timestamp - b.timestamp);
        if (!data.length) { setError(true); setLoading(false); return; }
        chart.applyNewData(data);
        if (activeMAs.length) applyMA(activeMAs);
        setLoading(false);
      })
      .catch(() => { setError(true); setLoading(false); });
    return () => { klineDispose(id); chartRef.current = null; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isin, rangeIdx]);

  // mum/çizgi değişimi (yeniden veri çekmeden stil değiştir)
  useEffect(() => { chartRef.current?.setStyles({ candle: { type: mode === 'line' ? 'area' : 'candle_solid' } }); }, [mode]);

  const indicatorSlot = (
    <div className="relative">
      <button onClick={() => setIndMenuOpen(o => !o)}
        className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold border transition-all ${
          indMenuOpen || activeMAs.length || activeSubs.length ? 'border-[#093eaa] text-[#093eaa] bg-[#093eaa]/5' : 'bg-white text-gray-600 border-gray-200 hover:border-[#093eaa]'}`}>
        {t('İndikatör')}<ChevronDown className={`w-3 h-3 transition-transform ${indMenuOpen ? 'rotate-180' : ''}`} />
      </button>
      {indMenuOpen && (
        <div className="absolute top-full left-0 mt-1 z-50 bg-white border border-gray-200 rounded-xl shadow-lg p-2 min-w-[200px]">
          <p className="text-[10px] font-bold text-gray-400 uppercase px-1 mb-1">{t('Hareketli Ortalama')}</p>
          <div className="grid grid-cols-3 gap-1 mb-2">
            {MA_PERIODS.map(m => (
              <button key={m.period} onClick={() => toggleMA(m.period)}
                className={`px-2 py-1.5 rounded-lg text-xs font-semibold border ${activeMAs.includes(m.period) ? 'text-white border-transparent' : 'bg-white text-gray-600 border-gray-200'}`}
                style={activeMAs.includes(m.period) ? { backgroundColor: m.color } : {}}>{m.label}</button>
            ))}
          </div>
          <p className="text-[10px] font-bold text-gray-400 uppercase px-1 mb-1">{t('Osilatörler')}</p>
          <div className="grid grid-cols-3 gap-1">
            {SUB_INDICATORS.map(s => (
              <button key={s.name} onClick={() => toggleSub(s.name)}
                className={`px-2 py-1.5 rounded-lg text-xs font-semibold border ${activeSubs.includes(s.name) ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'bg-white text-gray-600 border-gray-200'}`}>{t(s.label)}</button>
            ))}
          </div>
        </div>
      )}
    </div>
  );

  return (
    <div>
      <div className="flex items-center justify-between mb-2 flex-wrap gap-2">
        <div className="inline-flex rounded-lg border border-gray-200 overflow-hidden">
          <button onClick={() => setMode('candle')} className={`px-3 py-1.5 text-xs font-bold ${mode === 'candle' ? 'bg-[#093eaa] text-white' : 'bg-white text-gray-500'}`}>{t('Mum Grafik')}</button>
          <button onClick={() => setMode('line')} className={`px-3 py-1.5 text-xs font-bold ${mode === 'line' ? 'bg-[#093eaa] text-white' : 'bg-white text-gray-500'}`}>{t('Çizgi')}</button>
        </div>
        <div className="flex gap-1 flex-wrap">
          {RANGES.map((r, i) => (
            <button key={r.value} onClick={() => setRangeIdx(i)}
              className={`px-2.5 py-1 rounded-lg text-xs font-bold transition-all ${i === rangeIdx ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}>{r.label}</button>
          ))}
        </div>
      </div>

      <DrawingToolbar activeTool={activeTool} onSelectTool={selectTool} onDeleteSelected={delSelected} onClearAll={clearAll} indicatorSlot={indicatorSlot} />

      <div className="relative" style={{ height: 380 }}>
        {loading && <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-400 bg-white/70 z-10">{t('Yükleniyor...')}</div>}
        {error && !loading && <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-400 z-10">{t('Grafik verisi yok.')}</div>}
        <div id={chartId.current} style={{ width: '100%', height: '100%' }} />
      </div>
    </div>
  );
}
