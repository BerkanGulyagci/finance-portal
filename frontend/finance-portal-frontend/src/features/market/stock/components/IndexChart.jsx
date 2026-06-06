import { useEffect, useState, useRef } from 'react';
import { init as klineInit, dispose as klineDispose } from 'klinecharts';
import { getStockChart, getIndex } from '../../../../api/marketApi';
import { STOCK_CHART_RANGES, formatStockChartTimeLabel } from '../utils/stockChartRanges';
import { useTranslation } from '../../../../context/LanguageContext';
import { useTheme } from '../../../../context/ThemeContext';
import { computeKlinePricePrecision } from '../../../../utils/numberFormat';
import ChartHoverCard from '../../../../components/common/ChartHoverCard';

/**
 * Tek tip BIST endeks grafiği — hem Hisse sayfasındaki BIST 30/50/100 hem de endeks DETAY
 * sayfasında AYNI bileşen kullanılır. Grafik motoru: KLINECHARTS (alan/çizgi) — endeks DETAY
 * çizgi grafiğiyle (LineChart) AYNI motor + AYNI kaynak (getStockChart kapanış serisi).
 * "Son Değer" ve günlük % ise Endeksler listesiyle BİREBİR aynı kaynaktan (getIndex → cache
 * snapshot); son nokta snapshot fiyatıyla hizalanır → grafik ucu + "Son Değer" listeyle tutarlı.
 * Hover: tema uyumlu ChartHoverCard (onCrosshairChange ile beslenir, LineChart deseni).
 *
 * @param symbol      Yahoo sembolü (XU100.IS, XBANK.IS …)
 * @param label       Görünen ad (BIST 100 …) — showSummary=true iken başlıkta
 * @param showSummary Başlıkta canlı fiyat+% ve altta "Son Değer" göster (liste sayfası için).
 *                    Detay sayfasında false — sayfanın kendi fiyat kartı var.
 * @param height      Grafik yüksekliği (px)
 */
export default function IndexChart({ symbol, label, showSummary = true, height = 260 }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const [data, setData]         = useState([]);
  const [summary, setSummary]   = useState(null); // { price, changePercent } — Endeksler listesiyle aynı snapshot
  const [loading, setLoading]   = useState(true);
  const [rangeIdx, setRangeIdx] = useState(2); // default 1A

  const chartId       = useRef(`kline_index_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`);
  const chartRef      = useRef(null);
  const klineDataRef  = useRef([]);
  const pricePrecRef  = useRef(2);

  // Tema uyumlu hover kartı (ChartHoverCard) — imleci takip eder.
  const [hover, setHover] = useState(null);
  const [pos,   setPos]   = useState({ x: 0, y: 0, flipX: false, flipY: false });

  const activeRange = STOCK_CHART_RANGES[rangeIdx];
  const code = (symbol ?? '').replace(/\.IS$/i, '').toUpperCase();

  // Grafik serisi + endeks özeti (getIndex = liste ile aynı cache snapshot). Son noktayı snapshot
  // fiyatıyla hizala → grafik ucu + "Son Değer" listeyle birebir tutarlı.
  useEffect(() => {
    setLoading(true);
    let alive = true;
    Promise.all([
      getStockChart(symbol, activeRange.range, activeRange.interval).catch(() => null),
      getIndex(code).catch(() => null),
    ])
      .then(([res, idx]) => {
        if (!alive) return;
        const ts     = res?.timestamps  ?? [];
        const prices = res?.closePrices ?? [];
        const points = ts.map((tt, i) => ({
          ts:    Number(tt) * 1000,
          label: formatStockChartTimeLabel(tt, activeRange.range, activeRange.interval),
          price: prices[i] != null ? parseFloat(prices[i]) : null,
        })).filter(d => d.price != null && Number.isFinite(d.price));
        const listPrice = idx?.price != null ? parseFloat(idx.price) : null;
        if (listPrice != null && points.length > 0) {
          points[points.length - 1] = { ...points[points.length - 1], price: listPrice };
        }
        setData(points);
        setSummary(listPrice != null
          ? { price: listPrice, changePercent: idx?.changePercent != null ? parseFloat(idx.changePercent) : null }
          : null);
      })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [symbol, code, activeRange.range, activeRange.interval]);

  // KlineCharts render (alan grafiği — yön rengine göre yeşil/kırmızı + alan gradyanı)
  useEffect(() => {
    if (loading || data.length < 2) return;

    const id = chartId.current;
    const chart = klineInit(id);
    chartRef.current = chart;

    const klineData = data
      .map(d => ({
        timestamp: d.ts,
        open: d.price, high: d.price, low: d.price, close: d.price,
        volume: 0, turnover: 0,
      }))
      .sort((a, b) => a.timestamp - b.timestamp);

    const isUp  = klineData.at(-1).close >= klineData[0].close;
    const color = isUp ? '#10b981' : '#ef4444';

    chart.setStyles({
      candle: {
        type: 'area',
        area: {
          lineColor: color,
          lineSize: 2,
          value: 'close',
          smooth: false,
          backgroundColor: [
            { offset: 0, color: color + '33' },
            { offset: 1, color: color + '00' },
          ],
        },
        // Default metin tooltip'ini gizle → tema uyumlu ChartHoverCard kullanılır.
        tooltip: { showRule: 'none' },
      },
    });

    chart.applyNewData(klineData);

    klineDataRef.current = klineData;
    try { pricePrecRef.current = computeKlinePricePrecision(klineData.map(d => d.close)); } catch (_) {}

    try {
      chart.subscribeAction('onCrosshairChange', (cd) => {
        const arr = klineDataRef.current;
        const idx = cd?.dataIndex;
        if (idx == null || idx < 0 || !arr[idx]) { setHover(null); return; }
        const ptD = arr[idx];
        const dt = new Date(ptD.timestamp);
        const hasTime = dt.getHours() !== 0 || dt.getMinutes() !== 0;
        const dateLabel = dt.toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: '2-digit' })
          + (hasTime ? ' · ' + dt.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }) : '');
        const prec = pricePrecRef.current;
        const priceLabel = Number(ptD.close).toLocaleString('tr-TR', { minimumFractionDigits: prec, maximumFractionDigits: prec });
        // Sadece tarih + değer. Endekste ₺ sembolü yok (puan değeri). Yön rengi nötr (yeşil).
        setHover({ dateLabel, priceLabel, changePct: null, up: true, volumeLabel: null });
        const el = document.getElementById(id);
        const r = el?.getBoundingClientRect();
        if (r && cd?.x != null && cd?.y != null) {
          setPos({ x: cd.x, y: cd.y, flipX: cd.x > r.width - 200, flipY: cd.y > r.height - 96 });
        }
      });
    } catch (_) {}

    return () => { klineDispose(id); chartRef.current = null; };
  }, [data, loading, isDark]);

  return (
    <div className={showSummary ? 'bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-5 mb-6' : ''}>
      <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
        <div className="flex items-baseline gap-3 flex-wrap">
          {showSummary && <h2 className="text-base font-bold text-gray-900">{label} {t('Endeksi')}</h2>}
          {showSummary && summary?.price != null && (
            <span className="flex items-baseline gap-1.5">
              <span className="text-base font-bold text-gray-900">
                {summary.price.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}
              </span>
              {summary.changePercent != null && (
                <span className={`text-xs font-bold ${summary.changePercent >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                  {summary.changePercent >= 0 ? '+' : ''}{summary.changePercent.toFixed(2)}%
                  <span className="text-gray-400 font-normal ml-1">{t('bugün')}</span>
                </span>
              )}
            </span>
          )}
        </div>
        <div className="flex gap-1">
          {STOCK_CHART_RANGES.map((r, i) => (
            <button key={r.label} onClick={() => setRangeIdx(i)}
              className={`px-2.5 py-1 rounded-lg text-xs font-bold transition-all ${
                i === rangeIdx ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
              }`}>
              {t(r.label)}
            </button>
          ))}
        </div>
      </div>

      {/* Loading overlay — chart div her zaman DOM'da kalır */}
      <div className="relative" style={{ height }} onMouseLeave={() => setHover(null)}>
        {loading && (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--card, #fff)', zIndex: 10 }}>
            <div className="flex gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          </div>
        )}
        {!loading && data.length < 2 && (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <span className="text-gray-400 text-sm">{t('Grafik verisi yüklenemedi.')}</span>
          </div>
        )}
        <div id={chartId.current} style={{ width: '100%', height: '100%', visibility: loading || data.length < 2 ? 'hidden' : 'visible' }} />
        {/* Tema uyumlu hover kartı (imleci takip eder) */}
        <ChartHoverCard hover={hover} pos={pos} />
      </div>

      {/* Alt "X Değişim / Son Değer" satırı KALDIRILDI: başlıktaki resmi '% bugün' (getIndex)
          ile çakışıyordu — alt satır grafiğin ilk-son farkıydı (farklı değer, kafa karıştırıcı),
          Son Değer de başlıktaki fiyatın tekrarıydı. Tek otoriter gösterim: başlık. */}
    </div>
  );
}
