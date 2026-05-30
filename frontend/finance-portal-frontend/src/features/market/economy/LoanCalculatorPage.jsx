import { useEffect, useMemo, useState } from 'react';
import { ChevronDown, ListOrdered } from 'lucide-react';
import {
  PieChart, Pie, Cell, ResponsiveContainer,
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
} from 'recharts';
import { getLoanRates } from '../../../api/marketApi.js';
import { useTranslation } from '../../../i18n/LanguageContext';

const NAVY = '#093eaa';
const EMERALD = '#059669';
const ROSE = '#e11d48';

const LOAN_TYPES = [
  { key: 'personal', label: 'İhtiyaç' },
  { key: 'housing', label: 'Konut' },
  { key: 'vehicle', label: 'Taşıt' },
  { key: 'commercial', label: 'Ticari' },
];

const TERM_OPTIONS = [3, 6, 9, 12, 18, 24, 36, 48, 60];
const TAX_FACTOR = { personal: 0.30, vehicle: 0.30, housing: 0, commercial: 0.05 };

const fmtTL = (v) =>
  v == null || !Number.isFinite(v) ? '—'
    : `${v.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} TL`;
const fmtTL0 = (v) =>
  v == null || !Number.isFinite(v) ? '—' : `${v.toLocaleString('tr-TR', { maximumFractionDigits: 0 })} TL`;
const fmtPct = (v) =>
  v == null || !Number.isFinite(v) ? '—' : `%${v.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}`;

function fmtCompact(v) {
  const n = Math.abs(Number(v));
  if (n >= 1e6) return `${(v / 1e6).toLocaleString('tr-TR', { maximumFractionDigits: 1 })}M`;
  if (n >= 1e3) return `${(v / 1e3).toLocaleString('tr-TR', { maximumFractionDigits: 0 })}B`;
  return String(Math.round(v));
}

const LABEL = 'block text-[10px] font-bold uppercase tracking-wider text-[#505f76] mb-1.5';
const FIELD = 'w-full bg-white border border-[#c4c5d5] rounded-xl px-4 py-3 text-base font-semibold text-gray-900 focus:outline-none focus:border-[#093eaa] focus:ring-1 focus:ring-[#093eaa] transition-colors';

export default function LoanCalculatorPage() {
  const { t } = useTranslation();
  const [rates, setRates] = useState(null);
  const [type, setType] = useState('personal');
  const [amount, setAmount] = useState(100000);
  const [term, setTerm] = useState(12);
  const [annualRate, setAnnualRate] = useState('');
  const [taxIncluded, setTaxIncluded] = useState(true);
  const [showSchedule, setShowSchedule] = useState(false);

  useEffect(() => { getLoanRates().then(setRates).catch(() => {}); }, []);
  useEffect(() => {
    if (rates && rates[type] != null) setAnnualRate(String(Number(rates[type]).toFixed(2)));
  }, [type, rates]);

  const currentRate = rates?.[type] != null ? Number(rates[type]) : null;

  const result = useMemo(() => {
    const P = Number(amount);
    const n = Math.round(Number(term));
    const annual = Number(annualRate);
    if (!Number.isFinite(P) || P <= 0 || !Number.isFinite(n) || n <= 0 || !Number.isFinite(annual) || annual < 0) return null;
    const taxFactor = taxIncluded ? (TAX_FACTOR[type] || 0) : 0;
    const r = (annual / 100 / 12) * (1 + taxFactor);
    const installment = r > 0 ? (P * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1) : P / n;
    const total = installment * n;
    const interest = total - P;
    const schedule = [];
    let balance = P;
    for (let i = 1; i <= n; i++) {
      const interestPart = balance * r;
      const principalPart = installment - interestPart;
      balance -= principalPart;
      schedule.push({ i, installment, interestPart, principalPart, balance: Math.max(0, balance) });
    }
    return { principal: P, installment, total, interest, monthlyRate: r * 100, taxFactor, schedule };
  }, [amount, term, annualRate, type, taxIncluded]);

  const donutData = result
    ? [{ name: t('Anapara'), value: result.principal, color: EMERALD },
       { name: t('Toplam Faiz'), value: result.interest, color: ROSE }]
    : [];

  return (
    <div className="max-w-5xl mx-auto">
      {/* Başlık */}
      <div className="mb-6">
        <h1 className="text-3xl font-black tracking-tight text-[#093eaa] mb-2">{t('Kredi Hesaplama')}</h1>
        <p className="text-sm text-[#434653]">{t('Aylık taksit, faiz ve toplam ödeme tutarınızı görmek için kredi hesaplama aracını kullanın.')}</p>
      </div>

      <div className="bg-white border border-[#c4c5d5] rounded-2xl p-6 sm:p-8 shadow-sm flex flex-col gap-6">
        {/* Sekmeler */}
        <div className="bg-[#eeedf7] border border-[#c4c5d5] rounded-xl p-1.5 flex gap-1 w-full max-w-2xl mx-auto">
          {LOAN_TYPES.map(lt => (
            <button
              key={lt.key}
              type="button"
              onClick={() => setType(lt.key)}
              className={`flex-1 text-sm font-bold py-2.5 rounded-lg transition-all text-center
                ${type === lt.key
                  ? 'bg-white text-[#093eaa] shadow-sm border border-[#c4c5d5]'
                  : 'text-[#505f76] hover:bg-white/60'}`}
            >
              {t(lt.label)}
            </button>
          ))}
        </div>

        {/* Form */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div>
            <label className={LABEL}>{t('Kredi Tutarı')}</label>
            <div className="relative">
              <input type="number" min="0" value={amount} onChange={e => setAmount(e.target.value)} className={`${FIELD} pr-10`} />
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-[#505f76] text-sm">TL</span>
            </div>
          </div>
          <div>
            <label className={LABEL}>{t('Vade')}</label>
            <div className="relative">
              <select value={term} onChange={e => setTerm(Number(e.target.value))} className={`${FIELD} appearance-none pr-10 cursor-pointer`}>
                {TERM_OPTIONS.map(m => <option key={m} value={m}>{m} {t('Ay')}</option>)}
              </select>
              <ChevronDown className="w-5 h-5 absolute right-3 top-1/2 -translate-y-1/2 text-[#505f76] pointer-events-none" />
            </div>
          </div>
          <div>
            <label className={LABEL}>{t('Faiz Oranı')}</label>
            <div className="relative">
              <input type="number" min="0" step="0.01" value={annualRate} onChange={e => setAnnualRate(e.target.value)} className={`${FIELD} pr-9`} />
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-[#505f76] text-sm">%</span>
            </div>
          </div>
          <div className="flex items-end">
            <button
              type="button"
              onClick={() => setShowSchedule(false)}
              className="w-full bg-[#093eaa] hover:bg-[#0b50d0] text-white rounded-xl py-3.5 font-bold text-sm transition-colors shadow-sm"
            >
              {t('Hesapla')}
            </button>
          </div>
        </div>

        {/* Faiz notu + vergi anahtarı */}
        <div className="flex items-center justify-between gap-3 flex-wrap -mt-1">
          <p className="text-[11px] text-[#747684] max-w-md leading-relaxed">
            {currentRate != null
              ? `${t('Güncel ortalama')}: ${fmtPct(currentRate)} ${t('(yıllık)')} · ${t('TCMB EVDS — tüm bankaların ortalaması; kendi oranınızı girebilirsiniz.')}`
              : t('Güncel oran yükleniyor...')}
          </p>
          <label className="flex items-center gap-2 cursor-pointer shrink-0">
            <span className="text-xs font-semibold text-[#505f76]">{t('Vergiler dahil (KKDF/BSMV)')}</span>
            <input type="checkbox" checked={taxIncluded} onChange={e => setTaxIncluded(e.target.checked)} className="sr-only peer" />
            <span className="relative w-10 h-5 bg-[#c4c5d5] rounded-full transition-colors peer-checked:bg-[#093eaa] after:content-[''] after:absolute after:top-0.5 after:left-0.5 after:w-4 after:h-4 after:bg-white after:rounded-full after:shadow after:transition-all peer-checked:after:translate-x-5" />
          </label>
        </div>

        {result ? (
          <>
            {/* Sonuç kartları */}
            <div className="pt-6 border-t border-[#e2e1eb] grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div className="bg-[#f3f3fc] border border-[#e2e1eb] rounded-xl p-5 text-center">
                <p className="text-[10px] font-bold uppercase tracking-wider text-[#505f76] mb-2">{t('Aylık Taksit')}</p>
                <p className="text-2xl font-black text-[#093eaa]">{fmtTL(result.installment)}</p>
              </div>
              <div className="bg-[#f3f3fc] border border-[#e2e1eb] rounded-xl p-5 text-center">
                <p className="text-[10px] font-bold uppercase tracking-wider text-[#505f76] mb-2">{t('Toplam Geri Ödeme')}</p>
                <p className="text-2xl font-black text-gray-900">{fmtTL(result.total)}</p>
              </div>
              <div className="bg-[#f3f3fc] border border-[#e2e1eb] rounded-xl p-5 text-center">
                <p className="text-[10px] font-bold uppercase tracking-wider text-[#505f76] mb-2">{t('Toplam Faiz')}</p>
                <p className="text-2xl font-black text-rose-600">{fmtTL(result.interest)}</p>
              </div>
            </div>

            {/* Donut dağılım */}
            <div className="flex items-center justify-center gap-6 flex-wrap">
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
                  <span className="text-[10px] text-[#505f76] uppercase">{t('Faiz Payı')}</span>
                  <span className="text-base font-bold text-rose-600">{fmtPct(result.total > 0 ? result.interest / result.total * 100 : 0)}</span>
                </div>
              </div>
              <div className="space-y-2.5 text-sm">
                <div className="flex items-center gap-2">
                  <span className="w-3 h-3 rounded-full" style={{ background: EMERALD }} />
                  <span className="text-[#505f76]">{t('Anapara')}:</span>
                  <span className="font-semibold text-gray-800">{fmtTL0(result.principal)}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="w-3 h-3 rounded-full" style={{ background: ROSE }} />
                  <span className="text-[#505f76]">{t('Toplam Faiz')}:</span>
                  <span className="font-semibold text-rose-600">{fmtTL0(result.interest)}</span>
                </div>
                <div className="text-[11px] text-[#747684] pt-1">
                  {result.taxFactor > 0 ? t('Aylık maliyet (vergi dahil)') : t('Aylık faiz')}: {fmtPct(result.monthlyRate)}
                </div>
              </div>
            </div>

            {/* Ödeme planı butonu */}
            <div className="flex justify-center">
              <button type="button" onClick={() => setShowSchedule(s => !s)} className="text-[#093eaa] font-bold text-sm hover:underline flex items-center gap-1.5">
                <ListOrdered className="w-4 h-4" />
                {showSchedule ? t('Ödeme planını gizle') : t('Ödeme Planını Göster')}
              </button>
            </div>

            <p className="text-[11px] text-[#747684] text-center leading-relaxed">
              {taxIncluded
                ? t('Eşit taksitli (anüite); ihtiyaç/taşıt KKDF+BSMV, ticari BSMV dahil (konut muaf). Banka masrafları hariç.')
                : t('Eşit taksitli (anüite); vergiler (KKDF/BSMV) ve banka masrafları hariç. Bilgilendirme amaçlıdır.')}
            </p>
          </>
        ) : (
          <p className="text-sm text-[#747684] text-center py-4">{t('Geçerli değerler girin.')}</p>
        )}
      </div>

      {/* Ödeme planı */}
      {result && showSchedule && (
        <div className="mt-6 space-y-4">
          <div className="bg-white rounded-2xl border border-[#c4c5d5] shadow-sm p-4 sm:p-6">
            <p className="text-sm font-bold text-gray-800 mb-4">{t('Kalan Borç (Ay Ay)')}</p>
            <ResponsiveContainer width="100%" height={220}>
              <AreaChart data={result.schedule.map(s => ({ ay: s.i, kalan: s.balance }))} margin={{ top: 5, right: 12, left: 0, bottom: 4 }}>
                <defs>
                  <linearGradient id="balGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={NAVY} stopOpacity={0.28} />
                    <stop offset="100%" stopColor={NAVY} stopOpacity={0.02} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#eef2f7" vertical={false} />
                <XAxis dataKey="ay" tick={{ fontSize: 11, fill: '#6b7280' }} tickLine={false} axisLine={{ stroke: '#e5e7eb' }} />
                <YAxis tick={{ fontSize: 11, fill: '#6b7280' }} tickLine={false} axisLine={false} width={48} tickFormatter={fmtCompact} />
                <Tooltip formatter={(v) => fmtTL(v)} labelFormatter={(l) => `${l}. ${t('ay')}`} wrapperStyle={{ zIndex: 50 }} />
                <Area type="monotone" dataKey="kalan" name={t('Kalan')} stroke={NAVY} strokeWidth={2.5} fill="url(#balGrad)" dot={false} />
              </AreaChart>
            </ResponsiveContainer>
          </div>

          <div className="bg-white rounded-2xl border border-[#c4c5d5] shadow-sm overflow-hidden">
            <div className="overflow-x-auto max-h-[440px]">
              <table className="w-full text-sm">
                <thead className="bg-[#f3f3fc] sticky top-0">
                  <tr>
                    <th className="text-left px-4 py-2.5 text-xs font-bold text-[#505f76] uppercase tracking-wider">{t('Ay')}</th>
                    <th className="text-right px-4 py-2.5 text-xs font-bold text-[#505f76] uppercase tracking-wider">{t('Taksit')}</th>
                    <th className="text-right px-4 py-2.5 text-xs font-bold text-[#505f76] uppercase tracking-wider">{t('Faiz')}</th>
                    <th className="text-right px-4 py-2.5 text-xs font-bold text-[#505f76] uppercase tracking-wider">{t('Anapara')}</th>
                    <th className="text-right px-4 py-2.5 text-xs font-bold text-[#505f76] uppercase tracking-wider">{t('Kalan')}</th>
                  </tr>
                </thead>
                <tbody>
                  {result.schedule.map((row, idx) => (
                    <tr key={row.i} className={`border-t border-gray-100 ${idx % 2 ? 'bg-[#f3f3fc]/50' : ''}`}>
                      <td className="px-4 py-2 text-gray-700">{row.i}</td>
                      <td className="px-4 py-2 text-right font-medium text-gray-800">{fmtTL(row.installment)}</td>
                      <td className="px-4 py-2 text-right text-rose-600">{fmtTL(row.interestPart)}</td>
                      <td className="px-4 py-2 text-right text-emerald-600">{fmtTL(row.principalPart)}</td>
                      <td className="px-4 py-2 text-right text-gray-600">{fmtTL(row.balance)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
