import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { RefreshCw, TrendingUp, TrendingDown, BarChart2 } from 'lucide-react';
import { init as klineInit, dispose as klineDispose, registerIndicator } from 'klinecharts';
import { getEvdsBondHistory, getEvdsBondDetail } from '../../../../api/marketApi';
import BondCompareSelector   from './BondCompareSelector';
import BondComparisonSummary from './BondComparisonSummary';
import UniversalCompareButton from '../../../../components/common/UniversalCompareButton';
import TrendBadge from '../../../../components/common/TrendBadge';
import IndicatorMenu from '../../../../components/common/IndicatorMenu';
import { buildTrendItem } from '../../../../utils/trendUtils';
import {
  BOND_CHART_PERIODS,
  toKlineData, normalizeSeries, mergeByDate,
  findCommonStartDate, calculatePeriodChangePercent,
  prepareBondLineChartPoints,
  filterBondHistoryByDays,
  fmtNum, fmtPct,
} from '../utils/bondChartUtils';
import { useTranslation } from '../../../../context/LanguageContext';

const PERIODS = BOND_CHART_PERIODS;

const MAIN_COLOR    = '#093eaa';
const COMPARE_COLOR = '#f97316';
const MA20_COLOR    = '#f59e0b';
const MA50_COLOR    = '#a855f7';
const MA200_COLOR   = '#ef4444';

// Karşılaştırma serisi için custom indicator register eder.
// name = kıymet kodu → tooltip'te direkt kıymet kodu görünür, "MA(1)" değil.
const registeredIndicators = new Set();
function ensureIndicatorRegistered(code) {
  if (!registeredIndicators.has(code)) {
    try {
      registerIndicator({
        name: code,
        figures: [{ key: 'val', title: `${code}: `, type: 'line' }],
        calc: () => [],  // placeholder — createIndicator'da override edilir
      });
      registeredIndicators.add(code);
    } catch (_) {
      // Zaten kayıtlıysa sessizce geç
      registeredIndicators.add(code);
    }
  }
}

// KLineCharts stil ayarları — OHLC tooltip'ini tamamen gizler
function buildChartStyles(lineColor, mainLabel) {
  return {
    candle: {
      type: 'area',
      area: {
        lineColor,
        lineSize: 2,
        value: 'close',
        smooth: true,
        backgroundColor: [
          { offset: 0, color: lineColor + '28' },
          { offset: 1, color: lineColor + '00' },
        ],
      },
      // Hover tooltip'i aç: gösterge değeri + karşılaştırma kıymeti. Çizgilerin üstünde
      // okunabilsin diye arka planlı kutu (rect) + üstten boşluk.
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
          const date = d.timestamp
            ? new Date(d.timestamp).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' })
            : '';
          return [
            { title: '', value: { text: date, color: '#6b7280' } },
            {
              title: mainLabel ? { text: `${mainLabel}:`, color: lineColor } : '',
              value: { text: fmtNum(d.close, 2), color: lineColor },
            },
          ];
        },
      },
      priceMark: {
        last: { show: true, upColor: lineColor, downColor: lineColor, noChangeColor: lineColor },
        high: { show: false },
        low:  { show: false },
      },
    },
    // Karşılaştırma/MA değerleri hover'da yan yana — gereksiz isim/parametre tekrarını gizle
    indicator: {
      tooltip: { showRule: 'follow_cross', showType: 'rect', showName: false, showParams: false, defaultValue: '—', text: { size: 12, marginTop: 4, marginBottom: 4, marginLeft: 8, marginRight: 8 } },
    },
    // Eksen yazıları net/koyu olsun (açık gri "bulanık" görünmesin)
    xAxis: { tickText: { color: '#4b5563', size: 11 } },
    yAxis: { type: 'normal', tickText: { color: '#4b5563', size: 11 } },
  };
}

// ── KLineCharts grafik bileşeni ───────────────────────────────────────────────
/**
 * Grafik verisi değişince chart tamamen yeniden oluşturulur (dispose + init).
 * MA toggle'lar ise chart instance üzerinde removeIndicator/createIndicator ile yönetilir.
 */
function BondKlineChart({
  mainPoints,
  comparePoints,
  mainCode,
  compareCode,
  chartMode,
  showMA20,
  showMA50,
  showMA200,
}) {
  const { t } = useTranslation();
  const chartId  = useRef(`kline_bond_${Date.now()}`);
  const chartRef = useRef(null);
  const isComparing = !!compareCode && comparePoints.length > 0;

  // ── Grafik verisi değişince yeniden oluştur ──────────────────────────────
  useEffect(() => {
    if (!mainPoints || mainPoints.length === 0) return;

    const id    = chartId.current;
    const chart = klineInit(id);
    chartRef.current = chart;

    if (chartMode === 'performance' && isComparing) {
      // Ortak ilk tarih bul — adil karşılaştırma için
      const commonStart = findCommonStartDate(mainPoints, comparePoints);

      // Her iki seriyi ortak baz tarihten normalize et
      const mainNorm    = normalizeSeries(mainPoints,    commonStart);
      const compareNorm = normalizeSeries(comparePoints, commonStart);
      const merged      = mergeByDate(mainNorm, compareNorm);

      // Sadece mainValue geçerli olan satırları al — index kaymasını önler
      const validMerged = merged.filter(p => p.mainValue != null && !isNaN(p.mainValue));

      const klineData = validMerged
        .map(p => ({
          timestamp: new Date(p.date).getTime(),
          open: p.mainValue, high: p.mainValue, low: p.mainValue, close: p.mainValue,
          volume: 0, turnover: 0,
        }))
        .sort((a, b) => a.timestamp - b.timestamp);

      if (klineData.length === 0) { klineDispose(id); return; }

      chart.setStyles(buildChartStyles(MAIN_COLOR, mainCode));
      chart.applyNewData(klineData);

      // Karşılaştırma serisi — validMerged ile index eşleşmesi garantili
      const compareValues = validMerged.map(p => p.compareValue);
      // Custom indicator register et — name = kıymet kodu, tooltip'te "(1)" suffix'i olmaz
      ensureIndicatorRegistered(compareCode);
      try {
        chart.createIndicator(
          {
            name: compareCode,
            figures: [{ key: 'val', title: `${compareCode}: `, type: 'line' }],
            calc: (dataList) => dataList.map((_, i) => ({ val: compareValues[i] ?? null })),
            styles: {
              lines: [{ color: COMPARE_COLOR, size: 2, smooth: true }],
            },
          },
          false,
          { id: 'candle_pane' }
        );
      } catch (e) {
        console.warn('[BondChart] compare indicator:', e);
      }

    } else {
      // Değer modu
      const klineData = toKlineData(mainPoints);
      if (klineData.length === 0) { klineDispose(id); return; }

      const isUp  = klineData[klineData.length - 1].close >= klineData[0].close;
      const color = isUp ? '#10b981' : '#ef4444';

      chart.setStyles(buildChartStyles(color, mainCode));
      chart.applyNewData(klineData);

      // MA'ları başlangıçta ekle — yeterli veri olanları; çizgi rengi buton rengiyle aynı
      const n = mainPoints.length;
      const maDefs = [[20, MA20_COLOR], [50, MA50_COLOR], [200, MA200_COLOR]]
        .filter(([p]) => (p === 20 ? showMA20 : p === 50 ? showMA50 : showMA200) && n >= p);
      if (maDefs.length > 0) {
        try {
          chart.createIndicator(
            { name: 'MA', calcParams: maDefs.map(([p]) => p), styles: { lines: maDefs.map(([, c]) => ({ color: c, size: 1.5 })) } },
            false,
            { id: 'candle_pane' }
          );
        } catch (e) {
          console.warn('[BondChart] MA init:', e);
        }
      }

      // Karşılaştırma serisi (değer modunda)
      if (isComparing) {
        const compareKline = toKlineData(comparePoints);
        const compareMap   = new Map(compareKline.map(d => [d.timestamp, d.close]));
        const compareVals  = klineData.map(d => compareMap.get(d.timestamp) ?? null);
        // Custom indicator register et — name = kıymet kodu
        ensureIndicatorRegistered(compareCode);
        try {
          chart.createIndicator(
            {
              name: compareCode,
              figures: [{ key: 'val', title: `${compareCode}: `, type: 'line' }],
              calc: (dataList) => dataList.map((_, i) => ({ val: compareVals[i] ?? null })),
              styles: {
                lines: [{ color: COMPARE_COLOR, size: 2, smooth: true }],
              },
            },
            false,
            { id: 'candle_pane' }
          );
        } catch (e) {
          console.warn('[BondChart] compare value indicator:', e);
        }
      }
    }

    return () => { klineDispose(id); };
    // MA toggle'lar bu effect'e dahil değil — ayrı effect ile yönetilir
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mainPoints, comparePoints, mainCode, compareCode, chartMode, isComparing]);

  // ── MA toggle değişince indicator güncelle (chart dispose etme) ──────────
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart || isComparing || chartMode === 'performance') return;

    try { chart.removeIndicator('candle_pane', 'MA'); } catch (_) {}

    const n = mainPoints.length;
    const maDefs = [[20, MA20_COLOR], [50, MA50_COLOR], [200, MA200_COLOR]]
      .filter(([p]) => (p === 20 ? showMA20 : p === 50 ? showMA50 : showMA200) && n >= p);

    if (maDefs.length > 0) {
      try {
        chart.createIndicator(
          { name: 'MA', calcParams: maDefs.map(([p]) => p), styles: { lines: maDefs.map(([, c]) => ({ color: c, size: 1.5 })) } },
          false,
          { id: 'candle_pane' }
        );
      } catch (e) {
        console.warn('[BondChart] MA toggle:', e);
      }
    }
    // MA yoksa indicator eklenmez — grafik temiz kalır
  }, [showMA20, showMA50, showMA200, isComparing, chartMode]);

  if (!mainPoints || mainPoints.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-[380px] gap-3 text-gray-400">
        <BarChart2 className="w-10 h-10 opacity-30" />
        <p className="text-sm">{t('Bu dönem için grafik verisi bulunamadı.')}</p>
      </div>
    );
  }

  return <div id={chartId.current} style={{ width: '100%', height: '380px' }} />;
}

// ── Toggle butonu ─────────────────────────────────────────────────────────────
function Toggle({ label, active, color, onClick, disabled, title }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border transition-all ${
        disabled
          ? 'border-gray-100 bg-gray-50 text-gray-300 cursor-not-allowed'
          : active
            ? 'border-transparent text-white'
            : 'border-gray-200 bg-gray-50 text-gray-500 hover:bg-gray-100'
      }`}
      style={active && !disabled ? { backgroundColor: color } : {}}
    >
      <span
        className="inline-block w-3 h-0.5 rounded"
        style={{ backgroundColor: disabled ? '#d1d5db' : active ? 'white' : color }}
      />
      {label}
    </button>
  );
}

// ── Ana bileşen ───────────────────────────────────────────────────────────────
export default function BondEvdsHistoryChart({
  instrumentCode,
  basePoints = [],
  baseLoading = false,
  mainDetail = null,
}) {
  const { t } = useTranslation();
  const [period, setPeriod]       = useState('ONE_MONTH');
  const [allPoints, setAllPoints] = useState(basePoints);
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState(false);

  const [showMA20, setShowMA20]   = useState(false);
  const [showMA50, setShowMA50]   = useState(false);
  const [showMA200, setShowMA200] = useState(false);
  const [chartMode, setChartMode] = useState('value');

  const [compareCode, setCompareCode]     = useState(null);
  const [comparePoints, setComparePoints] = useState([]);
  const [compareDetail, setCompareDetail] = useState(null);
  const [compareLoading, setCompareLoading] = useState(false);
  const [compareError, setCompareError]   = useState(false);

  const isComparing = !!compareCode;

  // basePoints gelince güncelle
  useEffect(() => {
    if (basePoints.length > 0) setAllPoints(basePoints);
  }, [basePoints]);

  // Period değişince veri çek
  const loadMainHistory = useCallback(() => {
    if (!instrumentCode) return;
    const periodDef = PERIODS.find(p => p.key === period);
    if (basePoints.length > 0 && periodDef && periodDef.days <= 365) {
      setError(false);
      return;
    }
    setLoading(true);
    setError(false);
    getEvdsBondHistory(instrumentCode, period)
      .then(data => setAllPoints(Array.isArray(data) ? data : []))
      .catch(() => { setAllPoints([]); setError(true); })
      .finally(() => setLoading(false));
  }, [instrumentCode, period, basePoints.length]);

  useEffect(() => { loadMainHistory(); }, [loadMainHistory]);

  // Karşılaştırma kıymeti seçilince veri çek
  useEffect(() => {
    if (!compareCode) {
      setComparePoints([]);
      setCompareDetail(null);
      setCompareError(false);
      return;
    }
    setCompareLoading(true);
    setCompareError(false);
    Promise.all([
      getEvdsBondHistory(compareCode, period),
      getEvdsBondDetail(compareCode),
    ])
      .then(([hist, detail]) => {
        setComparePoints(Array.isArray(hist) ? hist : []);
        setCompareDetail(detail);
      })
      .catch(() => {
        setComparePoints([]);
        setCompareDetail(null);
        setCompareError(true);
      })
      .finally(() => setCompareLoading(false));
  }, [compareCode, period]);

  // Karşılaştırma seçilince performans moduna geç; kaldırılınca değer moduna dön
  useEffect(() => {
    if (compareCode) {
      setChartMode('performance');
      // MA'lar karşılaştırma modunda devre dışı
    } else {
      setChartMode('value');
      setShowMA20(false);
      setShowMA50(false);
      setShowMA200(false);
    }
  }, [compareCode]);

  const periodDays = useMemo(
    () => (PERIODS.find((p) => p.key === period) ?? PERIODS[1]).days,
    [period],
  );

  const statsPoints = useMemo(
    () => filterBondHistoryByDays(allPoints, periodDays),
    [allPoints, periodDays],
  );
  const statsComparePoints = useMemo(
    () => filterBondHistoryByDays(comparePoints, periodDays),
    [comparePoints, periodDays],
  );

  const { points: filteredPoints, usesWeeklySampling } = useMemo(
    () => prepareBondLineChartPoints(allPoints, period),
    [allPoints, period],
  );

  const { points: filteredComparePoints } = useMemo(
    () => prepareBondLineChartPoints(comparePoints, period),
    [comparePoints, period],
  );

  const mainPeriodPct = useMemo(
    () => calculatePeriodChangePercent(statsPoints),
    [statsPoints],
  );
  const comparePeriodPct = useMemo(
    () => calculatePeriodChangePercent(statsComparePoints),
    [statsComparePoints],
  );
  const periodPos = (mainPeriodPct ?? 0) >= 0;
  const currentPeriod = PERIODS.find(p => p.key === period) ?? PERIODS[1];
  const isLoading = loading || baseLoading || compareLoading;

  // İndikatör için yeterli veri var mı? (kısa geçmişte MA200 dolmaz)
  const n = filteredPoints.length;
  const maTitle = (need) => n < need ? t('Yeterli veri yok ({n} nokta gerekir)', { n: need }) : undefined;

  // Trend rozeti — gösterge serisinden (MA20/50)
  const trendItem = useMemo(
    () => buildTrendItem(filteredPoints.map(p => parseFloat(p.indicatorValue)), 'BOND'),
    [filteredPoints],
  );

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      {/* ── Başlık satırı ── */}
      <div className="flex items-start justify-between mb-4 flex-wrap gap-3">
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h2 className="font-bold text-gray-900">{t('TCMB EVDS Gösterge Değeri Grafiği')}</h2>
            {!isComparing && trendItem && <TrendBadge item={trendItem} size="xs" />}
          </div>
          {/* Karşılaştırma yoksa sadece ana kıymet değişimi */}
          {!isComparing && mainPeriodPct != null && (
            <span className={`text-sm font-semibold flex items-center gap-1 mt-0.5 ${(mainPeriodPct ?? 0) >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
              {(mainPeriodPct ?? 0) >= 0 ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
              {t(currentPeriod.label)} {t('değişim:')} {fmtPct(mainPeriodPct, 2)}
            </span>
          )}
          {/* Karşılaştırma varsa her iki kıymetin değişimi yan yana */}
          {isComparing && (
            <div className="flex items-center gap-4 mt-1 flex-wrap">
              {mainPeriodPct != null && (
                <span className="flex items-center gap-1.5 text-sm font-semibold">
                  <span className="inline-block w-3 h-0.5 rounded bg-[#093eaa]" />
                  <span className="text-gray-600 font-mono text-xs">{instrumentCode}</span>
                  <span className={(mainPeriodPct ?? 0) >= 0 ? 'text-emerald-600' : 'text-rose-600'}>
                    {fmtPct(mainPeriodPct, 2)}
                  </span>
                </span>
              )}
              {comparePeriodPct != null && (
                <span className="flex items-center gap-1.5 text-sm font-semibold">
                  <span className="inline-block w-3 h-0.5 rounded bg-orange-500" />
                  <span className="text-gray-600 font-mono text-xs">{compareCode}</span>
                  <span className={(comparePeriodPct ?? 0) >= 0 ? 'text-emerald-600' : 'text-rose-600'}>
                    {fmtPct(comparePeriodPct, 2)}
                  </span>
                </span>
              )}
            </div>
          )}
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5">
            {PERIODS.map(p => (
              <button key={p.key} onClick={() => setPeriod(p.key)}
                className={`px-3 py-1 rounded-md text-xs font-semibold whitespace-nowrap transition-all ${
                  period === p.key ? 'bg-white text-[#093eaa] shadow-sm' : 'text-gray-500 hover:text-gray-800'
                }`}>
                {t(p.label)}
              </button>
            ))}
          </div>
          <button onClick={loadMainHistory}
            className="p-1.5 rounded-lg bg-gray-100 hover:bg-gray-200 transition-all" title={t('Yenile')}>
            <RefreshCw className={`w-3.5 h-3.5 text-gray-500 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* ── Kontrol satırı ── */}
      <div className="flex items-center gap-3 mb-4 flex-wrap">
        {/* Grafik modu — karşılaştırma yoksa sadece Değer, varsa sadece Performans */}
        {!isComparing ? (
          <div className="flex gap-1 bg-gray-100 rounded-lg p-0.5">
            <span className="px-3 py-1 rounded-md text-xs font-bold bg-white text-gray-900 shadow-sm">
              {t('Değer Grafiği')}
            </span>
          </div>
        ) : (
          <div className="flex gap-1 bg-gray-100 rounded-lg p-0.5">
            <span className="px-3 py-1 rounded-md text-xs font-bold bg-white text-gray-900 shadow-sm">
              {t('Performans Grafiği')}
            </span>
          </div>
        )}

        {/* İndikatör (MA20/50/200) — karşılaştırma modunda + yetersiz veride pasif */}
        <IndicatorMenu
          maDefs={[{ period: 20, color: MA20_COLOR, label: 'MA20' }, { period: 50, color: MA50_COLOR, label: 'MA50' }, { period: 200, color: MA200_COLOR, label: 'MA200' }]}
          activeMAs={[...(showMA20 ? [20] : []), ...(showMA50 ? [50] : []), ...(showMA200 ? [200] : [])]}
          onToggleMA={(p) => { if (p === 20) setShowMA20(v => !v); else if (p === 50) setShowMA50(v => !v); else setShowMA200(v => !v); }}
          dataLen={n}
          disabled={isComparing}
        />

        {/* Karşılaştır */}
        <div className="ml-auto flex items-center gap-2">
          <div title={t('Aynı grafikte başka DİBS kıymetleriyle kıyasla')}>
            <BondCompareSelector
              mainCode={instrumentCode}
              compareCode={compareCode}
              onSelect={code => setCompareCode(code)}
              onClear={() => setCompareCode(null)}
            />
          </div>
          <UniversalCompareButton assetType="BOND" symbol={instrumentCode} name={instrumentCode} />
        </div>
      </div>

      {/* Karşılaştırma legend — her iki kıymet, renk + kod + son değer */}
      {isComparing && (
        <div className="flex items-center gap-6 mb-3 bg-gray-50 rounded-lg px-3 py-2 flex-wrap">
          <span className="flex items-center gap-2 text-xs">
            <span className="inline-block w-5 h-0.5 rounded bg-[#093eaa]" />
            <span className="font-mono font-bold text-gray-700">{instrumentCode}</span>
            {filteredPoints.length > 0 && (
              <span className="text-gray-500">
                {fmtNum(parseFloat(filteredPoints[filteredPoints.length - 1]?.indicatorValue), 2)}
              </span>
            )}
          </span>
          <span className="flex items-center gap-2 text-xs">
            <span className="inline-block w-5 h-0.5 rounded bg-orange-500" />
            <span className="font-mono font-bold text-gray-700">{compareCode}</span>
            {filteredComparePoints.length > 0 && (
              <span className="text-gray-500">
                {fmtNum(parseFloat(filteredComparePoints[filteredComparePoints.length - 1]?.indicatorValue), 2)}
              </span>
            )}
          </span>
          {chartMode === 'performance' && (
            <span className="ml-auto text-gray-400 text-xs">{t('İlk değer = 100 bazlı normalize')}</span>
          )}
        </div>
      )}

      {/* ── Grafik alanı ── */}
      <div className="relative" style={{ minHeight: '380px' }}>
        {isLoading && (
          <div className="absolute inset-0 flex items-center justify-center bg-white/70 z-10 rounded-xl">
            <div className="flex gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          </div>
        )}

        {!isLoading && error && (
          <div className="flex flex-col items-center justify-center h-[380px] gap-3 text-gray-400">
            <BarChart2 className="w-10 h-10 opacity-30" />
            <p className="text-sm">{t('Grafik verisi şu an alınamadı.')}</p>
          </div>
        )}

        {compareError && !isLoading && (
          <div className="mb-2 px-3 py-2 bg-amber-50 border border-amber-200 rounded-lg text-xs text-amber-700">
            {t('Karşılaştırma verisi alınamadı. Ana kıymet grafiği gösteriliyor.')}
          </div>
        )}

        {!isLoading && !error && (
          <BondKlineChart
            mainPoints={filteredPoints}
            comparePoints={filteredComparePoints}
            mainCode={instrumentCode}
            compareCode={compareCode}
            chartMode={chartMode}
            showMA20={showMA20}
            showMA50={showMA50}
            showMA200={showMA200}
          />
        )}
      </div>

      {/* ── Karşılaştırma özet kartları ── */}
      {isComparing && !isLoading && (
        <BondComparisonSummary
          mainCode={instrumentCode}
          mainDetail={mainDetail}
          mainHistory={filteredPoints}
          compareCode={compareCode}
          compareDetail={compareDetail}
          compareHistory={filteredComparePoints}
          period={t(currentPeriod.label)}
        />
      )}

      {period === 'FIVE_YEARS' && usesWeeklySampling && (
        <p className="text-xs text-amber-700/90 mt-2">
          {t('5Y görünüm: EVDS günlük verisi haftalık son değerle sadeleştirildi (okunabilirlik).')}
        </p>
      )}

      <p className="text-xs text-gray-400 mt-3">
        {t('Kaynak: TCMB EVDS · Günlük gösterge değerleri · Alış/satış fiyatı değildir')}
      </p>
    </div>
  );
}
