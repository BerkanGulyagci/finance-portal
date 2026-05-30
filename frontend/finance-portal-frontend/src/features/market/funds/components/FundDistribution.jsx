import { useTranslation } from '../../../../context/LanguageContext';

// Renk paleti — fon içeriği dağılımı için
const COLORS = [
  '#3b82f6', '#6366f1', '#8b5cf6', '#a78bfa',
  '#c4b5fd', '#ddd6fe', '#ede9fe', '#f5f3ff',
];

export default function FundDistribution({ fund }) {
  const { t } = useTranslation();
  const items = fund?.distribution ?? [];
  if (!items.length) return null;

  const total = items.reduce((s, i) => s + i.rate, 0);

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-1">{fund?.code} {t('Fon İçeriği')}</h2>
      {fund?.updateDate && (
        <p className="text-xs text-gray-400 mb-4">
          {new Date(fund.updateDate).toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit', year: 'numeric' })}
        </p>
      )}

      {/* Stacked bar */}
      <div className="flex h-4 rounded-full overflow-hidden mb-5 gap-px">
        {items.map((item, i) => (
          <div
            key={i}
            style={{ width: `${(item.rate / total) * 100}%`, backgroundColor: COLORS[i % COLORS.length] }}
            title={`${item.title}: ${item.rateText}`}
          />
        ))}
      </div>

      {/* Legend */}
      <div className="space-y-2.5">
        {items.map((item, i) => (
          <div key={i} className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <div
                className="w-3 h-3 rounded-sm flex-shrink-0"
                style={{ backgroundColor: COLORS[i % COLORS.length] }}
              />
              <span className="text-sm text-gray-700">{item.title}</span>
            </div>
            <span className="text-sm font-bold text-gray-900">{item.rateText}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
