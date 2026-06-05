import { useMemo } from 'react';
import { fmt, getSpotPrice } from '../utils/goldConstants';
import { useTranslation } from '../../../../context/LanguageContext';

/**
 * Seçili dönemin history noktalarından istatistik hesaplar.
 * displayPoints: GoldPage'de buildDisplayPoints() ile üretilen, tab'a göre dönüştürülmüş noktalar.
 */
export default function GoldPriceStats({ spot, activeTab, historyPoints }) {
  const { t } = useTranslation();

  const isUsd = activeTab?.currency === 'USD';
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

  if (!spot || !activeTab) return null;

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
    // Hacim/Miktar/İşlem Sayısı YALNIZCA Ons Altın'da (USD) gösterilir.
    // BIST işlem hacmi verisi yalnız has-altın ONS bazında anlamlıdır; gram ve teorik
    // ürünlerde (çeyrek/yarım/cumhuriyet/ziynet/bilezik) bunlar fiyat türevidir, kendi
    // işlem hacimleri yoktur — history'de TL hacmi de bulunmadığından gösterilmez.
    ...(isUsd ? [
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
    ] : []),
  ];

  // Boş ('-') alanlar görünmesin; ilk öğe (Güncel Fiyat) highlight; VİOP-stili kutu-grid.
  const visible = rows.filter(r => r.value != null && r.value !== '-' && String(r.value).trim() !== '');
  if (!visible.length) return null;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-4 sm:p-5 h-full">
      <h2 className="font-bold text-gray-900 mb-3 text-sm sm:text-base">{t('Fiyat Bilgileri')}</h2>
      <div className="grid grid-cols-2 gap-2.5">
        {visible.map((row, i) => {
          const highlight = i === 0;
          const color = row.colored
            ? (row.positive ? 'text-emerald-600' : 'text-rose-600')
            : (highlight ? 'text-[#093eaa]' : 'text-gray-900');
          return (
            <div
              key={row.label}
              className={`rounded-xl p-3 text-center ${
                highlight ? 'bg-[#093eaa]/[0.06] border border-[#093eaa]/20' : 'bg-gray-50 border border-transparent'
              }`}
            >
              <p className={`text-[10px] font-semibold mb-1 ${highlight ? 'text-[#093eaa]' : 'text-gray-500'}`}>
                {t(row.label)}
              </p>
              <p className={`text-sm font-bold ${color}`}>{row.value}</p>
            </div>
          );
        })}
      </div>
    </div>
  );
}
