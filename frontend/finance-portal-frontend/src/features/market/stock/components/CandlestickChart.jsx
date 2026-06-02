import { useEffect, useState, useCallback, useRef, useMemo } from 'react';
import { SlidersHorizontal, ChevronDown } from 'lucide-react';
import { init as klineInit, dispose as klineDispose } from 'klinecharts';
import DrawingToolbar from './DrawingToolbar';
import { getStockOhlc } from '../../../../api/marketApi';
import { prefGet, prefSet } from '../../../../api/prefs';
import {
  MA_PERIODS, SUB_INDICATORS,
  maLineStyles, STOCK_WARMUP_RANGE, RANGE_WINDOW_MS, fitVisibleToWindow,
} from '../utils/stockChartConfig';
import { useTranslation } from '../../../../context/LanguageContext';

// Detay grafiği: yatırım-vadeli aralıklarda (1A–Tüm) GÜNLÜK çubuk kullanılır → MA20/50/200
// her zaman 20/50/200 GÜN demektir (tutarlı; "Tüm"de 200 ay gibi saçma olmaz). Yalnız 1G/1H
// gün-içi kalır (orada MA gün-içi çubuk, normal). MA ısınması fitVisibleToWindow ile sağlanır.
const OHLC_RANGES = [
  { label: '1G', range: '1d',  interval: '5m' },
  { label: '1H', range: '5d',  interval: '1h' },
  { label: '1A', range: '1mo', interval: '1d' },
  { label: '3A', range: '3mo', interval: '1d' },
  { label: '6A', range: '6mo', interval: '1d' },
  { label: '1Y', range: '1y',  interval: '1d' },
  { label: '5Y', range: '5y',  interval: '1d' },
  // 'max'+'1d' Yahoo'da ~aylığa seyrekleşiyor (MA200 yine 200 ay olur); '10y' gerçek günlük verir.
  { label: 'Tüm', range: '10y', interval: '1d' },
];

export default function CandlestickChart({ symbol }) {
  const { t } = useTranslation();
  const chartId = useRef(`kline_${Date.now()}`);
  const chartRef = useRef(null);
  const indicatorPaneIds = useRef({}); // Her indikatör için pane ID'sini sakla
  const overlaysRef = useRef(new Map()); // canlıOverlayId -> kayıt nesnesi (o aralıkta GÖRÜNEN çizimler)
  const allOverlaysRef = useRef([]);     // sembolün TÜM kayıtlı çizimleri (görünen + pencere dışı) — kaynak gerçek
  const [ohlcRangeIdx, setOhlcRangeIdx] = useState(3);
  const [activeMAs, setActiveMAs] = useState([]);
  const [activeSubInds, setActiveSubInds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(false);
  const [activeTool, setActiveTool] = useState(null);
  const [indMenuOpen, setIndMenuOpen] = useState(false);

  const rangeConfig = OHLC_RANGES[ohlcRangeIdx];
  const { range, interval } = rangeConfig;

  // ── Çizim (overlay) KALICILIĞI: localStorage + (giriş varsa) sunucu senkronu → cihazlar arası.
  // Sembol başına TEK liste (chart-overlays:{symbol}) — TÜM aralıklarda ortak. Overlay'ler veri-koordinatlı
  // (timestamp+değer) olduğu için aralık değişince klinecharts onları kendi tarihlerine sabitler; tarihi
  // yüklü verinin dışında kalan çizim ekranın dışında kalır (kaydırınca görünür), yanlış yere KONMAZ.
  const persistOverlays = useCallback(() => {
    prefSet(`chart-overlays:${symbol}`, allOverlaysRef.current);
  }, [symbol]);
  const trackOverlay = useCallback((ov) => {
    if (!ov?.id) return;
    const points = (ov.points || []).map(p => ({ timestamp: p.timestamp, value: p.value }));
    const existing = overlaysRef.current.get(ov.id);
    if (existing) {
      // Taşıma/güncelleme: kaydı YERİNDE güncelle (allOverlaysRef'teki aynı referans → tam liste de güncellenir)
      existing.name = ov.name;
      existing.points = points;
    } else {
      // Yeni çizim: hem canlı haritaya hem tam listeye ekle (aynı nesne referansı)
      const rec = { name: ov.name, points };
      overlaysRef.current.set(ov.id, rec);
      allOverlaysRef.current.push(rec);
    }
    persistOverlays();
  }, [persistOverlays]);
  const untrackOverlay = useCallback((ov) => {
    if (!ov?.id) return;
    const rec = overlaysRef.current.get(ov.id);
    overlaysRef.current.delete(ov.id);
    if (rec) {
      const i = allOverlaysRef.current.indexOf(rec);
      if (i >= 0) allOverlaysRef.current.splice(i, 1);
    }
    persistOverlays();
  }, [persistOverlays]);
  const overlayEvents = useMemo(() => ({
    onDrawEnd:        (e) => { trackOverlay(e.overlay); return false; },
    onPressedMoveEnd: (e) => { trackOverlay(e.overlay); return false; },
    onRemoved:        (e) => { untrackOverlay(e.overlay); return false; },
  }), [trackOverlay, untrackOverlay]);

  // MA toggle — calcParams ile tüm aktif periyotları tek seferde set et
  const applyMA = useCallback((periods) => {
    const chart = chartRef.current;
    if (!chart) return;
    chart.removeIndicator('candle_pane', 'MA');
    if (periods.length > 0) {
      chart.createIndicator(
        { name: 'MA', calcParams: periods, styles: maLineStyles(periods) },
        false,
        { id: 'candle_pane' }
      );
    }
  }, []);

  const toggleMA = useCallback((period) => {
    setActiveMAs(prev => {
      const next = prev.includes(period)
        ? prev.filter(p => p !== period)
        : [...prev, period].sort((a, b) => a - b);
      applyMA(next);
      return next;
    });
  }, [applyMA]);

  // Alt indikatör toggle - pane ID tracking ile
  const toggleSubIndicator = useCallback((indName) => {
    const chart = chartRef.current;
    if (!chart) return;
    
    setActiveSubInds(prev => {
      const isCurrentlyActive = prev.includes(indName);
      
      if (isCurrentlyActive) {
        // Kaldır
        const paneId = indicatorPaneIds.current[indName];
        if (paneId) {
          try {
            chart.removeIndicator(paneId, indName);
            delete indicatorPaneIds.current[indName];
          } catch (e) {
            console.error('Error removing indicator:', indName, e);
          }
        }
        return prev.filter(n => n !== indName);
      } else {
        // Ekle
        try {
          const paneId = chart.createIndicator(
            { name: indName },
            true,
            { height: 80 }
          );
          if (paneId) {
            indicatorPaneIds.current[indName] = paneId;
          }
        } catch (e) {
          console.error('Error adding indicator:', indName, e);
        }
        return [...prev, indName];
      }
    });
  }, []);

  const handleSelectTool = useCallback((toolId) => {
    setActiveTool(toolId);
    if (!chartRef.current) return;
    if (toolId) {
      // overlayEvents ile çiz → onDrawEnd'de kaydedilir (kalıcı)
      chartRef.current.createOverlay({ name: toolId, ...overlayEvents });
    }
  }, [overlayEvents]);

  // Çizimleri sil → grafikten kaldır + KALICI kaydı da temizle (yoksa refresh/başka cihazda geri gelir).
  // klinecharts bulk removeOverlay'de onRemoved'ı her zaman tetiklemeyebilir; bu yüzden depoyu elle boşaltıyoruz.
  const clearAllOverlays = useCallback(() => {
    try { chartRef.current?.removeOverlay(); } catch { /* yoksay */ }
    overlaysRef.current.clear();
    allOverlaysRef.current = [];
    persistOverlays();
  }, [persistOverlays]);

  const handleDeleteSelected = useCallback(() => { clearAllOverlays(); }, [clearAllOverlays]);
  const handleClearAll = useCallback(() => { clearAllOverlays(); setActiveTool(null); }, [clearAllOverlays]);

  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') setActiveTool(null); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  useEffect(() => {
    const id = chartId.current;
    const chart = klineInit(id);
    chartRef.current = chart;
    indicatorPaneIds.current = {}; // Temizle

    setLoading(true);
    setError(false);

    const fetchRange = STOCK_WARMUP_RANGE[range] ?? range;
    getStockOhlc(symbol, fetchRange, interval)
      .then(data => {
        if (!data?.length) { setError(true); setLoading(false); return; }
        const klineData = data
          .map(d => ({
            timestamp: Number(d.time) * 1000,
            open:   parseFloat(d.open),
            high:   parseFloat(d.high),
            low:    parseFloat(d.low),
            close:  parseFloat(d.close),
            volume: Number(d.volume ?? 0),
            turnover: 0,
          }))
          .filter(d => !isNaN(d.open) && d.open > 0)
          .sort((a, b) => a.timestamp - b.timestamp);

        chartRef.current?.applyNewData(klineData);
        // Görünür alanı yalnız seçili pencereye sığdır (ısınma verisi sola kayar, MA dolu çizilir)
        const winMsC = RANGE_WINDOW_MS[range];
        fitVisibleToWindow(chartRef.current, id, klineData, (winMsC && winMsC !== Infinity) ? Date.now() - winMsC : 0);

        // Data yüklendikten SONRA aktif indikatörleri ekle
        if (chartRef.current) {
          // MA ekle
          if (activeMAs.length > 0) {
            chartRef.current.createIndicator(
              { name: 'MA', calcParams: activeMAs, styles: maLineStyles(activeMAs) },
              false,
              { id: 'candle_pane' }
            );
          }

          // Alt indikatörleri ekle ve pane ID'lerini kaydet
          activeSubInds.forEach(indName => {
            try {
              const paneId = chartRef.current.createIndicator(
                { name: indName },
                true,
                { height: 80 }
              );
              if (paneId) {
                indicatorPaneIds.current[indName] = paneId;
              }
            } catch (e) {
              console.error('Error creating indicator in useEffect:', indName, e);
            }
          });

          // ── Kaydedilmiş çizimleri geri yükle (refresh / başka cihaz / aralık değişimi sonrası) ──
          // Sembol başına TEK liste (tüm aralıklar ortak). Overlay'ler veri-koordinatlı (timestamp+değer);
          // klinecharts her birini kendi tarihine sabitler. Yüklü verinin DIŞINDA kalan noktalar grafiğin
          // dışına (sola/sağa) düşer — ekranda yanlış yere konmaması için bu aralıkta görünmeyecekleri
          // OLUŞTURMUYORUZ ama kayıtta TUTUYORUZ (allOverlaysRef), aralık değişince yeniden değerlendiriliyor.
          overlaysRef.current.clear();
          const savedOverlays = prefGet(`chart-overlays:${symbol}`, []) || [];
          allOverlaysRef.current = Array.isArray(savedOverlays) ? savedOverlays : [];
          const dataMin = klineData.length ? klineData[0].timestamp : -Infinity;
          const dataMax = klineData.length ? klineData[klineData.length - 1].timestamp : Infinity;
          allOverlaysRef.current.forEach(ov => {
            const pts = ov.points || [];
            // En az bir noktası yüklü veri penceresinde olan çizimi göster (yatay çizgi gibi tek-değerli
            // overlay'ler tarihsiz olabilir → onları da göster). Hiçbir noktası pencerede değilse atla.
            const hasTs = pts.some(p => p && p.timestamp != null);
            const inWindow = !hasTs || pts.some(p => p.timestamp == null || (p.timestamp >= dataMin && p.timestamp <= dataMax));
            if (!inWindow) return;
            try {
              const oid = chartRef.current.createOverlay({ name: ov.name, points: ov.points, ...overlayEvents });
              const realId = Array.isArray(oid) ? oid[0] : oid;
              if (realId) overlaysRef.current.set(realId, ov);
            } catch { /* yoksay */ }
          });
        }

        setLoading(false);
      })
      .catch(() => { setError(true); setLoading(false); });

    // Cleanup
    return () => { 
      klineDispose(id);
      indicatorPaneIds.current = {};
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [symbol, range, interval]);

  // İndikatör menüsü (çizim araç çubuğunun yanına yerleşir)
  const indicatorDropdown = (
    <div className="relative">
      <button
        onClick={() => setIndMenuOpen(v => !v)}
        className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold border transition-all ${
          indMenuOpen || (activeMAs.length + activeSubInds.length) > 0
            ? 'bg-[#093eaa] text-white border-[#093eaa]'
            : 'bg-white text-gray-600 border-gray-200 hover:border-[#093eaa] hover:text-[#093eaa]'
        }`}
      >
        <SlidersHorizontal className="w-3.5 h-3.5" />
        {t('İndikatör')}
        {(activeMAs.length + activeSubInds.length) > 0 && (
          <span className="inline-flex items-center justify-center min-w-[16px] h-4 px-1 rounded-full bg-[#093eaa] text-white text-[10px] font-bold">
            {activeMAs.length + activeSubInds.length}
          </span>
        )}
        <ChevronDown className={`w-3 h-3 transition-transform ${indMenuOpen ? 'rotate-180' : ''}`} />
      </button>

      {indMenuOpen && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setIndMenuOpen(false)} />
          <div className="absolute left-0 top-full mt-1 z-50 bg-white border border-gray-200 rounded-xl shadow-lg p-3 w-64">
            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">{t('Hareketli Ortalama')}</p>
            <div className="flex flex-wrap gap-1.5 mb-3">
              {MA_PERIODS.map(({ period, color, label }) => {
                const active = activeMAs.includes(period);
                return (
                  <button key={period} onClick={() => toggleMA(period)}
                    className={`px-2.5 py-1 rounded-md text-xs font-bold transition-all border ${
                      active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'
                    }`}
                    style={active ? { backgroundColor: color, borderColor: color } : {}}>
                    {label}
                  </button>
                );
              })}
            </div>
            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">{t('Osilatör / İndikatör')}</p>
            <div className="flex flex-wrap gap-1.5">
              {SUB_INDICATORS.map(({ name, label, color }) => {
                const active = activeSubInds.includes(name);
                return (
                  <button key={name} onClick={() => toggleSubIndicator(name)}
                    className={`px-2.5 py-1 rounded-md text-xs font-semibold transition-all border ${
                      active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'
                    }`}
                    style={active ? { backgroundColor: color, borderColor: color } : {}}>
                    {label}
                  </button>
                );
              })}
            </div>
          </div>
        </>
      )}
    </div>
  );

  return (
    <div>
      {/* ── Zaman aralığı — segmented, ortalı ── */}
      <div className="flex justify-center mb-3">
        <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5 overflow-x-auto max-w-full [&::-webkit-scrollbar]:hidden">
          {OHLC_RANGES.map((r, i) => (
            <button key={r.label} onClick={() => setOhlcRangeIdx(i)}
              className={`px-3 py-1 rounded-md text-xs font-semibold whitespace-nowrap transition-all ${
                i === ohlcRangeIdx ? 'bg-white text-[#093eaa] shadow-sm' : 'text-gray-500 hover:text-gray-800'
              }`}>
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {/* ── Drawing Toolbar (İndikatör menüsü çizim gruplarının yanında) ── */}
      <DrawingToolbar
        activeTool={activeTool}
        onSelectTool={handleSelectTool}
        onDeleteSelected={handleDeleteSelected}
        onClearAll={handleClearAll}
        indicatorSlot={indicatorDropdown}
      />

      <div className="relative">
        {loading && (
          <div className="absolute inset-0 flex items-center justify-center bg-white/80 z-10">
            <div className="flex gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          </div>
        )}
        {error && !loading && (
          <div className="absolute inset-0 flex items-center justify-center text-gray-400 text-sm">
            {t('Mum grafiği verisi yüklenemedi.')}
          </div>
        )}
        <div id={chartId.current} style={{ width: '100%', height: '460px' }} />
      </div>
      <p className="text-xs text-gray-400 mt-2">Kaynak: Yahoo Finance · OHLC verisi</p>
    </div>
  );
}
