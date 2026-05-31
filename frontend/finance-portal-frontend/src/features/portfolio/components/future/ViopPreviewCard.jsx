import { useMemo } from 'react';
import { TrendingUp, TrendingDown } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';
import { notional, marginPosted, pnl as pnlMath, leverage as leverageMath } from '../../utils/viopMath';

/**
 * AddTransactionModal'da VİOP işlemleri için canlı önizleme kartı.
 * Kullanıcı miktar/fiyat/yön girdikçe nominal, teminat, kaldıraç, komisyon, toplam bağlanan değerini gösterir.
 *
 * Props:
 *   qty            — kontrat adedi (string|number)
 *   price          — kontrat fiyatı (TL)
 *   direction      — 'LONG' veya 'SHORT'
 *   commission     — komisyon (TL)
 *   instrument     — { symbol, viopSpec: { multiplier, marginRate, currency } }
 *                    veya backend'in döndürdüğü diğer alanlar (yedek inference)
 *
 * Backend kontrat spec'i (multiplier, marginRate) instrument.viopSpec'ten ya da
 * instrument.contractMultiplier / contractMarginRate gibi yedeklerden okunur.
 * Spec yoksa "Sözleşme bilgileri yüklenemedi" uyarısı gösterilir.
 */
export default function ViopPreviewCard({ qty, price, direction = 'LONG', commission, instrument }) {
  const { t } = useTranslation();

  const spec = useMemo(() => {
    if (!instrument) return null;
    const s = instrument.viopSpec || {};
    const m = s.multiplier ?? instrument.contractMultiplier ?? instrument.viopMultiplier;
    const mr = s.marginRate ?? instrument.contractMarginRate ?? instrument.viopMarginRate;
    if (m == null || mr == null) return null;
    return { multiplier: Number(m), marginRate: Number(mr) };
  }, [instrument]);

  const qtyNum = Number(qty) || 0;
  const priceNum = Number(price) || 0;
  const isShort = (direction || 'LONG').toUpperCase() === 'SHORT';

  if (!spec) {
    return (
      <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-[12px] text-amber-800 leading-snug">
        <span className="font-semibold">{t('Not:')}</span>{' '}
        {t('Bu sözleşme için çarpan/teminat bilgisi yüklenemedi — önizleme yaklaşık olabilir. Pozisyon yine de kaydedilir.')}
      </div>
    );
  }

  const nominalVal = notional(qtyNum, priceNum, spec.multiplier);
  const marginVal  = marginPosted(qtyNum, priceNum, spec.multiplier, spec.marginRate);
  const lev        = leverageMath(nominalVal, marginVal);
  const commissionNum = Number(commission) || 0;
  const totalLocked = marginVal + commissionNum;

  return (
    <div className="rounded-xl border border-[#c4c5d5] bg-[#f3f3fc] p-3 space-y-2">
      <div className="flex items-center gap-2 text-[11px] font-bold text-[#434653] uppercase tracking-wider">
        {t('İşlem Önizlemesi')}
      </div>

      <div className="flex items-center gap-2 text-[12px]">
        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md font-bold text-[11px] ${
          isShort
            ? 'bg-rose-100 text-rose-700 border border-rose-200'
            : 'bg-emerald-100 text-emerald-700 border border-emerald-200'
        }`}>
          {isShort ? <TrendingDown className="w-3 h-3" /> : <TrendingUp className="w-3 h-3" />}
          {isShort ? t('SHORT') : t('LONG')}
        </span>
        <span className="text-[#5a6472]">
          {qtyNum || '–'} {t('kontrat')} × {fmtPrice(priceNum)} ₺
        </span>
      </div>

      <div className="grid grid-cols-2 gap-x-3 gap-y-1.5 text-[12px]">
        <span className="text-[#5a6472]">{t('Nominal')}:</span>
        <span className="text-right font-bold text-[#1a1b22] tabular-nums">{fmtTRY(nominalVal)}</span>

        <span className="text-[#5a6472]">{t('Teminat')}:</span>
        <span className="text-right font-bold text-[#1a1b22] tabular-nums">{fmtTRY(marginVal)}</span>

        <span className="text-[#5a6472]">{t('Kaldıraç')}:</span>
        <span className="text-right font-bold text-amber-700 tabular-nums">{lev > 0 ? lev.toFixed(2) + 'x' : '–'}</span>

        {commissionNum > 0 && (
          <>
            <span className="text-[#5a6472]">{t('Komisyon')}:</span>
            <span className="text-right text-[#1a1b22] tabular-nums">{fmtTRY(commissionNum)}</span>
          </>
        )}
      </div>

      <div className="border-t border-[#c4c5d5] pt-1.5 flex items-center justify-between text-[12px]">
        <span className="font-bold text-[#434653]">{t('Cebinden Çıkacak')}:</span>
        <span className="font-black text-[#093eaa] tabular-nums">{fmtTRY(totalLocked)}</span>
      </div>

      <p className="text-[10.5px] text-[#747684] leading-snug">
        {t('Çarpan')}: <span className="font-semibold">{spec.multiplier}</span> · {t('Marjin')}: <span className="font-semibold">%{(spec.marginRate * 100).toFixed(1)}</span>
      </p>
    </div>
  );
}

function fmtTRY(v) {
  return (Number(v) || 0).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' ₺';
}

function fmtPrice(v) {
  return (Number(v) || 0).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
}
