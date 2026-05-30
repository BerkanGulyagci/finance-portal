import { COMPARE_COLORS, toNum } from './compareUtils';
import { useTranslation } from '../../../../../context/LanguageContext';

const PIE_COLORS = ['#3b82f6','#10b981','#f59e0b','#ef4444','#8b5cf6','#06b6d4','#f97316','#84cc16'];

function MiniDonut({ items }) {
  if (!items || items.length === 0) return null;
  const total = items.reduce((s, a) => s + (toNum(a.percentage) ?? 0), 0);
  const size = 80, cx = 40, cy = 40, r = 30, innerR = 16;
  let angle = -90;
  const slices = items.map((a, i) => {
    const pct = (toNum(a.percentage) ?? 0) / (total || 1);
    const sweep = pct * 360;
    const startAngle = angle;
    angle += sweep;
    const endAngle = angle;
    const toRad = d => d * Math.PI / 180;
    const x1 = cx + r * Math.cos(toRad(startAngle));
    const y1 = cy + r * Math.sin(toRad(startAngle));
    const x2 = cx + r * Math.cos(toRad(endAngle));
    const y2 = cy + r * Math.sin(toRad(endAngle));
    const xi1 = cx + innerR * Math.cos(toRad(startAngle));
    const yi1 = cy + innerR * Math.sin(toRad(startAngle));
    const xi2 = cx + innerR * Math.cos(toRad(endAngle));
    const yi2 = cy + innerR * Math.sin(toRad(endAngle));
    const large = sweep > 180 ? 1 : 0;
    const d = sweep < 0.5 ? '' :
      `M ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} L ${xi2} ${yi2} A ${innerR} ${innerR} 0 ${large} 0 ${xi1} ${yi1} Z`;
    return { ...a, d, color: PIE_COLORS[i % PIE_COLORS.length], pct: pct * 100 };
  });

  return (
    <svg width={size} height={size} className="shrink-0">
      {slices.map((s, i) => (
        <path key={i} d={s.d} fill={s.color} stroke="white" strokeWidth={1} />
      ))}
    </svg>
  );
}

export default function FundCompareAllocationSection({ detailMap, selectedCodes }) {
  const { t } = useTranslation();
  const hasAny = selectedCodes.some(code => (detailMap[code]?.assetAllocation?.length ?? 0) > 0);
  if (!hasAny) return null;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100">
        <h2 className="font-bold text-gray-900">{t('Varlık Dağılımı')}</h2>
        <p className="text-xs text-gray-400 mt-0.5">{t('Fonların portföy dağılımı karşılaştırması')}</p>
      </div>
      <div className={`grid gap-0 divide-x divide-gray-100 ${selectedCodes.length === 2 ? 'grid-cols-2' : selectedCodes.length === 3 ? 'grid-cols-3' : 'grid-cols-2 lg:grid-cols-4'}`}>
        {selectedCodes.map((code, idx) => {
          const alloc = detailMap[code]?.assetAllocation ?? [];
          const color = COMPARE_COLORS[idx % COMPARE_COLORS.length];
          return (
            <div key={code} className="p-5">
              <div className="flex items-center gap-2 mb-3">
                <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: color }} />
                <span className="font-bold text-sm" style={{ color }}>{code}</span>
              </div>
              {alloc.length === 0 ? (
                <p className="text-xs text-gray-400">{t('Varlık dağılımı verisi bulunamadı.')}</p>
              ) : (
                <div className="flex items-start gap-3">
                  <MiniDonut items={alloc} />
                  <div className="flex-1 space-y-1 min-w-0">
                    {alloc.filter(a => toNum(a.percentage) > 0).map((a, i) => (
                      <div key={i} className="flex items-center justify-between gap-1">
                        <div className="flex items-center gap-1 min-w-0">
                          <div className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: PIE_COLORS[i % PIE_COLORS.length] }} />
                          <span className="text-[10px] text-gray-600 truncate">{a.name}</span>
                        </div>
                        <span className="text-[10px] font-bold text-gray-700 shrink-0">
                          %{toNum(a.percentage)?.toFixed(1)}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
