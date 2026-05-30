import { useState, useEffect, useMemo } from 'react';
import { ArrowLeftRight, ChevronDown } from 'lucide-react';
import { FX_META } from '../utils/fxMeta';
import { useTranslation } from '../../../../context/LanguageContext';

/** Küçük döviz seçici (chip görünümü) — FxConverter'ın alt parçası. */
function CurrencyChip({ value, onChange, currencies }) {
  return (
    <div className="relative shrink-0">
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        className="appearance-none bg-[#eef2f8] hover:bg-[#e3eaf6] text-[#093eaa] font-bold text-xs rounded-full pl-2.5 pr-6 py-1 cursor-pointer focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 transition-colors"
      >
        {currencies.map(c => <option key={c} value={c}>{c}</option>)}
      </select>
      <ChevronDown className="w-3 h-3 absolute right-1.5 top-1/2 -translate-y-1/2 text-[#093eaa] pointer-events-none" />
    </div>
  );
}

/**
 * Döviz Çevirici — kompakt kart (sidebar için tasarlandı).
 *
 * Props:
 *   symbol: önseçili "from" sembolü (URL'den)
 *   allRates: TCMB rates listesi (sembol → sell oranı)
 *   embedded: true ise dış kart sarmalayıcısı yok
 */
export default function FxConverter({ symbol, allRates, embedded = false }) {
  const { t } = useTranslation();
  const sym = symbol?.toUpperCase();
  const currencies = useMemo(() => ['TRY', ...Object.keys(FX_META).filter(k => k !== 'TRY')], []);

  const [fromCur, setFromCur] = useState(sym ?? 'USD');
  const [toCur, setToCur]     = useState('TRY');
  const [amount, setAmount]   = useState('1');

  useEffect(() => { if (sym) setFromCur(sym); }, [sym]);

  function getRateTry(cur) {
    if (cur === 'TRY') return 1;
    const r = allRates.find(x => x.symbol === cur);
    return r?.sell ? parseFloat(r.sell) : null;
  }
  const fromRate = getRateTry(fromCur);
  const toRate   = getRateTry(toCur);

  function calculate() {
    const a = parseFloat(amount || 0);
    if (!a || isNaN(a) || !fromRate || !toRate) return '-';
    return ((a * fromRate) / toRate).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 6 });
  }
  const unitRate = (fromRate && toRate)
    ? (fromRate / toRate).toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 6 }) : null;

  function swap() { setFromCur(toCur); setToCur(fromCur); }

  return (
    <div className={embedded ? '' : 'bg-white rounded-3xl border border-[#e2e8f0] shadow-[0_1px_3px_rgba(15,23,42,0.06)] p-5'}>
      <h2 className="font-bold text-[#1a1c1e] mb-3 flex items-center gap-2 text-sm">
        <ArrowLeftRight className="w-4 h-4 text-[#093eaa]" /> {t('Döviz Çevirici')}
      </h2>

      <div className="space-y-2">
        <div className="relative">
          <input
            type="number" value={amount} onChange={e => setAmount(e.target.value)} placeholder="1"
            className="w-full pl-3 pr-20 py-2.5 rounded-xl border border-[#e2e8f0] bg-white text-[#1a1c1e] font-semibold tabular-nums focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa] transition-all"
          />
          <div className="absolute right-2 top-1/2 -translate-y-1/2"><CurrencyChip value={fromCur} onChange={setFromCur} currencies={currencies} /></div>
        </div>

        <div className="flex justify-center">
          <button onClick={swap} title={t('Ters çevir')}
            className="m3-state w-8 h-8 rounded-full bg-[#093eaa] text-white flex items-center justify-center shadow-[0_3px_10px_-3px_rgba(9,62,170,0.6)] hover:rotate-180 transition-transform duration-500">
            <ArrowLeftRight className="w-4 h-4" />
          </button>
        </div>

        <div className="relative">
          <input
            type="text" readOnly value={calculate()}
            className="w-full pl-3 pr-20 py-2.5 rounded-xl border border-[#eef2f8] bg-[#f6f8fc] text-[#1a1c1e] font-semibold tabular-nums"
          />
          <div className="absolute right-2 top-1/2 -translate-y-1/2"><CurrencyChip value={toCur} onChange={setToCur} currencies={currencies} /></div>
        </div>
      </div>

      <div className="mt-3 p-3 rounded-xl bg-[#eef4ff] border border-[#dbe6ff] text-center">
        {unitRate ? (
          <p className="text-sm font-black text-[#093eaa] tabular-nums">1 {fromCur} = {unitRate} {toCur}</p>
        ) : (
          <p className="text-xs text-[#5a6472]">{t('Bu döviz çifti için kur bulunamadı.')}</p>
        )}
      </div>
    </div>
  );
}
