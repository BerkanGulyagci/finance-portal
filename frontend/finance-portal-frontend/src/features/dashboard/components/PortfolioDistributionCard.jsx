import { useState } from 'react';
import { PieChart as PieIcon, X } from 'lucide-react';
import { ResponsiveContainer, PieChart, Pie, Cell, Tooltip } from 'recharts';
import DashCard, { CardLink } from './DashCard';
import { calculateAllocationByType, CHART_DONUT_COLORS, formatSharePercent } from '../../portfolio';
import { fmtMoney } from '../utils/dashUtils';
import { useTranslation } from '../../../context/LanguageContext';

/**
 * Tüm portföylerin (ya da pano üstüne sürüklenen tek portföyün) birleşik varlık türü
 * dağılımı — kompakt pasta grafiği + lejant. Soldaki "Portföylerim" kartından bir
 * portföyü buraya sürükleyip bırakınca yalnızca onun dağılımı çizilir.
 */
export default function PortfolioDistributionCard({ holdings, focusName, onDropPortfolio, onClear }) {
  const { t } = useTranslation();
  const [dragOver, setDragOver] = useState(false);
  const { rows } = calculateAllocationByType(holdings);
  const data = rows.map(r => ({ ...r, name: t(r.name) }));

  function handleDrop(e) {
    e.preventDefault();
    setDragOver(false);
    const id = e.dataTransfer.getData('application/x-portfolio-id');
    if (id && onDropPortfolio) onDropPortfolio(id);
  }

  const action = focusName ? (
    <button
      onClick={onClear}
      className="inline-flex items-center gap-1 text-[11px] font-semibold text-[#093eaa] bg-[#093eaa]/5 hover:bg-[#093eaa]/10 rounded-full pl-2 pr-1 py-0.5 transition-colors"
      title={t('Tüm portföyler')}
    >
      {focusName} <X className="w-3 h-3" />
    </button>
  ) : (
    <CardLink to="/portfolio">{t('Portföyler')}</CardLink>
  );

  return (
    <DashCard title={t('Portföy Dağılımı')} icon={PieIcon} action={action}>
      <div
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        className={`h-full rounded-xl transition-colors ${dragOver ? 'ring-2 ring-[#093eaa] ring-offset-2 bg-[#093eaa]/[0.03]' : ''}`}
      >
        {data.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center py-8">
            <p className="text-sm text-gray-400">{t('Yeterli veri yok.')}</p>
            <p className="text-[11px] text-gray-300 mt-1">{t('Bir portföyü buraya sürükleyip bırakın')}</p>
          </div>
        ) : (
          <div className="flex items-center gap-4">
            <div className="w-[156px] h-[156px] shrink-0">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={data} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius="100%"
                    minAngle={4} paddingAngle={1} isAnimationActive animationDuration={320} stroke="#fff" strokeWidth={1.5}>
                    {data.map((e, i) => <Cell key={e.type} fill={CHART_DONUT_COLORS[i % CHART_DONUT_COLORS.length]} />)}
                  </Pie>
                  <Tooltip formatter={(v, n) => [`₺${fmtMoney(v)}`, n]} />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <ul className="flex-1 min-w-0 space-y-1">
              {data.map((e, i) => (
                <li key={e.type} className="flex items-center justify-between gap-2 text-xs">
                  <span className="flex items-center gap-1.5 min-w-0">
                    <span className="w-2.5 h-2.5 rounded-sm shrink-0" style={{ backgroundColor: CHART_DONUT_COLORS[i % CHART_DONUT_COLORS.length] }} />
                    <span className="truncate text-gray-700">{e.name}</span>
                  </span>
                  <span className="tabular-nums font-semibold text-gray-500 shrink-0">{formatSharePercent(e.sharePct)}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </DashCard>
  );
}
