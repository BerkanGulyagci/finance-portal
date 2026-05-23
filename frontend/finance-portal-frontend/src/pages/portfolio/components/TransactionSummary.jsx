import { fmtWithCcy, fmtNum } from '../utils/transactionFormUtils';

/**
 * İşlem (alış/satış) özet kutusu. AddTransactionModal'dan ayrıştırıldı.
 * Tüm hesaplama/validasyon mantığı parent'ta kalır; bu bileşen yalnızca gösterir.
 * Veri yoksa null döner.
 */
export default function TransactionSummary({
  isAmountMode, amountCalc, summaryHasError, isBuy, isGold, goldMeta, isBond,
  commission, currency, useQtyFloor, availableQty, price, form,
  quantityModeTotal, quantitySellExceedsFund, quantitySellExceedsGold,
  quantitySellExceedsBond, goldPieceQtyInvalid, formatSummaryQty, t,
}) {
  // ── Tutar ile modu ──────────────────────────────────────────────────────────
  if (isAmountMode && amountCalc) {
    return (
      <div className={`rounded-xl p-3 text-xs space-y-1.5 ${
        summaryHasError
          ? 'bg-rose-50 border border-rose-200'
          : 'bg-[#f3f3fc]'
      }`}>
        {amountCalc.commissionOverflow ? (
          <p className="text-rose-700 font-semibold">
            {t('Komisyon tutar değerinden büyük veya eşit olamaz.')}
          </p>
        ) : amountCalc.exceedsAvailable ? (
          <p className="text-rose-700 font-semibold">
            {t('Satış miktarı eldeki miktarı aşıyor.')}
            {availableQty != null && ` (${t('Eldeki:')} ${formatSummaryQty(availableQty)})`}
          </p>
        ) : amountCalc.isZero ? (
          <p className="text-rose-700 font-semibold">
            {isBuy
              ? (isGold && goldMeta?.floorQty
                ? t('Bu tutar ile en az 1 adet alınamıyor.')
                : t('Bu tutar ile işlem yapılamıyor.'))
              : (isGold && goldMeta?.floorQty
                ? t('Bu tutar ile en az 1 adet satılamıyor.')
                : t('Bu tutar ile işlem yapılamıyor.'))}
          </p>
        ) : isBuy ? (
          /* BUY özeti */
          <>
            {isGold ? (
              <>
                <SummaryRow label={t('Kullanılan tutar')} value={fmtWithCcy(amountCalc.used, currency)} />
                <SummaryRow label={t('Hesaplanan miktar')} value={formatSummaryQty(amountCalc.qty)} highlight />
                {commission > 0 && (
                  <SummaryRow label={t('Komisyon / Masraf')} value={fmtWithCcy(commission, currency)} />
                )}
                <SummaryRow
                  label={t('Toplam ödeme')}
                  value={fmtWithCcy(
                    amountCalc.totalPayment != null ? amountCalc.totalPayment : parseFloat(form.tradeAmount),
                    currency,
                  )}
                  highlight
                />
              </>
            ) : (
              <>
                <SummaryRow label={t('Hesaplanan miktar')} value={formatSummaryQty(amountCalc.qty)} highlight />
                <SummaryRow label={t('Kullanılan tutar')} value={fmtWithCcy(amountCalc.used, currency)} />
                {commission > 0 && <SummaryRow label={t('Komisyon')} value={fmtWithCcy(commission, currency)} />}
                {useQtyFloor && (
                  <SummaryRow
                    label={t('Kalan nakit')}
                    value={fmtWithCcy(amountCalc.remaining, currency)}
                    dim={amountCalc.remaining <= 0}
                  />
                )}
                <SummaryRow
                  label={t('Toplam ödeme')}
                  value={fmtWithCcy(
                    amountCalc.totalPayment != null ? amountCalc.totalPayment : parseFloat(form.tradeAmount),
                    currency,
                  )}
                  highlight
                />
              </>
            )}
          </>
        ) : (
          /* SELL özeti */
          <>
            <SummaryRow label={t('Satış tutarı')} value={fmtWithCcy(amountCalc.grossSell, currency)} />
            <SummaryRow label={t('Hesaplanan miktar')} value={formatSummaryQty(amountCalc.qty)} highlight />
            {commission > 0 && (
              <SummaryRow
                label={isGold ? t('Komisyon / Masraf') : t('Komisyon')}
                value={fmtWithCcy(commission, currency)}
              />
            )}
            <SummaryRow label={t('Tahmini net gelir')} value={fmtWithCcy(amountCalc.netIncome, currency)} highlight />
            {availableQty != null && (
              <SummaryRow label={t('Eldeki miktar')} value={formatSummaryQty(availableQty)} dim />
            )}
          </>
        )}
      </div>
    );
  }

  // ── Miktar ile modu ─────────────────────────────────────────────────────────
  if (!isAmountMode && (quantityModeTotal != null || quantitySellExceedsFund || quantitySellExceedsGold || quantitySellExceedsBond || goldPieceQtyInvalid)) {
    return (
      <div className={`rounded-xl p-3 text-xs space-y-1.5 ${
        summaryHasError && !quantityModeTotal
          ? 'bg-rose-50 border border-rose-200'
          : 'bg-[#f3f3fc]'
      }`}>
        {goldPieceQtyInvalid && (
          <p className="text-rose-700 font-semibold">{t('Bu altın türünde miktar tam adet olmalıdır.')}</p>
        )}
        {quantitySellExceedsFund && (
          <p className="text-rose-700 font-semibold">
            {t('Satış miktarı eldeki pay miktarını aşamaz.')}
            {availableQty != null && ` (${t('Eldeki:')} ${fmtNum(availableQty, 8).replace(/\.?0+$/, '')} ${t('pay')})`}
          </p>
        )}
        {quantitySellExceedsGold && (
          <p className="text-rose-700 font-semibold">
            {t('Satış miktarı eldeki miktarı aşamaz.')}
            {availableQty != null && ` (${t('Eldeki:')} ${formatSummaryQty(availableQty)})`}
          </p>
        )}
        {quantitySellExceedsBond && (
          <p className="text-rose-700 font-semibold">
            {t('Satış miktarı eldeki miktarı aşamaz.')}
            {availableQty != null && ` (${t('Eldeki:')} ${fmtNum(availableQty, 8).replace(/\.?0+$/, '')})`}
          </p>
        )}
        {quantityModeTotal != null && !quantitySellExceedsFund && !quantitySellExceedsGold && !quantitySellExceedsBond && !goldPieceQtyInvalid && (
          <>
            {isBuy ? (
              <>
                <SummaryRow
                  label={isGold && goldMeta ? t(goldMeta.quantityLabel.replace(' *', '')) : t('Miktar')}
                  value={formatSummaryQty(parseFloat(form.quantity))}
                />
                <SummaryRow
                  label={
                    isBond
                      ? t('Gösterge Değeri')
                      : isGold && goldMeta
                        ? t(goldMeta.priceLabel.replace(' *', ''))
                        : t('Fiyat')
                  }
                  value={fmtWithCcy(price, currency)}
                />
                {(commission > 0 || isBond) && (
                  <SummaryRow
                    label={isGold || isBond ? t('Komisyon / Masraf') : t('Komisyon')}
                    value={fmtWithCcy(commission, currency)}
                  />
                )}
                <SummaryRow label={t('Toplam ödeme')} value={fmtWithCcy(quantityModeTotal.total, currency)} highlight />
              </>
            ) : (
              <>
                <SummaryRow
                  label={isGold && goldMeta ? t(goldMeta.quantityLabel.replace(' *', '')) : t('Miktar')}
                  value={formatSummaryQty(parseFloat(form.quantity))}
                />
                <SummaryRow
                  label={
                    isBond
                      ? t('Gösterge Değeri')
                      : isGold && goldMeta
                        ? t(goldMeta.priceLabel.replace(' *', ''))
                        : t('Fiyat')
                  }
                  value={fmtWithCcy(price, currency)}
                />
                {(commission > 0 || isBond) && (
                  <SummaryRow
                    label={isGold || isBond ? t('Komisyon / Masraf') : t('Komisyon')}
                    value={fmtWithCcy(commission, currency)}
                  />
                )}
                <SummaryRow
                  label={isBond ? t('Net Tahsilat') : t('Tahmini net gelir')}
                  value={fmtWithCcy(quantityModeTotal.netIncome, currency)}
                  highlight
                />
              </>
            )}
          </>
        )}
      </div>
    );
  }

  return null;
}

// ── yardımcı satır ──────────────────────────────────────────────────────────────

function SummaryRow({ label, value, highlight = false, dim = false }) {
  return (
    <div className="flex justify-between items-center">
      <span className="text-[#434653]">{label}</span>
      <span className={`font-bold ${
        highlight ? 'text-[#1a1b22] text-sm' : dim ? 'text-[#747684]' : 'text-[#434653]'
      }`}>
        {value}
      </span>
    </div>
  );
}
