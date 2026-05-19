package com.finance.portal.portfolio.application.port;

import com.finance.portal.common.domain.AssetType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * Portföy performans hesabı için günlük kapanış fiyat serisi.
 */
public interface PortfolioHistoricalPricePort {

    /**
     * {@code from}–{@code to} aralığında günlük kapanış fiyatları (tarih → fiyat).
     * Boş veya bulunamayan seri için {@link Optional#empty()}.
     */
    Optional<NavigableMap<LocalDate, BigDecimal>> fetchDailyClosePrices(
            AssetType assetType,
            String symbol,
            LocalDate from,
            LocalDate to);
}
