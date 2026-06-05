import { useId } from 'react';

/**
 * Ticker mini grafik — verilen sayı dizisinden 64×28 SVG alan+çizgi sparkline.
 * MarketTicker'dan ayrıştırıldı (saf sunum; veri/% mantığı tickerUtils'te).
 */
export default function Sparkline({ data, color }) {
  const gradId = useId().replace(/:/g, '');
  if (!data || data.length < 2) return null;
  const w = 64, h = 28;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const pts = data.map((v, i) => {
    const x = (i / (data.length - 1)) * w;
    const y = h - ((v - min) / range) * (h - 4) - 2;
    return `${x},${y}`;
  }).join(' ');
  const fillPts = `0,${h} ${pts} ${w},${h}`;
  const fillUrl = `url(#spark-grad-${gradId})`;
  return (
    <svg width={w} height={h} className="shrink-0">
      <defs>
        <linearGradient id={`spark-grad-${gradId}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.3" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polygon points={fillPts} fill={fillUrl} />
      <polyline points={pts} fill="none" stroke={color} strokeWidth="1.5" strokeLinejoin="round" />
    </svg>
  );
}
