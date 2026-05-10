package com.finance.portal.portfolio.domain;

/**
 * Portföy tipi.
 * <p>
 * HOLDINGS  → Gerçek portföy: BUY/SELL işlemleri, maliyet, kar/zarar hesabı.
 * WATCHLIST → İzleme listesi: Sadece sembol takibi, fiyat bilgisi market servislerinden anlık çekilir.
 */
public enum PortfolioType {
    HOLDINGS,
    WATCHLIST
}
