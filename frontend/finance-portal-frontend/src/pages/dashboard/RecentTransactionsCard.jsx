import { Link } from 'react-router-dom';
import { ArrowDownLeft, ArrowUpRight, History } from 'lucide-react';
import DashCard from './DashCard';
import { fmtMoney, num, ASSET_LABEL } from './dashUtils';
import { getWatchlistDetailPath } from '../portfolio/constants/watchlistMarketRoutes';
import { useTranslation } from '../../i18n/LanguageContext';

function fmtDate(s) {
  if (!s) return '';
  const d = new Date(s);
  return Number.isNaN(d.getTime())
    ? ''
    : d.toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' });
}

/**
 * Tüm portföylerdeki en son alış/satış işlemleri (alt alan).
 */
export default function RecentTransactionsCard({ transactions }) {
  const { t } = useTranslation();

  return (
    <DashCard title={t('Son İşlemler')} icon={History} scroll>
      {transactions.length === 0 ? (
        <p className="text-sm text-gray-400 py-6 text-center">{t('Henüz işlem yapmadınız.')}</p>
      ) : (
        <ul className="divide-y divide-gray-50">
          {transactions.map(tx => {
            const buy = tx.transactionType === 'BUY';
            const total = num(tx.quantity) * num(tx.price);
            const path = getWatchlistDetailPath?.(tx.assetType, tx.symbol);
            const rowInner = (
              <>
                <span className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 ${buy ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'}`}>
                  {buy ? <ArrowDownLeft className="w-4 h-4" /> : <ArrowUpRight className="w-4 h-4" />}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-2">
                    <span className="font-semibold text-gray-900 text-sm truncate">{tx.symbol}</span>
                    <span className="text-[10px] font-semibold text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded shrink-0">
                      {t(ASSET_LABEL[tx.assetType] ?? tx.assetType)}
                    </span>
                  </span>
                  <span className="text-[11px] text-gray-400 truncate">
                    <span className={buy ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>
                      {buy ? t('Alış') : t('Satış')}
                    </span>
                    {tx.portfolioName ? ` · ${tx.portfolioName}` : ''} · {fmtDate(tx.transactionDate)}
                  </span>
                </span>
                <span className="text-right shrink-0">
                  <span className="block text-sm font-bold text-gray-800 tabular-nums">₺{fmtMoney(total)}</span>
                  <span className="block text-[11px] text-gray-400 tabular-nums">
                    {num(tx.quantity).toLocaleString('tr-TR', { maximumFractionDigits: 4 })} × ₺{fmtMoney(tx.price)}
                  </span>
                </span>
              </>
            );
            return (
              <li key={tx.id}>
                {path ? (
                  <Link to={path} className="flex items-center gap-2.5 py-2 -mx-1 px-1 rounded-lg hover:bg-gray-50 transition-colors">
                    {rowInner}
                  </Link>
                ) : (
                  <div className="flex items-center gap-2.5 py-2">{rowInner}</div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </DashCard>
  );
}
