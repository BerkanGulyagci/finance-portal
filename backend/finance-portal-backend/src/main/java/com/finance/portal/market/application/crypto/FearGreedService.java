package com.finance.portal.market.application.crypto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.crypto.model.FearGreedPoint;
import com.finance.portal.market.infrastructure.external.crypto.FearGreedClient;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Crypto Fear &amp; Greed Index servisi — alternative.me proxy'si.
 *
 * <p>Endeks günde bir kez güncellenir; bu yüzden cache TTL ~2 saattir (CacheConfig
 * {@code market.feargreed}). Kaynak (alternative.me) çökerse {@link LastKnownGoodCache}
 * son başarılı seriyi servis eder (stale-if-error) — kullanıcı boş/hata değil "biraz eski"
 * veri görür.</p>
 *
 * <p>Piyasa-geneli tek seri (coin başına değil); tek parametre {@code days}.</p>
 */
@Service
public class FearGreedService {

    private static final Logger log = LoggerFactory.getLogger(FearGreedService.class);

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;
    private static final int DEFAULT_DAYS = 90;

    /** Endeks günlük güncellenir; kaynak çökerse en fazla bu kadar eski seri gösterilir. */
    private static final Duration FEARGREED_LKG_TTL = Duration.ofDays(7);

    private final FearGreedClient fearGreedClient;
    private final LastKnownGoodCache lkg;

    public FearGreedService(FearGreedClient fearGreedClient, LastKnownGoodCache lkg) {
        this.fearGreedClient = fearGreedClient;
        this.lkg = lkg;
    }

    /**
     * Son {@code days} günlük Fear &amp; Greed serisini döner (eskiden yeniye değil,
     * alternative.me sırasıyla — en yeni önce). {@code days} [1,365]'e kıstırılır;
     * geçersiz/0 ise {@value #DEFAULT_DAYS} kullanılır.
     */
    @Cacheable(cacheNames = "market.feargreed", key = "#days")
    @WithSpan("FearGreedService.getFearGreed")
    public List<FearGreedPoint> getFearGreed(@SpanAttribute("crypto.feargreed.days") int days) {
        int normalized = normalizeDays(days);
        log.debug("Fear & Greed isteği days={} (normalized={})", days, normalized);
        // alternative.me çökerse/boş dönerse son başarılı seriyi servis et.
        return lkg.resilient("crypto.feargreed:" + normalized,
                FEARGREED_LKG_TTL, new TypeReference<List<FearGreedPoint>>() {},
                () -> fearGreedClient.fetchFearGreed(normalized));
    }

    private static int normalizeDays(int days) {
        if (days < MIN_DAYS) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }
}
