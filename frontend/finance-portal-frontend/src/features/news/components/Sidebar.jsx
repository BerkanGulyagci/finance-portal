import { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { BarChart3, TrendingUp, ArrowUpDown, CalendarDays, ChevronDown } from 'lucide-react';
import { getFxTcmb, getEconomicIndicators, getEconomy } from '../../../api/marketApi';
import { getIpos } from '../../../api/ipoApi';
import StockLogo from '../../dashboard/components/StockLogo';
import MarketMoversCard from '../../dashboard/components/MarketMoversCard';
import VolumeLeadersCard from '../../dashboard/components/VolumeLeadersCard';
import { useTranslation } from '../../../context/LanguageContext';

/** Site tasarımına uygun, tarayıcı select'i yerine özel açılır menü. */
function CurrencyDropdown({ value, options, onChange }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  useEffect(() => {
    function onDoc(e) {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    }
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);
  return (
    <div className="relative shrink-0" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        className="flex items-center gap-1 bg-white border border-gray-200 rounded-md px-2.5 py-1.5 text-sm font-bold text-gray-800 hover:bg-gray-50"
      >
        {value} <ChevronDown className={`w-3.5 h-3.5 text-gray-400 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="absolute right-0 mt-1 w-24 max-h-56 overflow-auto bg-white border border-gray-200 rounded-md shadow-lg z-30">
          {options.map(o => (
            <button
              key={o}
              type="button"
              onClick={() => { onChange(o); setOpen(false); }}
              className={`block w-full text-left px-3 py-1.5 text-sm hover:bg-gray-50 ${o === value ? 'text-[#093eaa] font-bold' : 'text-gray-700'}`}
            >
              {o}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export function Sidebar() {
  const { t, language } = useTranslation();
  const [rates, setRates] = useState([]);
  const [selectedCurrency, setSelectedCurrency] = useState('USD');
  const [amount, setAmount] = useState(1000);
  const [swapped, setSwapped] = useState(false);
  const [ipos, setIpos] = useState([]);
  const [indicators, setIndicators] = useState({ policyRate: '...', inflation: '...' });
  const [fxChange, setFxChange] = useState({}); // { USD: %, EUR: % } — günlük değişim (renk için)

  useEffect(() => {
    getFxTcmb().then(fx => setRates(fx?.rates ?? [])).catch(() => {});
    getIpos().then(setIpos).catch(() => {});
    getEconomicIndicators().then(setIndicators).catch(() => {});
    getEconomy().then(eco => {
      const find = (k) => {
        for (const g of eco?.groups ?? []) {
          const f = (g.indicators ?? []).find(i => i.key === k);
          if (f) return f;
        }
        return null;
      };
      setFxChange({ USD: find('usdTry')?.changePercent, EUR: find('eurTry')?.changePercent });
    }).catch(() => {});
  }, []);

  const selectedRate = rates.find(r => r.symbol === selectedCurrency);
  // swapped=false: girilen tutar yabancı para → TRY; swapped=true: girilen tutar TRY → yabancı para
  const result = selectedRate ? (swapped ? amount / selectedRate.sell : amount * selectedRate.sell) : null;
  const resultStr = result != null
    ? result.toLocaleString('tr-TR', { maximumFractionDigits: swapped ? 4 : 2 })
    : '...';

  function handleSwap() {
    if (result != null && Number.isFinite(result)) setAmount(Number(result.toFixed(2)));
    setSwapped(s => !s);
  }

  return (
    <aside className="space-y-8">
      {/* Piyasanın Hareketlileri (dashboard kartı — kendi verisini çeker) */}
      <div className="h-[380px]">
        <MarketMoversCard />
      </div>

      {/* Hacim Liderleri */}
      <div className="h-[360px]">
        <VolumeLeadersCard />
      </div>

      {/* Döviz Çevirici — site tasarımına uygun açık tema */}
      <div className="bg-white rounded-md border border-gray-200 p-5 shadow-sm">
        <h3 className="font-bold mb-4 flex items-center gap-2 text-gray-800">
          <ArrowUpDown className="w-5 h-5 text-[#093eaa]" /> {t('Döviz Çevirici')}
        </h3>
        {/* Üst: girdi */}
        <div className="flex items-center gap-2 bg-gray-50 border border-gray-200 rounded-md p-2.5">
          <input
            type="text"
            inputMode="decimal"
            value={amount.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}
            onChange={e => {
              let raw = e.target.value.replace(/[^\d,]/g, '');
              const firstComma = raw.indexOf(',');
              if (firstComma !== -1) {
                raw = raw.slice(0, firstComma + 1) + raw.slice(firstComma + 1).replace(/,/g, '');
              }
              const n = parseFloat(raw.replace(',', '.'));
              setAmount(Number.isFinite(n) ? n : 0);
            }}
            className="bg-transparent border-none text-gray-900 font-semibold focus:outline-none flex-1 text-sm min-w-0"
          />
          {swapped ? (
            <span className="flex items-center bg-white border border-gray-200 rounded-md px-2.5 py-1.5 text-sm font-bold text-gray-800 shrink-0">TRY</span>
          ) : (
            <CurrencyDropdown value={selectedCurrency} options={rates.map(r => r.symbol)} onChange={setSelectedCurrency} />
          )}
        </div>

        {/* Yön değiştir */}
        <div className="flex justify-center my-2">
          <button
            type="button"
            onClick={handleSwap}
            title={t('Yönü değiştir')}
            aria-label={t('Yönü değiştir')}
            className="flex items-center justify-center w-8 h-8 rounded-full border border-gray-200 bg-white text-[#093eaa] hover:bg-gray-50 active:scale-95 transition"
          >
            <ArrowUpDown className="w-4 h-4" />
          </button>
        </div>

        {/* Alt: sonuç */}
        <div className="flex items-center gap-2 bg-gray-50 border border-gray-200 rounded-md p-2.5">
          <span className="flex-1 text-sm font-bold text-gray-900 min-w-0 truncate">{resultStr}</span>
          {swapped ? (
            <CurrencyDropdown value={selectedCurrency} options={rates.map(r => r.symbol)} onChange={setSelectedCurrency} />
          ) : (
            <span className="flex items-center bg-white border border-gray-200 rounded-md px-2.5 py-1.5 text-sm font-bold text-gray-800 shrink-0">TRY</span>
          )}
        </div>

        {selectedRate && (
          <p className="text-xs text-gray-400 mt-3 text-center">
            1 {selectedCurrency} = {selectedRate.sell.toLocaleString('tr-TR', { minimumFractionDigits: 2 })} TRY
          </p>
        )}
      </div>

      {/* Piyasa Özeti */}
      <div>
        <h3 className="font-bold text-lg mb-4 flex items-center gap-2">
          <BarChart3 className="w-5 h-5 text-[#093eaa]" /> {t('Piyasa Özeti')}
        </h3>
        <div className="bg-white rounded-md border border-gray-200 p-4 shadow-sm space-y-3">
          <div className="flex justify-between items-center">
            <span className="text-xs font-semibold text-gray-600">{t('TCMB Faiz Oranı')}</span>
            <span className="text-xs font-bold text-[#093eaa]">%{indicators.policyRate}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-xs font-semibold text-gray-600">{t('Enflasyon (TÜFE)')}</span>
            <span className="text-xs font-bold text-rose-500">%{indicators.inflation}</span>
          </div>
          {indicators.ppi != null && indicators.ppi !== '' && (
            <div className="flex justify-between items-center">
              <span className="text-xs font-semibold text-gray-600">{t('Üretici Enf. (ÜFE)')}</span>
              <span className="text-xs font-bold text-rose-500">%{indicators.ppi}</span>
            </div>
          )}
          {indicators.depositRate != null && indicators.depositRate !== '' && (
            <div className="flex justify-between items-center">
              <span className="text-xs font-semibold text-gray-600">{t('Mevduat Faizi')}</span>
              <span className="text-xs font-bold text-[#093eaa]">%{indicators.depositRate}</span>
            </div>
          )}
          {['USD', 'EUR', 'GBP'].map(sym => {
            const r = rates.find(x => x.symbol === sym);
            if (!r) return null;
            const chg = fxChange[sym];
            const up = chg != null && chg > 0.001;
            const down = chg != null && chg < -0.001;
            const cls = up ? 'text-emerald-600' : down ? 'text-rose-600' : 'text-gray-800';
            return (
              <div key={sym} className="flex justify-between items-center">
                <span className="text-xs font-semibold text-gray-600">{sym}/TRY</span>
                <span className={`text-xs font-bold ${cls}`}>
                  {r.sell.toLocaleString('tr-TR', { minimumFractionDigits: 3 })}
                  {(up || down) && (
                    <span className="ml-1 text-[10px]">{up ? '▲' : '▼'}{Math.abs(chg).toFixed(2)}%</span>
                  )}
                </span>
              </div>
            );
          })}
          <Link
            to="/market/economy"
            className="block text-center text-xs font-semibold text-[#093eaa] hover:underline pt-1 border-t border-gray-100 mt-1"
          >
            {t('Tüm göstergeler →')}
          </Link>
        </div>
      </div>

      {/* Halka Arz Takvimi */}
      <div>
        <h3 className="font-bold text-lg mb-4 flex items-center gap-2">
          <CalendarDays className="w-5 h-5 text-[#093eaa]" /> {t('Halka Arz Takvimi')}
        </h3>
        {/* Aşağı kaydırdıkça eskiye doğru (liste yeniden→eskiye sıralı); kaynak logolu satırlar. */}
        <div className="bg-white rounded-md border border-gray-200 overflow-hidden shadow-sm">
          {ipos.length === 0 && (
            <div className="p-4 text-sm text-gray-400">{t('Yükleniyor...')}</div>
          )}
          <div className="max-h-[460px] overflow-y-auto m3-scroll">
            {ipos.map((ipo, i) => (
              <div
                key={i}
                className={`p-3 flex items-center gap-3 ${i < ipos.length - 1 ? 'border-b border-gray-100' : ''}`}
              >
                <StockLogo symbol={ipo.ticker} name={ipo.name} size={30} />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-0.5">
                    <span className="text-xs font-black text-[#093eaa] shrink-0">{ipo.ticker}</span>
                    <span className="text-xs text-gray-700 font-semibold truncate">{ipo.name}</span>
                  </div>
                  <span className="text-[10px] text-gray-400">{ipo.date}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </aside>
  );
}
