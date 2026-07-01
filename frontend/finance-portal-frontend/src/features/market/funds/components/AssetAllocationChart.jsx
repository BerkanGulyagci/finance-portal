import { useState } from 'react';
import { useTranslation } from '../../../../context/LanguageContext';

const PIE_COLORS = ['#3b82f6','#10b981','#f59e0b','#ef4444','#8b5cf6','#06b6d4','#f97316','#84cc16'];

function toFloat(v) {
  if (v == null) return null;
  const n = parseFloat(v);
  return isNaN(n) ? null : n;
}

function fmtAllocPct(v) {
  const n = toFloat(v);
  if (n == null || n === 0) return '0';
  if (Math.abs(n) >= 0.005) return n.toFixed(2);
  return String(parseFloat(n.toPrecision(2)));
}

export default function AssetAllocationChart({ assetAllocation }) {
  const { t } = useTranslation();
  const [hovered, setHovered] = useState(null);
  if (!assetAllocation || assetAllocation.length === 0) return null;

  const total = assetAllocation.reduce((s, a) => s + (toFloat(a.percentage) ?? 0), 0);
  const size = 220;
  const cx = size / 2, cy = size / 2, r = 85, innerR = 44;

  let angle = -90;
  const slices = assetAllocation.map((a, i) => {
    const pct = (toFloat(a.percentage) ?? 0) / (total || 1);
    const startAngle = angle;
    const sweep = pct * 360;
    angle += sweep;
    const endAngle = angle;

    const toRad = deg => (deg * Math.PI) / 180;
    const x1 = cx + r * Math.cos(toRad(startAngle));
    const y1 = cy + r * Math.sin(toRad(startAngle));
    const x2 = cx + r * Math.cos(toRad(endAngle));
    const y2 = cy + r * Math.sin(toRad(endAngle));
    const xi1 = cx + innerR * Math.cos(toRad(startAngle));
    const yi1 = cy + innerR * Math.sin(toRad(startAngle));
    const xi2 = cx + innerR * Math.cos(toRad(endAngle));
    const yi2 = cy + innerR * Math.sin(toRad(endAngle));
    const large = sweep > 180 ? 1 : 0;

    const d = sweep < 0.1 ? '' :
      `M ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} L ${xi2} ${yi2} A ${innerR} ${innerR} 0 ${large} 0 ${xi1} ${yi1} Z`;

    return { ...a, d, color: PIE_COLORS[i % PIE_COLORS.length], pct: pct * 100 };
  });

  return (
    <div className="flex flex-col sm:flex-row items-center gap-6">
      {/* Donut pie */}
      <div className="shrink-0">
        <svg width={size} height={size}>
          {slices.map((s, i) => (
            <path key={i} d={s.d}
              fill={s.color}
              opacity={hovered === null || hovered === i ? 0.9 : 0.4}
              stroke="white" strokeWidth={1.5}
              className="cursor-pointer transition-opacity duration-150"
              onMouseEnter={() => setHovered(i)}
              onMouseLeave={() => setHovered(null)} />
          ))}
          {hovered !== null && slices[hovered] ? (
            <>
              <text x={cx} y={cy - 7} textAnchor="middle" fontSize={10} fill="#6b7280">
                {(() => { const nm = t(slices[hovered].name) ?? ''; return nm.length > 16 ? nm.slice(0, 16) + '…' : nm; })()}
              </text>
              <text x={cx} y={cy + 12} textAnchor="middle" fontSize={18} fontWeight="bold" fill="#111827">
                %{slices[hovered].pct.toFixed(1)}
              </text>
            </>
          ) : (
            <text x={cx} y={cy + 5} textAnchor="middle" fontSize={13} fill="#9ca3af">{t('Dağılım')}</text>
          )}
        </svg>
      </div>

      {/* Tablo */}
      <div className="flex-1 w-full">
        <div className="grid grid-cols-[1fr_auto] text-xs text-gray-400 font-semibold pb-1 border-b border-gray-100 mb-1 px-1">
          <span>{t('Varlık')}</span>
          <span>{t('% Oran')}</span>
        </div>
        {slices.map((s, i) => (
          <div key={i}
            className={`grid grid-cols-[1fr_auto] text-sm py-1.5 border-b border-gray-50 px-1 rounded transition-colors cursor-default ${hovered === i ? 'bg-gray-50' : ''}`}
            onMouseEnter={() => setHovered(i)}
            onMouseLeave={() => setHovered(null)}>
            <div className="flex items-center gap-2">
              <div className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: s.color }} />
              <span className="text-gray-700">{t(s.name)}</span>
            </div>
            <span className="text-gray-900 font-semibold">%{fmtAllocPct(s.percentage)}</span>
          </div>
        ))}
        <p className="text-[10px] text-gray-400 mt-2 px-1">{t('Veriler saatlik olarak güncellenmektedir.')}</p>
      </div>
    </div>
  );
}

