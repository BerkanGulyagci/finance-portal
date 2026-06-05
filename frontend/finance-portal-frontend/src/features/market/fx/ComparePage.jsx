import { useState, useCallback, useRef, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { X, Plus, TrendingUp, TrendingDown, BarChart2 } from 'lucide-react';
import { getFxHistory } from '../../../api/marketApi';
import { FX_META, FlagImg } from './utils/fxMeta';
import { useTranslation } from '../../../context/LanguageContext';
import { useTheme } from '../../../context/ThemeContext';

// ── Sabit renkler ─────────────────────────────────────────────────────────────
const COLORS = ['#093eaa', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#f97316'];

// ── Önerilen enstrümanlar ─────────────────────────────────────────────────────
const SUGGESTIONS = [
  { key: 'USD', label: 'USD/TRY', type: 'fx' },
  { key: 'EUR', label: 'EUR/TRY', type: 'fx' },
  { key: 'GBP', label: 'GBP/TRY', type: 'fx' },
  { key: 'CHF', label: 'CHF/TRY', type: 'fx' },
  { key: 'JPY', label: 'JPY/TRY', type: 'fx' },
  { key: 'AUD', label: 'AUD/TRY', type: 'fx' },
  { key: 'CAD', label: 'CAD/TRY', type: 'fx' },
  { key: 'SAR', label: 'SAR/TRY', type: 'fx' },
  { key: 'AED', label: 'AED/TRY', type: 'fx' },
  { key: 'KWD', label: 'KWD/TRY', type: 'fx' },
  { key: 'CNY', label: 'CNY/TRY', type: 'fx' },
  { key: 'RUB', label: 'RUB/TRY', type: 'fx' },
];

// FX geçmişi TCMB'den gün-gün çekildiği için "Tüm" yok; en uzun 10Y (cache+LKG'li, bkz. MarketFxService).
const RANGES = ['1W', '1M', '3M', '6M', '1Y', '5Y', '10Y'];
const RANGE_LABELS = { '1W': '1H', '1M': '1A', '3M': '3A', '6M': '6A', '1Y': '1Y', '5Y': '5Y', '10Y': '10Y' };

// ── ECharts Compare Chart ─────────────────────────────────────────────────────
function EChartsCompareChart({ chartData, instruments, formatDate }) {
  const chartRef = useRef(null);
  const instanceRef = useRef(null);
  const { isDark } = useTheme();

  useEffect(() => {
    if (!chartRef.current || !chartData.length) return;

    import('echarts').then(echarts => {
      if (instanceRef.current) {
        instanceRef.current.dispose();
      }

      const chart = echarts.init(chartRef.current, null, { renderer: 'canvas' });
      instanceRef.current = chart;

      const labels = chartData.map(d => formatDate(d.date));

      const series = instruments.map((inst, idx) => ({
        name: inst.label,
        type: 'line',
        data: chartData.map(d => d[inst.key] ?? null),
        smooth: false,
        symbol: 'none',
        lineStyle: { width: 2, color: COLORS[idx % COLORS.length] },
        itemStyle: { color: COLORS[idx % COLORS.length] },
        connectNulls: true,
      }));

      const option = {
        backgroundColor: 'transparent',
        animation: false,
        grid: { top: 20, right: 20, bottom: 80, left: 70 },
        xAxis: {
          type: 'category',
          data: labels,
          axisLine: { lineStyle: { color: '#e5e7eb' } },
          axisTick: { show: false },
          axisLabel: { color: '#9ca3af', fontSize: 10, interval: 'auto' },
          splitLine: { show: false },
        },
        yAxis: {
          type: 'value',
          min: 'dataMin',
          max: 'dataMax',
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: {
            color: '#9ca3af',
            fontSize: 10,
            formatter: v => `${v >= 0 ? '+' : ''}${v.toFixed(1)}%`,
          },
          splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'cross', lineStyle: { color: '#9ca3af', type: 'dashed' } },
          backgroundColor: isDark ? 'rgba(15,23,42,0.92)' : 'rgba(255,255,255,0.96)',
          borderColor: isDark ? 'rgba(255,255,255,0.12)' : '#e5e7eb',
          borderRadius: 12,
          padding: [10, 14],
          textStyle: { color: isDark ? '#e2e8f0' : '#1a1c1e' },
          formatter: params => {
            if (!params?.length) return '';
            const labelColor = isDark ? '#94a3b8' : '#6b7280';
            const mainColor = isDark ? '#e2e8f0' : '#374151';
            const borderCol = isDark ? 'rgba(255,255,255,0.12)' : '#f0f0f0';
            let html = `<div style="font-size:11px;color:${labelColor};font-weight:600;margin-bottom:8px;padding-bottom:6px;border-bottom:1px solid ${borderCol}">${params[0].axisValue}</div>`;
            params.forEach(p => {
              if (p.value == null) return;
              const isPos = p.value >= 0;
              const pctColor = isPos ? '#10b981' : '#ef4444';
              html += `<div style="display:flex;align-items:center;gap:8px;margin-bottom:6px">
                <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color};flex-shrink:0"></span>
                <span style="font-size:11px;color:${mainColor};font-weight:600;min-width:70px">${p.seriesName}:</span>
                <span style="font-weight:700;font-size:11px;color:${pctColor};margin-left:auto">${isPos ? '+' : ''}${p.value.toFixed(2)}%</span>
              </div>`;
            });
            return html;
          },
        },
        legend: {
          bottom: 32,
          textStyle: { color: '#6b7280', fontSize: 12 },
          icon: 'circle',
          itemWidth: 10,
          itemHeight: 10,
        },
        dataZoom: [
          { type: 'inside', xAxisIndex: 0, start: 0, end: 100, zoomOnMouseWheel: true },
          {
            type: 'slider',
            xAxisIndex: 0,
            start: 0,
            end: 100,
            height: 18,
            bottom: 8,
            borderColor: '#e5e7eb',
            fillerColor: 'rgba(9,62,170,0.08)',
            handleStyle: { color: '#093eaa' },
            showDetail: false,
          },
        ],
        series,
      };

      chart.setOption(option);

      const ro = new ResizeObserver(() => chart.resize());
      ro.observe(chartRef.current);

      return () => { ro.disconnect(); };
    });

    return () => {
      if (instanceRef.current) {
        instanceRef.current.dispose();
        instanceRef.current = null;
      }
    };
  }, [chartData, instruments, formatDate, isDark]);

  return <div ref={chartRef} style={{ width: '100%', height: '320px' }} />;
}

// ── Main Page ─────────────────────────────────────────────────────────────────
export default function ComparePage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  // URL'den başlangıç enstrümanlarını oku: ?symbols=USD,EUR,GBP
  function parseUrlSymbols() {
    const raw = searchParams.get('symbols');
    if (!raw) return [
      { key: 'USD', label: 'USD/TRY', type: 'fx' },
      { key: 'EUR', label: 'EUR/TRY', type: 'fx' },
    ];
    return raw.split(',')
      .map(s => s.trim().toUpperCase())
      .filter(s => s.length > 0)
      .slice(0, 7)
      .map(key => ({ key, label: `${key}/TRY`, type: 'fx' }));
  }

  const [instruments, setInstruments] = useState(parseUrlSymbols);
  const [range, setRange] = useState('1M');
  const [search, setSearch] = useState('');
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [seriesData, setSeriesData] = useState({});
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [investment, setInvestment] = useState('1000'); // TL simülasyon tutarı

  // URL'i enstrümanlara göre güncelle
  function syncUrl(newInstruments) {
    const symbols = newInstruments.map(i => i.key).join(',');
    navigate(`/market/fx/compare?symbols=${symbols}`, { replace: true });
  }

  // Enstrüman ekle
  function addInstrument(item) {
    if (instruments.find(i => i.key === item.key)) return;
    if (instruments.length >= 7) return;
    const updated = [...instruments, item];
    setInstruments(updated);
    syncUrl(updated);
    setSearch('');
    setShowSuggestions(false);
    setLoaded(false);
  }

  // Enstrüman kaldır
  function removeInstrument(key) {
    const updated = instruments.filter(i => i.key !== key);
    setInstruments(updated);
    syncUrl(updated);
    setSeriesData(prev => { const n = { ...prev }; delete n[key]; return n; });
  }

  // Arama filtresi
  const filtered = SUGGESTIONS.filter(s =>
    !instruments.find(i => i.key === s.key) &&
    (s.label.toLowerCase().includes(search.toLowerCase()) ||
     s.key.toLowerCase().includes(search.toLowerCase()))
  );

  // Veri yükle
  const loadData = useCallback(async () => {
    if (instruments.length === 0) return;
    setLoading(true);
    setLoaded(false);
    const results = {};
    await Promise.all(instruments.map(async inst => {
      try {
        const data = await getFxHistory(inst.key, range);
        results[inst.key] = data?.points ?? [];
      } catch {
        results[inst.key] = [];
      }
    }));
    setSeriesData(results);
    setLoading(false);
    setLoaded(true);
  }, [instruments, range]);

  // Bir kez karşılaştırma yapıldıysa, aralık (range) değişince otomatik yeniden yükle —
  // kullanıcının tekrar "Karşılaştır"a basmasına gerek kalmaz. (İlk yükleme yine butonla.)
  const comparedOnceRef = useRef(false);
  useEffect(() => {
    if (loaded) comparedOnceRef.current = true;
  }, [loaded]);
  useEffect(() => {
    if (comparedOnceRef.current && instruments.length > 0) {
      loadData();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [range]);

  // Rebased chart data — tüm serileri ortak tarihlerde birleştir
  function buildChartData() {
    if (!loaded) return [];

    // Tüm tarihleri topla
    const allDates = new Set();
    instruments.forEach(inst => {
      (seriesData[inst.key] ?? []).forEach(p => allDates.add(p.date));
    });
    const sortedDates = [...allDates].sort((a, b) => a.localeCompare(b));

    // Her seri için ilk değeri bul (rebasing için)
    const firstValues = {};
    instruments.forEach(inst => {
      const pts = seriesData[inst.key] ?? [];
      if (pts.length > 0) firstValues[inst.key] = parseFloat(pts[0].close);
    });

    return sortedDates.map(date => {
      const row = { date };
      instruments.forEach(inst => {
        const pts = seriesData[inst.key] ?? [];
        const pt = pts.find(p => p.date === date);
        const first = firstValues[inst.key];
        if (pt && first && first !== 0) {
          row[inst.key] = parseFloat(((parseFloat(pt.close) - first) / first * 100).toFixed(2));
        }
      });
      return row;
    });
  }

  const chartData = buildChartData();

  // ── Metrik hesaplamaları (ham fiyat verisi üzerinden) ────────────────────────
  function getMetrics(key) {
    const raw = seriesData[key] ?? [];
    if (raw.length < 2) return null;

    // Tarihe göre artan sırala — drawdown hesabı için kritik
    const pts = [...raw].sort((a, b) => a.date.localeCompare(b.date));
    const prices = pts.map(p => parseFloat(p.close));
    const startPrice = prices[0];
    const endPrice   = prices[prices.length - 1];

    // Dönem getirisi
    const periodReturn = ((endPrice - startPrice) / startPrice) * 100;

    // Max / Min
    const maxPrice = Math.max(...prices);
    const minPrice = Math.min(...prices);

    // Drawdown — tepe noktasından maksimum düşüş
    let peak = prices[0];
    let maxDrawdown = 0;
    for (const p of prices) {
      if (p > peak) peak = p;
      const dd = (p - peak) / peak;
      if (dd < maxDrawdown) maxDrawdown = dd;
    }

    // Volatilite: günlük getiri std sapması
    const returns = [];
    for (let i = 1; i < prices.length; i++) {
      if (prices[i - 1] > 0) returns.push((prices[i] - prices[i - 1]) / prices[i - 1] * 100);
    }
    const mean = returns.reduce((a, b) => a + b, 0) / (returns.length || 1);
    const variance = returns.reduce((a, b) => a + (b - mean) ** 2, 0) / (returns.length || 1);
    const volatility = Math.sqrt(variance);

    return {
      startPrice,
      endPrice,
      maxPrice,
      minPrice,
      periodReturn,
      drawdown: maxDrawdown * 100, // yüzde olarak
      volatility,
    };
  }

  // TL simülasyonu: investment TL ile startPrice'tan alıp endPrice'ta sat
  function simulate(key, investment) {
    const m = getMetrics(key);
    if (!m || !investment || isNaN(investment) || m.startPrice === 0) return null;
    const units = investment / m.startPrice;
    return units * m.endPrice;
  }

  // X ekseni formatter
  function formatDate(d) {
    if (!d) return '';
    const parts = d.split('-');
    if (range === '1W' || range === '1M') return `${parts[2]}/${parts[1]}`;
    return `${parts[1]}/${parts[0]?.slice(2)}`;
  }

  const topPerformer = loaded
    ? instruments
        .map(i => ({ ...i, ret: getMetrics(i.key)?.periodReturn ?? -Infinity }))
        .sort((a, b) => b.ret - a.ret)[0]
    : null;

  const fmt4 = v => v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
  const fmt2 = v => v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">
          {t('Enstrüman Karşılaştırma')}
        </h1>
        <p className="text-sm text-gray-500 mt-1 pl-5">
          {t('Birden fazla dövizi aynı grafikte karşılaştır — başlangıca göre yüzde değişim')}
        </p>
      </div>

      {/* Enstrüman seçici */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-5">
        <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">{t('Enstrüman Ekle')}</p>
        <div className="flex flex-wrap gap-2 mb-3">
          {instruments.map((inst, idx) => (
            <span
              key={inst.key}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-semibold text-white"
              style={{ background: COLORS[idx % COLORS.length] }}
            >
              <FlagImg cc={FX_META[inst.key]?.cc} size={16} />
              {inst.label}
              <button onClick={() => removeInstrument(inst.key)} className="ml-1 p-1 sm:p-0 hover:opacity-70">
                <X className="w-3.5 h-3.5" />
              </button>
            </span>
          ))}
          {instruments.length < 7 && (
            <div className="relative">
              <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full border-2 border-dashed border-gray-300 text-sm text-gray-400 cursor-pointer hover:border-[#093eaa] hover:text-[#093eaa] transition-colors"
                role="button"
                tabIndex={0}
                onClick={() => setShowSuggestions(true)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setShowSuggestions(true); } }}>
                <Plus className="w-3.5 h-3.5" />
                {t('Ekle')}
              </div>
              {showSuggestions && (
                <div className="absolute top-10 left-0 z-20 bg-white border border-gray-200 rounded-xl shadow-xl w-[calc(100vw-2rem)] max-w-[16rem] sm:w-64 p-2">
                  <input
                    autoFocus
                    value={search}
                    onChange={e => setSearch(e.target.value)}
                    placeholder={t('Ara... (USD, EUR...)')}
                    className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg mb-2 focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30"
                  />
                  <div className="max-h-48 overflow-y-auto space-y-0.5">
                    {filtered.map(s => (
                      <button
                        key={s.key}
                        onClick={() => addInstrument(s)}
                        className="w-full flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-blue-50 text-sm text-left transition-colors"
                      >
                        <FlagImg cc={FX_META[s.key]?.cc} size={18} />
                        <span className="font-semibold text-[#093eaa]">{s.label}</span>
                        <span className="text-gray-400 text-xs ml-auto">{FX_META[s.key]?.ad}</span>
                      </button>
                    ))}
                    {filtered.length === 0 && (
                      <p className="text-xs text-gray-400 text-center py-3">{t('Sonuç bulunamadı')}</p>
                    )}
                  </div>
                  <button onClick={() => setShowSuggestions(false)}
                    className="w-full mt-2 text-xs text-gray-400 hover:text-gray-600 py-1">
                    {t('Kapat')}
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Range + Karşılaştır butonu */}
        <div className="flex items-center gap-3 flex-wrap">
          <div className="flex gap-1">
            {RANGES.map(r => (
              <button key={r} onClick={() => setRange(r)}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                  range === r ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}>
                {RANGE_LABELS[r]}
              </button>
            ))}
          </div>
          <button
            onClick={loadData}
            disabled={loading || instruments.length === 0}
            className="ml-auto px-5 py-2 bg-[#093eaa] text-white rounded-xl text-sm font-bold hover:bg-[#0730a0] transition-all disabled:opacity-50 flex items-center gap-2"
          >
            <BarChart2 className="w-4 h-4" />
            {loading ? t('Yükleniyor...') : t('Karşılaştır')}
          </button>
        </div>
      </div>

      {/* Grafik */}
      {loaded && chartData.length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="font-bold text-gray-900">{t('Göreceli Performans (%)')}</h2>
              <p className="text-xs text-gray-400 mt-0.5">{t('Dönem başına göre yüzde değişim (rebased) · Scroll ile zoom')}</p>
            </div>
          </div>
          <EChartsCompareChart
            chartData={chartData}
            instruments={instruments}
            formatDate={formatDate}
          />
        </div>
      )}

      {/* Performans Metrikleri */}
      {loaded && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-3 py-3 sm:px-6 sm:py-4 border-b border-gray-100 flex items-center justify-between flex-wrap gap-3">
            <h2 className="font-bold text-gray-900">{t('Performans Metrikleri')}</h2>
            {topPerformer && (
              <div className="flex items-center gap-2 text-sm">
                <span className="text-gray-500">{t('En iyi:')}</span>
                <span className="font-bold text-emerald-600 flex items-center gap-1">
                  <TrendingUp className="w-4 h-4" />
                  {topPerformer.label}
                  {' '}({topPerformer.ret >= 0 ? '+' : ''}{topPerformer.ret.toFixed(2)}%)
                </span>
              </div>
            )}
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px]">
              <thead className="bg-gray-50">
                <tr>
                  {[
                    'Enstrüman',
                    'Başlangıç',
                    'Bitiş',
                    'Dönem Max',
                    'Dönem Min',
                    'Dönem Getirisi',
                    'Drawdown',
                    'Volatilite',
                  ].map((h, i) => (
                    <th key={h} className={`text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 whitespace-nowrap ${i === 0 ? 'sticky left-0 bg-gray-50 z-10' : ''}`}>
                      {t(h)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {instruments.map((inst, idx) => {
                  const m = getMetrics(inst.key);
                  const pos = (m?.periodReturn ?? 0) >= 0;
                  return (
                    <tr key={inst.key} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 sticky left-0 bg-white z-10">
                        <div className="flex items-center gap-2">
                          <span className="w-3 h-3 rounded-full flex-shrink-0" style={{ background: COLORS[idx % COLORS.length] }} />
                          <FlagImg cc={FX_META[inst.key]?.cc} size={18} />
                          <div>
                            <p className="font-bold text-sm text-gray-900">{inst.label}</p>
                            <p className="text-xs text-gray-400">{FX_META[inst.key]?.ad}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-700 font-mono">{m ? fmt4(m.startPrice) : '-'}</td>
                      <td className="px-4 py-3 text-sm text-gray-700 font-mono">{m ? fmt4(m.endPrice) : '-'}</td>
                      <td className="px-4 py-3 text-sm text-emerald-600 font-semibold font-mono">{m ? fmt4(m.maxPrice) : '-'}</td>
                      <td className="px-4 py-3 text-sm text-rose-600 font-semibold font-mono">{m ? fmt4(m.minPrice) : '-'}</td>
                      <td className="px-4 py-3">
                        {m ? (
                          <span className={`flex items-center gap-1 text-sm font-bold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                            {pos ? <TrendingUp className="w-3.5 h-3.5" /> : <TrendingDown className="w-3.5 h-3.5" />}
                            {pos ? '+' : ''}{fmt2(m.periodReturn)}%
                          </span>
                        ) : '-'}
                      </td>
                      <td className="px-4 py-3 text-sm font-semibold text-rose-600">
                        {m ? `${fmt2(m.drawdown)}%` : '-'}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">
                        {m ? `${fmt2(m.volatility)}%` : '-'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TL Yatırım Simülasyonu */}
      {loaded && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-3 py-3 sm:px-6 sm:py-4 border-b border-gray-100 flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-4 sm:flex-wrap">
            <h2 className="font-bold text-gray-900">{t('TL Yatırım Simülasyonu')}</h2>
            <div className="flex items-center gap-2 sm:ml-auto">
              <span className="text-sm text-gray-500">{t('Yatırım tutarı:')}</span>
              <input
                type="number"
                value={investment}
                onChange={e => setInvestment(e.target.value)}
                className="flex-1 sm:flex-none w-full sm:w-32 px-3 py-1.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 text-right"
                placeholder="1000"
              />
              <span className="text-sm font-semibold text-gray-600">₺</span>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  {['Enstrüman', 'Başlangıç Fiyatı', 'Alınan Birim', 'Bitiş Fiyatı', 'Nihai Değer (₺)', 'Kâr/Zarar'].map(h => (
                    <th key={h} className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 whitespace-nowrap">
                      {t(h)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {instruments.map((inst, idx) => {
                  const m = getMetrics(inst.key);
                  const inv = parseFloat(investment || 0);
                  const finalVal = simulate(inst.key, inv);
                  const profit = finalVal != null ? finalVal - inv : null;
                  const pos = (profit ?? 0) >= 0;
                  const units = m && m.startPrice > 0 ? inv / m.startPrice : null;
                  return (
                    <tr key={inst.key} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-2">
                          <span className="w-3 h-3 rounded-full flex-shrink-0" style={{ background: COLORS[idx % COLORS.length] }} />
                          <FlagImg cc={FX_META[inst.key]?.cc} size={18} />
                          <span className="font-bold text-sm text-gray-900">{inst.label}</span>
                        </div>
                      </td>
                      <td className="px-5 py-3 text-sm font-mono text-gray-700">{m ? fmt4(m.startPrice) : '-'}</td>
                      <td className="px-5 py-3 text-sm font-mono text-gray-700">
                        {units != null ? units.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 }) : '-'}
                      </td>
                      <td className="px-5 py-3 text-sm font-mono text-gray-700">{m ? fmt4(m.endPrice) : '-'}</td>
                      <td className="px-5 py-3 text-sm font-bold text-gray-900">
                        {finalVal != null ? `₺${finalVal.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '-'}
                      </td>
                      <td className="px-5 py-3">
                        {profit != null ? (
                          <span className={`text-sm font-bold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                            {pos ? '+' : ''}₺{profit.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                          </span>
                        ) : '-'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="px-5 py-2 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
            {t('* Simülasyon dönem başı ve sonu TCMB resmi kurları kullanılarak hesaplanmıştır. Gerçek alım-satım maliyetleri dahil değildir. Gösterge niteliğindedir.')}
          </div>
        </div>
      )}

      {/* Boş durum */}
      {!loaded && !loading && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 sm:p-12 text-center">
          <BarChart2 className="w-12 h-12 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-500 font-semibold">{t('Enstrümanları seçip "Karşılaştır" butonuna tıkla')}</p>
          <p className="text-gray-400 text-sm mt-1">{t('En fazla 7 enstrüman karşılaştırabilirsin')}</p>
        </div>
      )}
    </div>
  );
}
