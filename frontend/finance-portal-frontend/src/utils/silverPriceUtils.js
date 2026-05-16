/**
 * Gram gümüş (TRY) — güncel fiyat = son kapanış (detay grafik ile aynı).
 * Öncelik: history son close → spot.silverGramCloseTry → spot.silverGramTry
 */
export function pickSilverGramCloseTry(spot, historyPoints) {
  if (Array.isArray(historyPoints) && historyPoints.length > 0) {
    const last = historyPoints[historyPoints.length - 1];
    const close = last?.close != null ? parseFloat(last.close) : NaN;
    if (Number.isFinite(close) && close > 0) {
      return close;
    }
  }
  if (!spot) return null;
  const gramClose = spot.silverGramCloseTry != null ? parseFloat(spot.silverGramCloseTry) : NaN;
  if (Number.isFinite(gramClose) && gramClose > 0) return gramClose;
  const wa = spot.silverGramTry != null ? parseFloat(spot.silverGramTry) : NaN;
  return Number.isFinite(wa) && wa > 0 ? wa : null;
}
