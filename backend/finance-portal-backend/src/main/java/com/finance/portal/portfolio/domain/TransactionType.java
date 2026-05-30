package com.finance.portal.portfolio.domain;

public enum TransactionType {

    BUY,
    SELL,

    /**
     * Periyodik gelir kaydı — DİBS/Kira Sertifikası kupon ödemesi (manuel).
     * <p>Saklama konvansiyonu: {@code quantity} = TL kupon tutarı, {@code price} = 1.
     * <p>{@code PortfolioHoldingsBuilder} bu tipi açık pozisyondan bağımsız ele alır:
     * realized gelire eklenir, openQty/cost dokunulmaz.
     */
    COUPON_INCOME
}

