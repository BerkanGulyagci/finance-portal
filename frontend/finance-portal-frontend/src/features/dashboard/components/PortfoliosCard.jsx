import { Link } from 'react-router-dom';
import { Briefcase, X, RotateCcw } from 'lucide-react';
import DashCard, { CardLink } from './DashCard';
import { fmtMoney, pctClass, num } from '../utils/dashUtils';
import { useTranslation } from '../../../context/LanguageContext';

/**
 * Portföy listesi — her satır panodan gizlenebilir (gerçek portföy silinmez).
 */
export default function PortfoliosCard({ portfolios, onHide, hiddenCount = 0, onReset }) {
  const { t } = useTranslation();

  const action = (
    <div className="flex items-center gap-2 shrink-0">
      {hiddenCount > 0 && (
        <button
          onClick={onReset}
          className="inline-flex items-center gap-1 text-[11px] font-semibold text-gray-400 hover:text-[#093eaa]"
          title={t('Gizlenen portföyleri geri getir')}
        >
          <RotateCcw className="w-3 h-3" /> {hiddenCount} {t('gizli')}
        </button>
      )}
      <CardLink to="/portfolio">{t('Tümü')}</CardLink>
    </div>
  );

  return (
    <DashCard title={t('Portföylerim')} icon={Briefcase} action={action} scroll>
      {portfolios.length === 0 ? (
        <p className="text-sm text-gray-400 py-6 text-center">{t('Görüntülenecek portföy yok.')}</p>
      ) : (
        <ul className="divide-y divide-gray-50">
          {portfolios.map(p => {
            const pnl = num(p.totalProfitLoss);
            const pct = num(p.totalCost) > 0 ? (pnl / num(p.totalCost)) * 100 : 0;
            return (
              <li key={p.id} className="group flex items-center gap-1.5 py-2">
                <Link
                  to={`/portfolio/${p.id}`}
                  draggable
                  onDragStart={(e) => {
                    e.dataTransfer.setData('application/x-portfolio-id', p.id);
                    e.dataTransfer.effectAllowed = 'copy';
                  }}
                  title={t('Dağılım grafiğine sürükleyebilirsiniz')}
                  className="flex items-center justify-between gap-2 flex-1 min-w-0 -mx-1 px-1 py-0.5 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  <span className="min-w-0">
                    <span className="block font-semibold text-gray-900 text-sm truncate">{p.name}</span>
                    <span className={`block text-[11px] font-semibold ${pctClass(pnl)}`}>
                      {pnl >= 0 ? '+' : ''}{fmtMoney(pnl)} ({pnl >= 0 ? '+' : ''}{pct.toFixed(2)}%)
                    </span>
                  </span>
                  <span className="text-sm font-bold text-gray-800 tabular-nums shrink-0">₺{fmtMoney(p.totalMarketValue)}</span>
                </Link>
                <button
                  onClick={() => onHide(p.id)}
                  title={t('Panodan kaldır')}
                  className="opacity-0 group-hover:opacity-100 transition-opacity p-1 rounded-md text-gray-300 hover:text-rose-500 hover:bg-rose-50 shrink-0"
                >
                  <X className="w-4 h-4" />
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </DashCard>
  );
}
