import { useEffect, useMemo, useState } from 'react';
import { ChevronDown, TrendingUp, TrendingDown } from 'lucide-react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { getDepositRates } from '../../../api/marketApi.js';
import { useTranslation } from '../../../context/LanguageContext';

const NAVY = '#093eaa';
const EMERALD = '#059669';

const DAY_PRESETS = [
  { label: '1 ay', days: 32 },
  { label: '3 ay', days: 92 },
  { label: '6 ay', days: 181 },
  { label: '1 yıl', days: 365 },
  { label: '2 yıl', days: 732 },
];

const fmtTL = (v) =>
  v == null || !Number.isFinite(v) ? '—'
    : `${v.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} TL`;
const fmtTL0 = (v) =>
  v == null || !Number.isFinite(v) ? '—' : `${v.toLocaleString('tr-TR', { maximumFractionDigits: 0 })} TL`;
const fmtPct = (v) =>
  v == null || !Number.isFinite(v) ? '—' : `%${v.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}`;

function rateForTerm(rates, d) {
  if (!rates) return null;
  if (d <= 31) return rates.upTo1Month;
  if (d <= 92) return rates.upTo3Months;
  if (d <= 181) return rates.upTo6Months;
  return rates.upTo1Year;
}
function stopajForTerm(rates, d) {
  const s6 = rates?.stopaj6m != null ? Number(rates.stopaj6m) : 17.5;
  const s1y = rates?.stopaj1y != null ? Number(rates.stopaj1y) : 15;
  const sOver = rates?.stopajOver1y != null ? Number(rates.stopajOver1y) : 10;
  if (d <= 181) return s6;
  if (d <= 365) return s1y;
  return sOver;
}

const LABEL = 'block text-[10px] font-bold uppercase tracking-wider text-[#505f76] mb-1.5';
const FIELD = 'w-full bg-white border border-[#c4c5d5] rounded-xl px-4 py-3 text-base font-semibold text-gray-900 focus:outline-none focus:border-[#093eaa] focus:ring-1 focus:ring-[#093eaa] transition-colors';

export default function DepositCalculatorPage() {
  const { t } = useTranslation();
  const [rates, setRates] = useState(null);
  const [amount, setAmount] = useState(100000);
  const [days, setDays] = useState(92);
  const [annualRate, setAnnualRate] = useState('');
  const [stopaj, setStopaj] = useState('17.5');

  useEffect(() => { getDepositRates().then(setRates).catch(() => {}); }, []);
  useEffect(() => {
    const r = rateForTerm(rates, Number(days));
    if (r != null) setAnnualRate(String(Number(r).toFixed(2)));
  }, [days, rates]);
  useEffect(() => { setStopaj(String(stopajForTerm(rates, Number(days)))); }, [days, rates]);

  const currentRate = rateForTerm(rates, Number(days));
  const inflationYoy = rates?.inflationYoy != null ? Number(rates.inflationYoy) : null;

  const result = useMemo(() => {
    const P = Number(amount);
    const d = Math.round(Number(days));
    const a = Number(annualRate);
    const s = Number(stopaj);
    if (!Number.isFinite(P) || P <= 0 || !Number.isFinite(d) || d <= 0 || !Number.isFinite(a) || a < 0) return null;
    const grossInterest = P * (a / 100) * (d / 365);
    const tax = grossInterest * (Number.isFinite(s) ? s : 0) / 100;
    const netInterest = grossInterest - tax;
    const endTotal = P + netInterest;
    const netReturnPct = (netInterest / P) * 100;
    let periodInflation = null;
    let realPct = null;
    if (inflationYoy != null) {
      periodInflation = inflationYoy * (d / 365);
      realPct = ((1 + netReturnPct / 100) / (1 + periodInflation / 100) - 1) * 100;
    }
    return { principal: P, grossInterest, tax, netInterest, endTotal, netReturnPct, periodInflation, realPct };
  }, [amount, days, annualRate, stopaj, inflationYoy]);

  const donutData = result
    ? [{ name: t('Anapara'), value: result.principal, color: NAVY },
       { name: t('Net Faiz'), value: Math.max(0, result.netInterest), color: EMERALD }]
    : [];

  return (
    <div className="max-w-5xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl sm:text-3xl font-black tracking-tight text-[#093eaa] mb-2">{t('Mevduat Hesaplama')}</h1>
        <p className="text-sm text-[#434653]">{t('Vade sonu net getirinizi ve enflasyona göre reel getirinizi görmek için mevduat hesaplama aracını kullanın.')}</p>
      </div>

      <div className="bg-white border border-[#c4c5d5] rounded-2xl p-6 sm:p-8 shadow-sm flex flex-col gap-6">
        {/* Form */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div>
            <label className={LABEL}>{t('Anapara')}</label>
            <div className="relative">
              <input type="number" min="0" value={amount} onChange={e => setAmount(e.target.value)} className={`${FIELD} pr-10`} />
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-[#505f76] text-sm">TL</span>
            </div>
          </div>
          <div>
            <label className={LABEL}>{t('Vade')}</label>
            <div className="relative">
              <select value={days} onChange={e => setDays(Number(e.target.value))} className={`${FIELD} appearance-none pr-10 cursor-pointer`}>
                {DAY_PRESETS.map(p => <option key={p.days} value={p.days}>{t(p.label)} ({p.days}{t('g')})</option>)}
              </select>
              <ChevronDown className="w-5 h-5 absolute right-3 top-1/2 -translate-y-1/2 text-[#505f76] pointer-events-none" />
            </div>
          </div>
          <div>
            <label className={LABEL}>{t('Yıllık Brüt Faiz')}</label>
            <div className="relative">
              <input type="number" min="0" step="0.01" value={annualRate} onChange={e => setAnnualRate(e.target.value)} className={`${FIELD} pr-9`} />
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-[#505f76] text-sm">%</span>
            </div>
          </div>
          <div>
            <label className={LABEL}>{t('Stopaj')}</label>
            <div className="relative">
              <input type="number" min="0" step="0.5" value={stopaj} onChange={e => setStopaj(e.target.value)} className={`${FIELD} pr-9`} />
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-[#505f76] text-sm">%</span>
            </div>
          </div>
        </div>

        <p className="text-[11px] text-[#747684] leading-relaxed -mt-1">
          {currentRate != null
            ? `${t('Güncel ortalama')} (${days}${t('g')}): ${fmtPct(Number(currentRate))} · ${t('TCMB EVDS')}. `
            : `${t('Güncel oran yükleniyor...')} `}
          {t('Stopaj canlı değildir; güncel mevzuata göre ayarlıdır')}
          {rates?.stopaj6m != null
            ? ` (6 ${t('aya kadar')} %${Number(rates.stopaj6m).toLocaleString('tr-TR')} · 6ay–1yıl %${Number(rates.stopaj1y).toLocaleString('tr-TR')} · 1yıl+ %${Number(rates.stopajOver1y).toLocaleString('tr-TR')})`
            : ''}. {t('Kendi oranlarınızı girebilirsiniz.')}
        </p>

        {result ? (
          <>
            {/* Sonuç kartları */}
            <div className="pt-6 border-t border-[#e2e1eb] grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div className="bg-[#f3f3fc] border border-[#e2e1eb] rounded-xl p-3 sm:p-5 text-center">
                <p className="text-[10px] font-bold uppercase tracking-wider text-[#505f76] mb-2">{t('Vade Sonu (Net)')}</p>
                <p className="text-xl sm:text-2xl font-black text-[#093eaa] break-words">{fmtTL(result.endTotal)}</p>
              </div>
              <div className="bg-[#f3f3fc] border border-[#e2e1eb] rounded-xl p-3 sm:p-5 text-center">
                <p className="text-[10px] font-bold uppercase tracking-wider text-[#505f76] mb-2">{t('Net Faiz')}</p>
                <p className="text-xl sm:text-2xl font-black text-emerald-600 break-words">{fmtTL(result.netInterest)}</p>
              </div>
              <div className="bg-[#f3f3fc] border border-[#e2e1eb] rounded-xl p-3 sm:p-5 text-center">
                <p className="text-[10px] font-bold uppercase tracking-wider text-[#505f76] mb-2">{t('Net Getiri')}</p>
                <p className="text-xl sm:text-2xl font-black text-gray-900 break-words">{fmtPct(result.netReturnPct)}</p>
              </div>
            </div>

            {/* Donut + detay */}
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4 sm:gap-6 flex-wrap">
              <div className="relative w-[150px] h-[150px] shrink-0">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={donutData} dataKey="value" innerRadius={54} outerRadius={74} paddingAngle={2} stroke="none">
                      {donutData.map((d, i) => <Cell key={i} fill={d.color} />)}
                    </Pie>
                    <Tooltip formatter={(v) => fmtTL(v)} wrapperStyle={{ zIndex: 50 }} />
                  </PieChart>
                </ResponsiveContainer>
                <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                  <span className="text-[10px] text-[#505f76] uppercase">{t('Net Getiri')}</span>
                  <span className="text-base font-bold text-emerald-600">{fmtPct(result.netReturnPct)}</span>
                </div>
              </div>
              <div className="space-y-2.5 text-sm">
                <div className="flex items-center gap-2">
                  <span className="w-3 h-3 rounded-full" style={{ background: NAVY }} />
                  <span className="text-[#505f76]">{t('Anapara')}:</span>
                  <span className="font-semibold text-gray-800">{fmtTL0(result.principal)}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="w-3 h-3 rounded-full" style={{ background: EMERALD }} />
                  <span className="text-[#505f76]">{t('Net Faiz')}:</span>
                  <span className="font-semibold text-emerald-600">{fmtTL0(result.netInterest)}</span>
                </div>
                <div className="text-[11px] text-[#747684] pt-1">
                  {t('Brüt Faiz')}: {fmtTL0(result.grossInterest)} · {t('Stopaj')}: −{fmtTL0(result.tax)}
                </div>
              </div>
            </div>

            {/* Reel getiri */}
            {result.realPct != null && (
              <div className={`rounded-xl border p-4 flex items-center gap-4
                ${result.realPct >= 0 ? 'bg-emerald-50 border-emerald-200' : 'bg-rose-50 border-rose-200'}`}>
                <div className={`shrink-0 w-10 h-10 rounded-xl flex items-center justify-center
                  ${result.realPct >= 0 ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'}`}>
                  {result.realPct >= 0 ? <TrendingUp className="w-5 h-5" /> : <TrendingDown className="w-5 h-5" />}
                </div>
                <div>
                  <p className="text-[10px] font-bold uppercase tracking-wider text-[#505f76]">{t('Enflasyona Göre Reel Getiri')}</p>
                  <p className={`text-xl font-black ${result.realPct >= 0 ? 'text-emerald-700' : 'text-rose-700'}`}>
                    {result.realPct >= 0 ? '+' : ''}{fmtPct(result.realPct)}
                  </p>
                  <p className="text-xs text-[#747684]">
                    {result.realPct >= 0 ? t('Paranız enflasyonu yendi.') : t('Paranız enflasyona yenildi.')}
                    {' '}{t('Dönem enflasyonu')} ~{fmtPct(result.periodInflation)}
                    {inflationYoy != null ? ` (${t('yıllık')} ${fmtPct(inflationYoy)})` : ''}
                  </p>
                </div>
              </div>
            )}

            <p className="text-[11px] text-[#747684] text-center leading-relaxed">
              {t('Faiz basit yöntemle: anapara × yıllık faiz × (gün/365). Reel getiri, dönem enflasyonu (yıllık TÜFE\'nin vadeye oranlanmışı) ile yaklaşık hesaplanır. Bilgilendirme amaçlıdır.')}
            </p>
          </>
        ) : (
          <p className="text-sm text-[#747684] text-center py-4">{t('Geçerli değerler girin.')}</p>
        )}
      </div>
    </div>
  );
}
