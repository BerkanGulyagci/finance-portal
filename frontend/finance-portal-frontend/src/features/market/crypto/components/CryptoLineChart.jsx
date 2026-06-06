import { useEffect, useRef, useState } from 'react';
import { init as klineInit, dispose as klineDispose } from 'klinecharts';
import { computeKlinePricePrecision, computeKlineVolumePrecision } from '../../../../utils/numberFormat';
import { COMPARE_COLORS, MA_OPTIONS } from '../utils/cryptoChartConfig';
import { useTranslation } from '../../../../context/LanguageContext';
import { useTheme } from '../../../../context/ThemeContext';
import { useChartDrawings } from '../../../../hooks/useChartDrawings';
import { useYAxisWheelZoom } from '../../../../hooks/useYAxisWheelZoom';
import DrawingToolbar from '../../stock/components/DrawingToolbar';

export default function CryptoLineChart({ chartData, currency, compareCoins, compareData, coinId, mainCoinSymbol, activeMAs }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const chartId = useRef(`kline_crypto_${Date.now()}`);
  const chartRef = useRef(null);
  const isComparing = compareCoins && compareCoins.length > 0;

  // Hover tooltip state
  const [hoverData, setHoverData] = useState(null);

  // Karşılaştırma için normalize edilmiş veri ref (tooltip'te kullanmak için)
  const normalizedDataRef = useRef([]);
  const compareOverlayRef = useRef({}); // {coinId: [{ts, value}]}

  // Çizim kalıcılığı — ortak hook. Karşılaştırma modunda kapalı (persistKey boş → no-op).
  const {
    activeTool, handleSelectTool, handleDeleteSelected, handleClearAll, restoreOverlays,
  } = useChartDrawings({
    chartRef, chartIdRef: chartId,
    persistKey: (coinId && !isComparing) ? `chart-overlays:crypto:${coinId}` : '',
  });

  // Fiyat ekseninde fare tekerleği ile dikey zoom
  useYAxisWheelZoom(chartRef);

  useEffect(() => {
    if (!chartData || chartData.length === 0) return;

    const id = chartId.current;
    const chart = klineInit(id);
    chartRef.current = chart;

    if (isComparing) {
      // Karşılaştırma modu: % bazlı normalize edilmiş veriler
      const base0 = chartData[0]?.price;
      if (!base0) return;

      const normalizedData = chartData.map((point) => {
        const pctChange = base0 > 0 ? ((point.price - base0) / base0) * 100 : 0;
        
        return {
          timestamp: point.ts,
          open: pctChange,
          high: pctChange,
          low: pctChange,
          close: pctChange,
          volume: 0,
          turnover: 0,
        };
      }).filter(d => !isNaN(d.close)).sort((a, b) => a.timestamp - b.timestamp);

      if (normalizedData.length === 0) return;

      // Normalize data'yı ref'e kaydet (tooltip için)
      normalizedDataRef.current = normalizedData;

      // Ana coin için line style
      chart.setStyles({
        candle: {
          type: 'area',
          tooltip: {
            showRule: 'none',   // OHLC satırını kapat
          },
          priceMark: {
            last: { show: false },  // Sağdaki son fiyat etiketini kapat
          },
        },
        indicator: {
          lastValueMark: { show: false },
          tooltip: { showRule: 'none' },
        },
        crosshair: {
          horizontal: {
            text: { show: false },
          },
        },
      });

      chart.applyNewData(normalizedData);

      // Ana coin çizgisi (candle olarak)
      chart.setStyles({
        candle: {
          type: 'area',
          area: {
            lineColor: COMPARE_COLORS[0],
            lineSize: 2,
            value: 'close',
            smooth: true,
            backgroundColor: [
              { offset: 0, color: COMPARE_COLORS[0] + '15' },
              { offset: 1, color: COMPARE_COLORS[0] + '00' },
            ],
          },
        },
      });

      // Karşılaştırma coinleri için custom indicator ekle
      compareCoins.forEach((c, i) => {
        const prices = compareData[c.id] ?? [];
        const base = prices[0]?.[1];
        if (!base || base === 0) return;

        // Her karşılaştırma coini için overlay line ekle
        const overlayData = normalizedData.map(point => {
          // Timestamp bazlı en yakın noktayı bul
          let closest = prices[0];
          let minDiff = Math.abs(prices[0][0] - point.timestamp);
          for (let j = 1; j < prices.length; j++) {
            const diff = Math.abs(prices[j][0] - point.timestamp);
            if (diff < minDiff) { minDiff = diff; closest = prices[j]; }
            else break;
          }
          
          const pctChange = ((closest[1] - base) / base) * 100;
          return { value: pctChange };
        });

        // Custom overlay olarak ekle
        try {
          chart.createIndicator({
            name: 'MA',
            calcParams: [1], // Dummy MA, sadece çizgi çizmek için
            figures: [{
              key: 'ma',
              title: '',   // Grafik üzerindeki coin adı yazısını kapat
              type: 'line',
              baseValue: 0,
              styles: (data, indicator, defaultStyles) => {
                return {
                  line: {
                    style: 'solid',
                    smooth: true,
                    size: 2,
                    color: COMPARE_COLORS[i + 1] || '#6b7280',
                  }
                };
              }
            }],
            calc: (dataList) => {
              return dataList.map((kLineData, i) => {
                return { ma: overlayData[i]?.value ?? null };
              });
            },
          }, false, { id: 'candle_pane' });
        } catch (e) {
          console.error('Error adding compare line:', e);
        }

        // Overlay data'yı ref'e kaydet (tooltip için)
        compareOverlayRef.current[c.id] = overlayData;
      });

      // Crosshair hover event — floating tooltip için
      try {
        chart.subscribeAction('onCrosshairChange', (data) => {
          if (!data || data.dataIndex == null || data.dataIndex < 0) {
            setHoverData(null);
            return;
          }
          const idx = data.dataIndex;
          const mainPoint = normalizedDataRef.current[idx];
          if (!mainPoint) { setHoverData(null); return; }

          const date = new Date(mainPoint.timestamp).toLocaleString('tr-TR', {
            month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit',
          });

          const entries = [
            {
              symbol: mainCoinSymbol?.toUpperCase() ?? coinId,
              value: mainPoint.close,
              color: COMPARE_COLORS[0],
            },
            ...compareCoins.map((c, ci) => {
              const overlay = compareOverlayRef.current[c.id] ?? [];
              const pt = overlay[idx];
              return {
                symbol: c.symbol?.toUpperCase() ?? c.id,
                value: pt?.value ?? null,
                color: COMPARE_COLORS[ci + 1] || '#6b7280',
              };
            }),
          ];

          setHoverData({ date, entries });
        });
      } catch (_) {}

    } else {
      // Tek coin modu: Normal area chart + MA
      chart.setStyles({ candle: { type: 'area' } });

      const klineData = chartData
        .map(d => ({
          timestamp: d.ts,
          open:  d.price ?? 0,
          high:  d.price ?? 0,
          low:   d.price ?? 0,
          close: d.price ?? 0,
          volume: d.volume ?? 0,
          turnover: 0,
        }))
        .filter(d => !isNaN(d.close) && d.close > 0)
        .sort((a, b) => a.timestamp - b.timestamp);

      if (klineData.length === 0) return;

      try {
        chart.setPriceVolumePrecision(
          computeKlinePricePrecision(klineData.map((d) => d.close)),
          computeKlineVolumePrecision(klineData.map((d) => d.volume)),
        );
      } catch (_) { /* klinecharts sürümü */ }

      const isUp = klineData[klineData.length - 1].close >= klineData[0].close;
      const color = isUp ? '#10b981' : '#ef4444';

      chart.setStyles({
        candle: {
          type: 'area',
          area: {
            lineColor: color,
            lineSize: 2,
            value: 'close',
            smooth: true,
            backgroundColor: [
              { offset: 0, color: color + '33' },
              { offset: 1, color: color + '00' },
            ],
          },
        },
      });

      chart.applyNewData(klineData);

      // MA ekle (varsa) - önce eski MA'yı temizle; çizgi rengi buton rengiyle aynı
      chart.removeIndicator('candle_pane', 'MA');
      if (activeMAs && activeMAs.length > 0) {
        chart.createIndicator(
          { name: 'MA', calcParams: activeMAs, styles: { lines: activeMAs.map(p => ({ color: MA_OPTIONS.find(m => m.period === p)?.color ?? '#888', size: 1.5 })) } },
          false,
          { id: 'candle_pane' }
        );
      }

      // Kaydedilmiş çizimleri geri yükle (tekil coin modunda; applyNewData'dan sonra).
      restoreOverlays(klineData);
    }

    return () => { klineDispose(id); };
  }, [chartData, currency, isComparing, compareCoins, compareData, coinId, activeMAs]);

  if (!chartData || chartData.length === 0) {
    return (
      <div className="flex items-center justify-center h-[520px] text-gray-400 text-sm">
        {t('Grafik verisi yüklenemedi.')}
      </div>
    );
  }

  return (
    <div
      className="relative"
      onMouseLeave={() => setHoverData(null)}
    >
      {!isComparing && (
        <DrawingToolbar
          activeTool={activeTool}
          onSelectTool={handleSelectTool}
          onDeleteSelected={handleDeleteSelected}
          onClearAll={handleClearAll}
        />
      )}
      <div id={chartId.current} style={{ width: '100%', height: '520px' }} />

      {/* Hover tooltip — sadece karşılaştırma modunda */}
      {isComparing && hoverData && (
        <div className={`absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rounded-xl px-4 py-3 shadow-xl ring-1 text-xs min-w-[180px] pointer-events-none z-10 backdrop-blur-md ${
          isDark ? 'bg-slate-900/90 ring-white/10' : 'bg-white/95 ring-black/10'
        }`}>
          <p className={`mb-2 font-medium ${isDark ? 'text-slate-300' : 'text-gray-500'}`}>{hoverData.date}</p>
          {hoverData.entries.map(entry => {
            // İç içe ternary'yi açtık (S3358): null → gri, +/- → yeşil/kırmızı.
            let valueColor;
            if (entry.value == null) valueColor = isDark ? 'text-slate-400' : 'text-gray-400';
            else valueColor = entry.value >= 0 ? 'text-emerald-500' : 'text-rose-500';
            return (
              <div key={entry.symbol} className="flex justify-between gap-4 mb-1">
                <span style={{ color: entry.color }} className="font-semibold">{entry.symbol}</span>
                <span className={`font-bold ${valueColor}`}>
                  {entry.value == null ? '-' : `${entry.value >= 0 ? '+' : ''}${entry.value.toFixed(2)}%`}
                </span>
              </div>
            );
          })}
        </div>
      )}

      <p className="text-xs text-gray-400 mt-2">
        {t('Kaynak: CoinGecko ·')} {currency} {t('bazlı')}{isComparing ? t(' · % değişim bazlı karşılaştırma') : ''}
      </p>
    </div>
  );
}
