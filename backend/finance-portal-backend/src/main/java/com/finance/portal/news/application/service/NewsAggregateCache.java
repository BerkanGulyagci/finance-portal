package com.finance.portal.news.application.service;

import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.port.ArticleContentPort;
import com.finance.portal.news.application.port.NewsProvider;
import com.finance.portal.news.domain.NewsCategory;
import com.finance.portal.news.domain.NewsClassifier;
import com.finance.portal.news.domain.NewsDateUtil;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tüm etkin sağlayıcılardan haberleri toplayıp normalize eden ve BELLEKTE cache'leyen katman.
 * In-memory cache (kısa TTL'li paylaşımlı Redis cache yerine) ücretsiz API kotalarını korur:
 * sağlayıcılar yalnızca {@code MAX_AGE_MS} aşıldığında veya scheduler tetiklediğinde çağrılır.
 */
@Service
public class NewsAggregateCache {

    private static final Logger log = LoggerFactory.getLogger(NewsAggregateCache.class);
    private static final int MAX_ARTICLES = 700;
    private static final long MAX_AGE_MS = 25 * 60 * 1000L; // 25 dk
    private static final int MAX_IMG_ENRICH = 60;           // görseli olmayan en fazla N maddeyi zenginleştir
    private static final long IMG_BUDGET_SEC = 7;           // og:image için toplam süre bütçesi

    private final List<NewsProvider> providers;
    private final ArticleContentPort contentPort;

    private volatile List<NewsArticle> cached = List.of();
    private volatile long lastRefresh = 0L;

    public NewsAggregateCache(List<NewsProvider> providers, ArticleContentPort contentPort) {
        this.providers = providers;
        this.contentPort = contentPort;
    }

    /** Bayatladıysa yeniler; toplanmış/kategorilenmiş/tekilleştirilmiş, tarihe göre sıralı liste döner. */
    public List<NewsArticle> getAll() {
        ensureFresh();
        return cached;
    }

    private synchronized void ensureFresh() {
        if (cached.isEmpty() || System.currentTimeMillis() - lastRefresh > MAX_AGE_MS) {
            doRefresh();
        }
    }

    /** Scheduler tarafından zorunlu yenileme. */
    public synchronized void refresh() {
        doRefresh();
    }

    @WithSpan("NewsAggregateCache.refresh")
    private void doRefresh() {
        List<NewsArticle> collected = new ArrayList<>();
        for (NewsProvider p : providers) {
            if (!p.isEnabled()) {
                continue;
            }
            try {
                List<NewsArticle> items = p.fetch();
                if (items != null) {
                    collected.addAll(items);
                }
            } catch (Exception e) {
                log.warn("News provider '{}' failed: {}", p.id(), e.getMessage());
            }
        }

        Map<String, NewsArticle> unique = new LinkedHashMap<>();
        for (NewsArticle a : collected) {
            if (a == null || a.getUrl() == null || a.getTitle() == null) {
                continue;
            }
            // Kategori-özel feed (CRYPTO/FX/STOCKS/...) → kaynağın kategorisine güven.
            // Genel ekonomi feed'i (veya kategorisiz) → keyword classifier ile alt kategoriye ayır.
            NewsCategory feedCat = NewsCategory.fromString(a.getCategory());
            NewsCategory category = (feedCat != null && feedCat != NewsCategory.ECONOMY)
                    ? feedCat
                    : NewsClassifier.classify(a.getTitle(), a.getDescription(), NewsCategory.ECONOMY);
            unique.putIfAbsent(dedupeKey(a.getUrl()), a.withCategory(category.name()));
        }

        List<NewsArticle> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparingLong(
                (NewsArticle a) -> NewsDateUtil.toEpochMillis(a.getPublishedAt())).reversed());
        if (result.size() > MAX_ARTICLES) {
            result = new ArrayList<>(result.subList(0, MAX_ARTICLES));
        }

        enrichMissingImages(result);

        this.cached = List.copyOf(result);
        this.lastRefresh = System.currentTimeMillis();
        log.info("News aggregate refreshed: {} articles from {} providers", cached.size(), providers.size());
    }

    /** Görseli olmayan ilk N maddeyi paralel og:image çekerek zenginleştirir (toplam süre bütçeli). */
    private void enrichMissingImages(List<NewsArticle> articles) {
        List<Integer> targets = new ArrayList<>();
        for (int i = 0; i < articles.size() && targets.size() < MAX_IMG_ENRICH; i++) {
            NewsArticle a = articles.get(i);
            if ((a.getImageUrl() == null || a.getImageUrl().isBlank()) && a.getUrl() != null) {
                targets.add(i);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(12, targets.size()));
        Map<Integer, String> found = new ConcurrentHashMap<>();
        try {
            for (int idx : targets) {
                final int i = idx;
                pool.submit(() -> {
                    String img = contentPort.fetchOgImage(articles.get(i).getUrl());
                    if (img != null && !img.isBlank()) {
                        found.put(i, img);
                    }
                });
            }
            pool.shutdown();
            pool.awaitTermination(IMG_BUDGET_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        found.forEach((idx, img) -> articles.set(idx, articles.get(idx).withImageUrl(img)));
        if (!found.isEmpty()) {
            log.info("Enriched {} image-less articles with og:image", found.size());
        }
    }

    private static String dedupeKey(String url) {
        String u = url.trim().toLowerCase();
        int q = u.indexOf('?');
        if (q > 0) {
            u = u.substring(0, q);
        }
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }
}
