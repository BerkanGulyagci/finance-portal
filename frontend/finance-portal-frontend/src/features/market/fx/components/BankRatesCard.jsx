import { Link } from 'react-router-dom';
import { Landmark } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';

function fmtAuto(v) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
}

/**
 * Alış/satış makası (spread): TL farkı + farkın satışa oranı (%).
 * Döner: { tl, pct } veya değer eksikse null.
 */
function calcSpread(buy, sell) {
  const b = parseFloat(buy);
  const s = parseFloat(sell);
  if (isNaN(b) || isNaN(s) || s <= 0 || s < b) return null;
  const tl = s - b;
  return { tl, pct: (tl / s) * 100 };
}

function fmtSpreadTl(v) {
  return v.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
}

/**
 * Diğer Bankalar kartı — verilen sembolün ulaşılabilir banka kurları listesi.
 *
 * Props:
 *   rates: { bankName, buyRate, sellRate, has: boolean }[]
 *   loading: boolean
 */
export default function BankRatesCard({ rates, loading }) {
  const { t } = useTranslation();
  const present = rates.filter(r => r.has);
  const absent  = rates.filter(r => !r.has);

  return (
    <section className="bg-white rounded-3xl border border-[#e2e8f0] shadow-[0_1px_3px_rgba(15,23,42,0.06)] p-5">
      <div className="flex items-center justify-between mb-3">
        <h2 className="font-bold text-[#1a1c1e] flex items-center gap-2 text-sm">
          <Landmark className="w-4 h-4 text-[#093eaa]" /> {t('Diğer Bankalar')}
          {present.length > 0 && <span className="text-xs font-semibold text-[#9aa6b6]">({present.length})</span>}
        </h2>
        <Link to="/market/fx?tab=banks" className="text-xs font-semibold text-[#093eaa] hover:underline">{t('Tümünü Gör')}</Link>
      </div>

      {loading ? (
        <p className="text-sm text-[#9aa6b6] py-2">{t('Banka kurları yükleniyor...')}</p>
      ) : present.length === 0 ? (
        <div className="py-4 text-center">
          <p className="text-sm font-semibold text-[#1a1c1e]">{t('Banka kuru bulunamadı')}</p>
          <p className="text-xs text-[#9aa6b6] mt-0.5">{t('Bu döviz için banka kuru yok.')}</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-[1fr_auto_auto_auto] gap-x-3 text-[10px] font-bold text-[#9aa6b6] uppercase tracking-wider pb-1.5 border-b border-[#eef2f8]">
            <span>{t('Banka')}</span>
            <span className="text-right">{t('Alış')}</span>
            <span className="text-right">{t('Satış')}</span>
            <span className="text-right">{t('Makas')}</span>
          </div>
          <div className="divide-y divide-[#f1f5f9] m3-stagger">
            {present.map((r, i) => {
              const spread = calcSpread(r.buyRate, r.sellRate);
              return (
                <div key={`${r.bankName}-${i}`} className="grid grid-cols-[1fr_auto_auto_auto] gap-x-3 items-center py-2 text-sm">
                  <span className="font-semibold text-[#1a1c1e] truncate">{r.bankName}</span>
                  <span className="text-right tabular-nums text-[#5a6472]">{fmtAuto(r.buyRate)}</span>
                  <span className="text-right tabular-nums font-semibold text-[#1a1c1e]">{fmtAuto(r.sellRate)}</span>
                  {spread ? (
                    <span className="text-right tabular-nums leading-tight">
                      <span className="block font-semibold text-[#1a1c1e]">{fmtSpreadTl(spread.tl)}</span>
                      <span className="block text-[11px] text-[#9aa6b6]">%{spread.pct.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                    </span>
                  ) : (
                    <span className="text-right tabular-nums text-[#9aa6b6]">-</span>
                  )}
                </div>
              );
            })}
          </div>
          {absent.length > 0 && (
            <p className="mt-3 pt-3 border-t border-[#eef2f8] text-[11px] text-[#9aa6b6] leading-snug">
              {t('{n} banka bu dövizi kote etmiyor', { n: absent.length })}: {absent.map(r => r.bankName).join(', ')}
            </p>
          )}
        </>
      )}
    </section>
  );
}
