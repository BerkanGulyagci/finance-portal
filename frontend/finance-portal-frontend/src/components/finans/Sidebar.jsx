import { useState, useEffect } from 'react';
import { BarChart3, TrendingUp, ArrowUpDown, CalendarDays } from 'lucide-react';
import { getFxTcmb, getEconomicIndicators } from '../../api/marketApi';
import { getBloombergHtNews } from '../../api/newsApi';
import { getIpos } from '../../api/ipoApi';
import { useTranslation } from '../../i18n/LanguageContext';

export function Sidebar() {
  const { t } = useTranslation();
  const [rates, setRates] = useState([]);
  const [selectedCurrency, setSelectedCurrency] = useState('USD');
  const [amount, setAmount] = useState(1000);
  const [mostRead, setMostRead] = useState([]);
  const [ipos, setIpos] = useState([]);
  const [indicators, setIndicators] = useState({ policyRate: '...', inflation: '...' });

  useEffect(() => {
    getFxTcmb().then(fx => setRates(fx?.rates ?? [])).catch(() => {});
    getBloombergHtNews().then(n => setMostRead(n.slice(0, 3))).catch(() => {});
    getIpos().then(setIpos).catch(() => {});
    getEconomicIndicators().then(setIndicators).catch(() => {});
  }, []);

  const selectedRate = rates.find(r => r.symbol === selectedCurrency);
  const tryAmount = selectedRate ? (amount * selectedRate.sell).toLocaleString('tr-TR', { maximumFractionDigits: 2 }) : '...';

  return (
    <aside className="space-y-8">
      {/* Most Read */}
      <div>
        <h3 className="font-bold text-lg mb-4 flex items-center gap-2">
          <TrendingUp className="w-5 h-5 text-[#093eaa]" /> {t('En Son Haberler')}
        </h3>
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm">
          {mostRead.length === 0 && <div className="p-4 text-sm text-gray-400">{t('Yükleniyor...')}</div>}
          {mostRead.map((item, i) => (
            <div key={i} className={`p-4 flex gap-4 cursor-pointer hover:bg-gray-50 transition-colors ${i < mostRead.length - 1 ? 'border-b border-gray-100' : ''}`}>
              <span className="text-2xl font-black text-gray-200">{String(i + 1).padStart(2, '0')}</span>
              <p className="text-sm font-semibold leading-tight hover:text-[#093eaa] transition-colors">
                {item.url
                  ? <a href={item.url} target="_blank" rel="noopener noreferrer">{item.title}</a>
                  : item.title}
              </p>
            </div>
          ))}
        </div>
      </div>

      {/* Currency Converter */}
      <div className="bg-slate-900 text-white rounded-xl p-6 shadow-xl">
        <h3 className="font-bold mb-4 flex items-center gap-2">
          <ArrowUpDown className="w-5 h-5 text-[#093eaa]" /> {t('Döviz Çevirici')}
        </h3>
        <div className="space-y-3">
          {/* From */}
          <div className="flex bg-white/10 rounded-lg p-3 items-center gap-2">
            <input
              type="number"
              value={amount}
              onChange={e => setAmount(Number(e.target.value))}
              className="bg-transparent border-none text-white focus:outline-none flex-1 text-sm min-w-0"
            />
            <select
              value={selectedCurrency}
              onChange={e => setSelectedCurrency(e.target.value)}
              className="bg-white/20 border-none text-white text-xs font-bold rounded-md px-2 py-1 focus:outline-none cursor-pointer"
            >
              {rates.map(r => (
                <option key={r.symbol} value={r.symbol} className="bg-slate-800 text-white">
                  {r.symbol}
                </option>
              ))}
            </select>
          </div>

          <div className="flex justify-center">
            <ArrowUpDown className="w-5 h-5 text-[#093eaa]" />
          </div>

          {/* To — always TRY */}
          <div className="flex bg-white/10 rounded-lg p-3 items-center justify-between">
            <span className="text-sm text-white font-semibold">{tryAmount}</span>
            <span className="text-xs font-bold">TRY</span>
          </div>
        </div>

        {selectedRate && (
          <p className="text-xs text-white/60 mt-4 text-center">
            1 {selectedCurrency} = {selectedRate.sell.toLocaleString('tr-TR', { minimumFractionDigits: 2 })} TRY
          </p>
        )}
      </div>

      {/* Market Summary */}
      <div>
        <h3 className="font-bold text-lg mb-4 flex items-center gap-2">
          <BarChart3 className="w-5 h-5 text-[#093eaa]" /> {t('Piyasa Özeti')}
        </h3>
        <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-sm space-y-3">
          <div className="flex justify-between items-center">
            <span className="text-xs font-semibold text-gray-600">{t('TCMB Faiz Oranı')}</span>
            <span className="text-xs font-bold text-[#093eaa]">%{indicators.policyRate}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-xs font-semibold text-gray-600">{t('Enflasyon (TÜFE)')}</span>
            <span className="text-xs font-bold text-rose-500">%{indicators.inflation}</span>
          </div>
          {['USD', 'EUR', 'GBP'].map(sym => {
            const r = rates.find(x => x.symbol === sym);
            return r ? (
              <div key={sym} className="flex justify-between items-center">
                <span className="text-xs font-semibold text-gray-600">{sym}/TRY</span>
                <span className="text-xs font-bold text-gray-800">
                  {r.sell.toLocaleString('tr-TR', { minimumFractionDigits: 3 })}
                </span>
              </div>
            ) : null;
          })}
        </div>
      </div>

      {/* IPO Calendar */}
      <div>
        <h3 className="font-bold text-lg mb-4 flex items-center gap-2">
          <CalendarDays className="w-5 h-5 text-[#093eaa]" /> {t('Halka Arz Takvimi')}
        </h3>
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm">
          {ipos.length === 0 && (
            <div className="p-4 text-sm text-gray-400">{t('Yükleniyor...')}</div>
          )}
          {ipos.slice(0, 8).map((ipo, i) => (
            <div key={i} className={`p-3 flex items-start justify-between gap-2 ${i < Math.min(ipos.length, 8) - 1 ? 'border-b border-gray-100' : ''}`}>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-0.5">
                  <span className="text-xs font-black text-[#093eaa] shrink-0">{ipo.ticker}</span>
                  <span className="text-xs text-gray-700 font-semibold truncate">{ipo.name}</span>
                </div>
                <span className="text-[10px] text-gray-400">{ipo.date}</span>
              </div>
              {ipo.url && (
                <a href={ipo.url} target="_blank" rel="noopener noreferrer"
                  className="text-[10px] text-[#093eaa] hover:underline shrink-0">
                  {t('Detay')}
                </a>
              )}
            </div>
          ))}
          {ipos.length > 0 && (
            <a href="https://halkarz.com" target="_blank" rel="noopener noreferrer"
              className="block text-center text-xs text-[#093eaa] font-semibold py-3 hover:bg-gray-50 transition-colors border-t border-gray-100">
              {t('Tümünü Gör →')}
            </a>
          )}
        </div>
      </div>
    </aside>
  );
}
