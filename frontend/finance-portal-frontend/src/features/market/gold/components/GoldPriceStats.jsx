import { useMemo } from 'react';
import { fmt, getSpotPrice } from '../utils/goldConstants';
import { useTranslation } from '../../../../context/LanguageContext';

/**
 * Seçili dönemin history noktalarından istatistik hesaplar.
 * displayPoints: GoldPage'de buildDisplayPoints() ile üretilen, tab'a göre dönüştürülmüş noktalar.
 */
export default function GoldPriceStats({ spot, activeTab, historyPoints }) {
  const { t } = useTranslation();
  if (!spot || !activeTab) return null;

  const isUsd = activeTab.currency === 'USD';
  const sym   = isUsd ? '$' : '₺';

  // ── Dönem istatistikleri — history noktalarından hesapla ──────────────────
  const stats = useMemo(() => {
    if (!historyPoints?.length) return null;

    const closes = historyPoints
      .map(p => parseFloat(p.displayClose ?? p.close))
      .filter(v => !isNaN(v) && v > 0);

    const highs = historyPoints
      .map(p => parseFloat(p.displayHigh ?? p.high ?? p.displayClose ?? p.close))
      .filter(v => !isNaN(v) && v > 0);

    const lows = historyPoints
      .map(p => parseFloat(p.displayLow ?? p.low ?? p.displayClose ?? p.close))
      .filter(v => !isNaN(v) && v > 0);

    const was = historyPoints
      .map(p => {
        // USD modunda weightedAverageUsdOns, TRY modunda weightedAverage (gram)
        const raw = isUsd
          ? (p.weightedAverageUsdOns ?? p.displayClose ?? p.close)
          : (p.displayWa ?? p.weightedAverage ?? p.displayClose ?? p.close);
        return parseFloat(raw);
      })
      .filter(v => !isNaN(v) && v > 0);

    const volumes = historyPoints
      .map(p => parseFloat(p.volumeUsd ?? p.volume ?? 0))
      .filter(v => !isNaN(v) && v > 0);

    const qtys = historyPoints
      .map(p => parseFloat(p.quantityKg ?? 0))
      .filter(v => !isNaN(v) && v > 0);

    const cnts = historyPoints
      .map(p => parseInt(p.transactionCount ?? 0, 10))
      .filter(v => !isNaN(v) && v > 0);

    return {
      periodHigh:   highs.length   ? Math.max(...highs)   : null,
      periodLow:    lows.length    ? Math.min(...lows)     : null,
      lastClose:    closes.length  ? closes[closes.length - 1] : null,
      periodWa:     was.length     ? was.reduce((a, b) => a + b, 0) / was.length : null,
      totalVolume:  volumes.length ? volumes.reduce((a, b) => a + b, 0) : null,
      totalQty:     qtys.length    ? qtys.reduce((a, b) => a + b, 0)    : null,
      totalTxCount: cnts.length    ? cnts.reduce((a, b) => a + b, 0)    : null,
      firstClose:   closes.length  ? closes[0] : null,
    };
  }, [historyPoints, isUsd]);

  // Güncel fiyat — spot'tan
  const currentPrice = getSpotPrice(spot, activeTab);

  // Dönem değişimi
  const periodChange = stats?.firstClose && stats?.lastClose
    ? ((stats.lastClose - stats.firstClose) / stats.firstClose) * 100
    : null;

  const rows = [
    {
      label: 'Güncel Fiyat',
      value: currentPrice != null ? `${sym}${fmt(currentPrice)}` : '-',
    },
    {
      label: 'En Yüksek',
      value: stats?.periodHigh != null ? `${sym}${fmt(stats.periodHigh)}` : '-',
    },
    {
      label: 'En Düşük',
      value: stats?.periodLow != null ? `${sym}${fmt(stats.periodLow)}` : '-',
    },
    {
      label: 'Kapanış',
      value: stats?.lastClose != null ? `${sym}${fmt(stats.lastClose)}` : '-',
    },
    {
      label: 'Ağırlıklı Ortalama',
      value: stats?.periodWa != null ? `${sym}${fmt(stats.periodWa)}` : '-',
    },
    {
      label: 'Dönem Değişimi',
      value: periodChange != null
        ? `${periodChange >= 0 ? '+' : ''}${fmt(periodChange)}%`
        : '-',
      colored: periodChange != null,
      positive: periodChange != null && periodChange >= 0,
    },
    {
      label: 'İşlem Hacmi',
      value: stats?.totalVolume != null
        ? `${sym}${fmt(stats.totalVolume, 0)}`
        : '-',
    },
    {
      label: 'Miktar (Kg)',
      value: stats?.totalQty != null
        ? `${fmt(stats.totalQty, 3)} Kg`
        : '-',
    },
    {
      label: 'İşlem Sayısı',
      value: stats?.totalTxCount != null
        ? stats.totalTxCount.toLocaleString('tr-TR')
        : '-',
    },
  ];

  return (
    <div className="grid grid-cols-2 gap-x-8 gap-y-0 border-t border-gray-100 pt-4 mb-6">
      {rows.map(row => (
        <div
          key={row.label}
          className="flex justify-between items-center py-2 border-b border-gray-50"
        >
          <span className="text-xs font-bold text-gray-500 tracking-wider">{t(row.label)}</span>
          <span
            className={`text-sm font-semibold ${
              row.colored
                ? row.positive ? 'text-emerald-600' : 'text-rose-600'
                : 'text-gray-900'
            }`}
          >
            {row.value}
          </span>
        </div>
      ))}
    </div>
  );
}
