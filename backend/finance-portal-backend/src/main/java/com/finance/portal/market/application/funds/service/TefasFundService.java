package com.finance.portal.market.application.funds.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.funds.model.FundPriceHistoryPoint;
import com.finance.portal.market.application.funds.model.FundPriceHistoryResponse;
import com.finance.portal.market.infrastructure.external.tefas.TefasFundClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * TEFAS resmi API'sinden fon birim pay fiyatı tarihsel serisi (grafik için).
 * Künye (valör/komisyon vb.) Rasyonet'ten gelmeye devam eder; bu servis yalnız fiyat geçmişi.
 */
@Service
public class TefasFundService {

    /** Geçmiş-NAV (yavaş/tarihsel) verisi için LKG ömrü — kaynak çökerse en fazla bu kadar eski seri gösterilir. */
    private static final Duration HISTORY_LKG_TTL = Duration.ofDays(14);

    private final TefasFundClient client;
    private final LastKnownGoodCache lkg;

    public TefasFundService(TefasFundClient client, LastKnownGoodCache lkg) {
        this.client = client;
        this.lkg = lkg;
    }

    /**
     * Verilen aralık etiketi için fon fiyat geçmişi. Boş seri cache'lenmez (Rasyonet fallback için).
     *
     * @param code    fon kodu (AFA)
     * @param range   1W | 1M | 3M | 6M | 1Y | 3Y | 5Y
     * @param fonTipi YAT (TEFAS yatırım fonu) | EMK (emeklilik)
     */
    @Cacheable(
            cacheNames = "market.tefas.history",
            key = "#code + ':' + #range + ':' + #fonTipi",
            unless = "#result == null || #result.getPoints().isEmpty()"
    )
    public FundPriceHistoryResponse getPriceHistory(String code, String range, String fonTipi) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(rangeDays(range));
        // TEFAS çökerse/boş dönerse son başarılı seriyi servis et (kullanıcı boş grafik yerine eski seri görür).
        List<FundPriceHistoryPoint> points = lkg.resilient(
                "tefas.history:" + code + ":" + range + ":" + fonTipi, HISTORY_LKG_TTL,
                new TypeReference<List<FundPriceHistoryPoint>>() {},
                () -> client.fetchPriceHistory(code, fonTipi, from, to));
        return new FundPriceHistoryResponse(code, range, "TEFAS", points);
    }

    /**
     * Belirli bir tarih aralığı için fon fiyat geçmişi (işlem modalı price-at için).
     * Modal sadece ~10 günlük pencere çektiğinden tek TEFAS isteği olur → throttle riski yok,
     * 5 yıla kadar güvenilir. Boş seri cache'lenmez (Rasyonet fallback için).
     */
    @Cacheable(
            cacheNames = "market.tefas.history",
            key = "'range:' + #code + ':' + #fonTipi + ':' + #from + ':' + #to",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<FundPriceHistoryPoint> getPriceHistoryRange(String code, String fonTipi,
                                                            java.time.LocalDate from, java.time.LocalDate to) {
        // TEFAS çökerse/boş dönerse son başarılı pencereyi servis et (price-at fallback'i sağlam kalır).
        return lkg.resilient(
                "tefas.history.range:" + code + ":" + fonTipi + ":" + from + ":" + to, HISTORY_LKG_TTL,
                new TypeReference<List<FundPriceHistoryPoint>>() {},
                () -> client.fetchPriceHistory(code, fonTipi, from, to));
    }

    /** Aralık etiketi → gün sayısı (küçük tampon ile). */
    private static long rangeDays(String range) {
        if (range == null) return 372;
        return switch (range.trim().toUpperCase()) {
            case "1W" -> 9;
            case "1M" -> 33;
            case "3M" -> 95;
            case "6M" -> 188;
            case "1Y" -> 372;
            case "3Y" -> 1100;
            case "5Y" -> 1830;
            default -> 372;
        };
    }
}
