import { useState } from 'react';
import { X, Coins } from 'lucide-react';
import { addCouponIncome } from '../../../api/portfolioApi';
import DateTimeField from './DateTimeField';
import { useTranslation } from '../../../i18n/LanguageContext';
import {
  extractApiErrorMessage,
  formatGroupedInput,
  parseGroupedInput,
} from '../utils/transactionFormUtils';

/**
 * Manuel DİBS/Kira Sertifikası kupon ödeme kaydı modalı.
 *
 * Props:
 *   portfolioId
 *   holding: PortfolioHoldingResponse  (BOND, kupon ödeme yapan kategorilerden)
 *   onClose()
 *   onAdded(updatedPortfolio)
 */
export default function CouponIncomeModal({ portfolioId, holding, onClose, onAdded }) {
  const { t } = useTranslation();

  const now = new Date();
  const localNow = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);

  const [amount, setAmount] = useState('');
  const [paymentDate, setPaymentDate] = useState(localNow);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const symbol = holding?.symbol ?? '';
  const name = holding?.name ?? symbol;
  const couponRate = holding?.couponRate ?? null;
  const nominalQty = holding?.totalQuantity ?? null;
  // Tahmini yıllık kupon (basit, kullanıcıya referans için)
  const estimatedAnnualCoupon = nominalQty != null && couponRate != null
    ? (parseFloat(nominalQty) * parseFloat(couponRate)) / 100
    : null;

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');

    const amt = parseFloat(amount);
    if (!Number.isFinite(amt) || amt <= 0) {
      setError(t("Kupon tutarı 0'dan büyük olmalıdır."));
      return;
    }
    if (!paymentDate) {
      setError(t('Lütfen ödeme tarihini seçin.'));
      return;
    }

    setLoading(true);
    try {
      const updated = await addCouponIncome(portfolioId, {
        symbol,
        amount: amt,
        paymentDate: new Date(paymentDate).toISOString().replace('Z', ''),
      });
      onAdded(updated);
      onClose();
    } catch (err) {
      setError(extractApiErrorMessage(err) || t('Kupon kaydedilemedi.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1a1b22]/30 backdrop-blur-sm">
      <div className="bg-white rounded-xl shadow-2xl border border-[#e2e1eb] w-full max-w-md text-[#1a1b22] flex flex-col overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#e2e1eb]">
          <div className="flex items-center gap-2">
            <Coins className="w-5 h-5 text-[#093eaa]" />
            <div>
              <h2 className="font-bold text-lg">{t('Kupon Ödemesi Ekle')}</h2>
              <p className="text-sm text-[#434653]">
                <span className="font-bold text-[#1a1b22]">{symbol}</span>
                {name && name !== symbol && (
                  <span className="ml-1 text-[#747684]">· {name}</span>
                )}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label={t('Kapat')}
            className="text-[#747684] hover:text-[#1a1b22] rounded-full p-1 hover:bg-[#f3f3fc] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {/* Bilgi notu */}
          <div className="bg-[#f3f3fc] rounded-lg px-3 py-2.5 text-[12px] text-[#434653] leading-snug">
            {t('Bankanızdan kupon ödemesi geldiğinde tutarı buraya girin.')}
            <br />
            <span className="text-[#747684]">
              {t('Realized gelir olarak portföye yansır; açık nominal pozisyonunuz değişmez.')}
            </span>
          </div>

          {/* Tahmini referans */}
          {estimatedAnnualCoupon != null && estimatedAnnualCoupon > 0 && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 text-[11px] text-amber-800 leading-snug">
              {t('Referans: Mevcut {nominal} TL nominal × %{rate} = ~{est} TL yıllık brüt kupon (genelde 6 ayda bir ödenir).', {
                nominal: parseFloat(nominalQty).toLocaleString('tr-TR'),
                rate: parseFloat(couponRate).toFixed(2),
                est: estimatedAnnualCoupon.toLocaleString('tr-TR', { maximumFractionDigits: 2 }),
              })}
            </div>
          )}

          {/* Tutar */}
          <div>
            <label className="block text-xs font-semibold text-[#434653] mb-1.5">
              {t('Kupon Tutarı (TL)')} *
            </label>
            <input
              type="text"
              inputMode="decimal"
              value={formatGroupedInput(amount)}
              onChange={e => setAmount(parseGroupedInput(e.target.value))}
              placeholder="0,00"
              className="w-full bg-[#f3f3fc] border border-[#e2e1eb] rounded-xl px-4 py-2.5 text-sm text-[#1a1b22] placeholder-[#747684] focus:outline-none focus:border-[#002a7d] focus:ring-1 focus:ring-[#002a7d]"
              autoFocus
            />
            <p className="mt-1 text-[11px] text-[#747684] leading-snug">
              {t('Brüt tutarı girin (vergi/stopaj dahil değil — basit kayıt).')}
            </p>
          </div>

          {/* Tarih */}
          <div>
            <label className="block text-xs font-semibold text-[#434653] mb-1.5">
              {t('Ödeme Tarihi')} *
            </label>
            <DateTimeField value={paymentDate} onChange={setPaymentDate} />
          </div>

          {/* Hata */}
          {error && (
            <p className="text-rose-700 text-sm bg-rose-50 px-3 py-2 rounded-lg">{error}</p>
          )}

          {/* Butonlar */}
          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-[#e2e1eb] rounded-xl text-sm font-semibold text-[#434653] hover:bg-[#f3f3fc] transition-colors"
            >
              {t('İptal')}
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 bg-[#093eaa] hover:bg-[#072e80] text-white px-4 py-2.5 rounded-xl text-sm font-semibold disabled:opacity-50 transition-colors"
            >
              {loading ? t('Kaydediliyor…') : t('Kuponu Kaydet')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
