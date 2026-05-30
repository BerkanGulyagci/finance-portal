import { Bitcoin } from 'lucide-react';
import DashCard from './DashCard';
import { fmtMoney, fmtPct, pctClass, num } from '../utils/dashUtils';
import { useTranslation } from '../../../i18n/LanguageContext';

/**
 * Kripto piyasası — kompakt ilk N coin (ikon, sembol, fiyat, 24s %).
 */
export default function CryptoMarketCard({ cryptos }) {
  const { t } = useTranslation();

  return (
    <DashCard title={t('Kripto Piyasası')} icon={Bitcoin} to="/market/crypto" toLabel={t('Tümü →')} accent="#f59e0b">
      {cryptos.length === 0 ? (
        <p className="text-sm text-gray-400 py-6 text-center">{t('Yükleniyor...')}</p>
      ) : (
        <div className="divide-y divide-gray-50">
          {cryptos.map(c => {
            const pct = num(c.priceChangePercentage24h);
            return (
              <div key={c.id ?? c.symbol} className="flex items-center justify-between gap-2 py-2">
                <span className="flex items-center gap-2 min-w-0">
                  {c.image
                    ? <img src={c.image} alt="" className="w-6 h-6 rounded-full shrink-0" />
                    : <span className="w-6 h-6 rounded-full bg-gray-100 shrink-0" />}
                  <span className="text-sm font-semibold text-gray-900 truncate">{c.symbol?.toUpperCase()}</span>
                </span>
                <span className="text-right shrink-0">
                  <span className="block text-sm font-bold text-gray-800 tabular-nums">₺{fmtMoney(c.currentPrice)}</span>
                  <span className={`block text-[11px] font-semibold tabular-nums ${pctClass(pct)}`}>{fmtPct(pct)}</span>
                </span>
              </div>
            );
          })}
        </div>
      )}
    </DashCard>
  );
}
