import { TrendingUp } from 'lucide-react';
import { COLORS, calcMetrics } from '../utils/stockCompareUtils';
import { STOCK_CHART_RANGES } from '../utils/stockChartRanges';

/**
 * Hisse-hisse kıyasında performans metrikleri tablosu (getiri, BIST100'e göre, drawdown,
 * volatilite, RSI, MA durumları, trend...). SAF sunum: rawPrices/bist100Prices'tan metrikleri
 * hesaplayıp gösterir — state/handler yok.
 *
 * MA NOTU: MA7/MA25 son N VERİ NOKTASInın ortalamasıdır. 1A/3A/6A modunda veri SAATLİK
 * (interval '1h') → MA7 ≈ son 7 saat, "7 gün" DEĞİL. Etiket buna göre dürüstçe ayarlanır:
 * saatlik modda "MA7 (kısa)" + tooltip; günlük/haftalık/aylık modda gerçek MA7/MA25.
 */
export default function PerformanceMetricsTable({ selectedSymbols, rawPrices, bist100Prices, rangeIdx = 5, t }) {
  const bist100M = calcMetrics(bist100Prices);
  // Seçili aralığın bar birimi (saatlik mi günlük mü) — MA etiketini dürüstleştirmek için.
  const interval = STOCK_CHART_RANGES[rangeIdx]?.interval ?? '1d';
  const barUnit = interval === '1h' ? t('saat') : interval === '1wk' ? t('hafta') : interval === '1mo' ? t('ay') : t('gün');
  const maLabel = (n) => `MA${n} · ${n} ${barUnit}`;
  const metrics = {};
  selectedSymbols.forEach(sym => { metrics[sym] = calcMetrics(rawPrices[sym]); });
  const fmt2 = v => v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const fmt4 = v => v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 4 });

  const PERF_ROWS = [
    { label: 'Başlangıç Fiyatı', render: m => m ? `₺${fmt4(m.startPrice)}` : '-' },
    { label: 'Bitiş Fiyatı',     render: m => m ? `₺${fmt4(m.endPrice)}` : '-' },
    { label: 'Dönem Max',        render: m => m ? `₺${fmt4(m.maxPrice)}` : '-', green: true },
    { label: 'Dönem Min',        render: m => m ? `₺${fmt4(m.minPrice)}` : '-', red: true },
    {
      label: 'Dönem Getirisi',
      render: m => {
        if (!m) return '-';
        const pos = m.periodReturn >= 0;
        return <span className={pos ? 'text-emerald-600 font-bold' : 'text-rose-600 font-bold'}>{pos ? '+' : ''}{fmt2(m.periodReturn)}%</span>;
      },
    },
    {
      label: 'BIST100\'e Göre',
      render: m => {
        if (!m || !bist100M) return '-';
        // Genel finans konvansiyonu: relatif (çarpımsal) getiri = (1+hisse)/(1+endeks) − 1.
        // "Parayı endeks yerine bu hisseye koysaydın % kaç daha iyi/kötü olurdun."
        const denom = 1 + bist100M.periodReturn / 100;
        if (denom <= 0) return '-';
        const rel = ((1 + m.periodReturn / 100) / denom - 1) * 100;
        const pos = rel >= 0;
        return <span className={pos ? 'text-emerald-600 font-bold' : 'text-rose-600 font-bold'}>{pos ? '+' : ''}{fmt2(rel)}%</span>;
      },
    },
    { label: 'Max Drawdown',     render: m => m ? <span className="text-rose-600 font-semibold">{fmt2(m.drawdown)}%</span> : '-' },
    { label: 'Volatilite',       render: m => m ? `${fmt2(m.volatility)}%` : '-' },
    {
      label: 'RSI (14)',
      render: m => {
        if (!m) return '-';
        return <span className="text-gray-700">{fmt2(m.rsi)}</span>;
      },
    },
    { label: 'Risk/Getiri', render: m => m?.riskAdjusted != null ? fmt2(m.riskAdjusted) : '-' },
    // ── Teknik Analiz ──
    {
      label: maLabel(7),
      render: m => {
        if (!m?.ma7 || !m.lastPrice) return '-';
        const above = m.lastPrice >= m.ma7;
        return <span className={above ? 'text-emerald-600' : 'text-rose-600'}>{above ? t('▲ Üstünde') : t('▼ Altında')} <span className="text-gray-400 text-xs">(₺{fmt4(m.ma7)})</span></span>;
      },
    },
    {
      label: maLabel(25),
      render: m => {
        if (!m?.ma25 || !m.lastPrice) return '-';
        const above = m.lastPrice >= m.ma25;
        return <span className={above ? 'text-emerald-600' : 'text-rose-600'}>{above ? t('▲ Üstünde') : t('▼ Altında')} <span className="text-gray-400 text-xs">(₺{fmt4(m.ma25)})</span></span>;
      },
    },
    // NOT: "Trend" ve "RSI Uyarısı" satırları kaldırıldı.
    //  - Trend: compare MA7/25 + dönem getirisi (kısa-vade momentum) ile detay sayfası
    //    MA20/50 + 52 hafta (uzun-vade) farklı şey ölçüyordu → aynı hisse iki yerde zıt
    //    görünüp güveni zedeliyordu. Net sayısal metrikler (getiri/drawdown/volatilite/
    //    MA üstünde-altında durumu) yeterli ve tutarlı; sübjektif tek-kelime trend çıkarıldı.
    //  - RSI Uyarısı: çoğu zaman 30-70 arası → boş ("—"); MA durumu + RSI(14) zaten var.
  ];

  return (
    <div className="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between flex-wrap gap-2">
        <div>
          <h2 className="font-bold text-gray-900">{t('Performans Metrikleri')}</h2>
          <p className="text-xs text-gray-400 mt-0.5">
            {t('Seçili dönem · BIST100 getirisi:')} {bist100M ? `${bist100M.periodReturn >= 0 ? '+' : ''}${fmt2(bist100M.periodReturn)}%` : '-'}
          </p>
        </div>
        {/* En iyi performer */}
        {(() => {
          const best = selectedSymbols
            .map(s => ({ s, ret: metrics[s]?.periodReturn ?? -Infinity }))
            .sort((a, b) => b.ret - a.ret)[0];
          return best && best.ret !== -Infinity ? (
            <div className="flex items-center gap-1.5 text-sm">
              <TrendingUp className="w-4 h-4 text-emerald-500" />
              <span className="text-gray-500">{t('En iyi:')}</span>
              <span className="font-bold text-emerald-600">{best.s.replace('.IS', '').toUpperCase()} ({best.ret >= 0 ? '+' : ''}{fmt2(best.ret)}%)</span>
            </div>
          ) : null;
        })()}
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[560px]">
          <thead className="bg-gray-50">
            <tr>
              <th className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 w-44">{t('Metrik')}</th>
              {selectedSymbols.map((sym, idx) => (
                <th key={sym} className="text-right px-5 py-3 text-xs font-bold uppercase tracking-wider border-b border-gray-200 whitespace-nowrap" style={{ color: COLORS[idx % COLORS.length] }}>
                  <div className="flex items-center justify-end gap-1.5">
                    <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: COLORS[idx % COLORS.length] }} />
                    {sym.replace('.IS', '').toUpperCase()}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {PERF_ROWS.map((row, i) => (
              <tr key={row.label} className={`border-t border-gray-100 hover:bg-gray-50 ${i % 2 === 0 ? '' : 'bg-gray-50/40'}`}>
                <td className="px-5 py-3 text-sm text-gray-500 font-medium whitespace-nowrap">{t(row.label)}</td>
                {selectedSymbols.map(sym => (
                  <td key={sym} className="px-5 py-3 text-sm text-right font-mono text-gray-800">
                    {row.render(metrics[sym])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
