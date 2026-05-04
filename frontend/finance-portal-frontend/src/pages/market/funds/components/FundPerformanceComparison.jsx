// Geçmiş performans karşılaştırması — Dolar, Altın, BIST100 vs Fon
const BAR_COLORS = {
  Dolar:   '#f59e0b',
  'Gr. Altın': '#eab308',
  Bist100: '#3b82f6',
};

function getColor(code, isPos) {
  if (BAR_COLORS[code]) return BAR_COLORS[code];
  return isPos ? '#10b981' : '#ef4444';
}

export default function FundPerformanceComparison({ fund }) {
  const items = fund?.performanceComparison ?? [];
  if (!items.length) return null;

  const maxAbs = Math.max(...items.map(i => Math.abs(i.changePercent)), 1);

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-1">{fund?.code} Geçmiş Performans</h2>
      <p className="text-xs text-gray-400 mb-5">Son 1 aylık performans karşılaştırması</p>

      <div className="space-y-4">
        {items.map((item, i) => {
          const pos = item.changePercent >= 0;
          const color = getColor(item.code, pos);
          const barWidth = Math.abs(item.changePercent) / maxAbs * 100;

          return (
            <div key={i}>
              <div className="flex justify-between items-center mb-1.5">
                <span className="text-sm text-gray-600 font-medium">{item.name}</span>
                <span className={`text-sm font-bold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                  {item.changePercentFormated}
                </span>
              </div>
              <div className="h-6 bg-gray-100 rounded-lg overflow-hidden">
                <div
                  className="h-full rounded-lg transition-all duration-500 flex items-center justify-end pr-2"
                  style={{ width: `${Math.max(barWidth, 4)}%`, backgroundColor: color }}
                >
                  {barWidth > 15 && (
                    <span className="text-xs text-white font-bold">{item.changePercentFormated}</span>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
