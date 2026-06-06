package com.finance.portal.market.application.crypto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.crypto.model.FearGreedPoint;
import com.finance.portal.market.application.crypto.model.FearGreedSummary;
import com.finance.portal.market.application.crypto.model.FearGreedSummary.FearGreedSnapshot;
import com.finance.portal.market.infrastructure.external.crypto.FearGreedClient;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/** Crypto Fear &amp; Greed Index — alternative.me proxy'si (piyasa-geneli, cache + LKG). */
@Service
public class FearGreedService {

    private static final Logger log = LoggerFactory.getLogger(FearGreedService.class);

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;
    private static final int DEFAULT_DAYS = 90;

    private static final int SUMMARY_FETCH_DAYS = 365;   // yıllık min/max için tam yıl
    private static final int IDX_YESTERDAY = 1;
    private static final int IDX_LAST_WEEK = 7;
    private static final int IDX_LAST_MONTH = 30;

    private static final Duration FEARGREED_LKG_TTL = Duration.ofDays(7);

    private final FearGreedClient fearGreedClient;
    private final LastKnownGoodCache lkg;

    public FearGreedService(FearGreedClient fearGreedClient, LastKnownGoodCache lkg) {
        this.fearGreedClient = fearGreedClient;
        this.lkg = lkg;
    }

    /** Son {@code days} günlük seri (en yeni önce). */
    @Cacheable(cacheNames = "market.feargreed", key = "#days")
    @WithSpan("FearGreedService.getFearGreed")
    public List<FearGreedPoint> getFearGreed(@SpanAttribute("crypto.feargreed.days") int days) {
        int normalized = normalizeDays(days);
        return lkg.resilient("crypto.feargreed:" + normalized,
                FEARGREED_LKG_TTL, new TypeReference<List<FearGreedPoint>>() {},
                () -> fearGreedClient.fetchFearGreed(normalized));
    }

    /** Gauge paneli için zengin özet: güncel + dün/hafta/ay kıyas + yıllık min/max + son {@code days} seri. */
    @Cacheable(cacheNames = "market.feargreed", key = "'summary:' + #days")
    @WithSpan("FearGreedService.getSummary")
    public FearGreedSummary getSummary(@SpanAttribute("crypto.feargreed.days") int days) {
        int seriesDays = normalizeDays(days);
        return lkg.resilient("crypto.feargreed.summary:" + seriesDays,
                FEARGREED_LKG_TTL, new TypeReference<FearGreedSummary>() {},
                () -> buildSummary(seriesDays));
    }

    private FearGreedSummary buildSummary(int seriesDays) {
        List<FearGreedPoint> all = fearGreedClient.fetchFearGreed(SUMMARY_FETCH_DAYS);
        if (all == null || all.isEmpty()) {
            throw new ExternalApiException("Fear & Greed summary: kaynak boş seri döndü");
        }

        FearGreedSnapshot current = FearGreedSnapshot.of(all.get(0));
        FearGreedSnapshot yesterday = snapshotAt(all, IDX_YESTERDAY);
        FearGreedSnapshot lastWeek = snapshotAt(all, IDX_LAST_WEEK);
        FearGreedSnapshot lastMonth = snapshotAt(all, IDX_LAST_MONTH);

        FearGreedPoint high = all.get(0);
        FearGreedPoint low = all.get(0);
        for (FearGreedPoint p : all) {
            if (p.value() > high.value()) {
                high = p;
            }
            if (p.value() < low.value()) {
                low = p;
            }
        }

        List<FearGreedPoint> series = all.subList(0, Math.min(seriesDays, all.size()));

        return new FearGreedSummary(
                current,
                yesterday,
                lastWeek,
                lastMonth,
                FearGreedSnapshot.of(high),
                FearGreedSnapshot.of(low),
                List.copyOf(series));
    }

    private static FearGreedSnapshot snapshotAt(List<FearGreedPoint> all, int index) {
        if (index < 0 || index >= all.size()) {
            return null;
        }
        return FearGreedSnapshot.ofValueOnly(all.get(index));
    }

    private static int normalizeDays(int days) {
        if (days < MIN_DAYS) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }
}
