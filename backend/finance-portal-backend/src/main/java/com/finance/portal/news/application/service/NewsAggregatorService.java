package com.finance.portal.news.application.service;

import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.model.NewsDetail;
import com.finance.portal.news.application.model.NewsQueryResult;
import com.finance.portal.news.application.port.TranslationPort;
import com.finance.portal.news.application.port.UserHoldingsPort;
import com.finance.portal.news.domain.NewsCategory;
import com.finance.portal.news.domain.NewsDateUtil;
import com.finance.portal.news.domain.NewsIdUtil;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Cache'li toplu haber listesi üzerinde filtreleme (kategori/kaynak/arama/tarih),
 * sayfalama ve id ile detay/ilgili haber bulma.
 */
@Service
public class NewsAggregatorService {

    private static final int RELATED_LIMIT = 6;
    private static final long TRANSLATE_BUDGET_SEC = 9; // liste çevirisi için toplam süre bütçesi
    private static final Locale TR = Locale.forLanguageTag("tr");

    private final NewsAggregateCache cache;
    private final com.finance.portal.news.application.port.ArticleContentPort contentPort;
    private final TranslationPort translationPort;
    private final UserHoldingsPort holdingsPort;

    public NewsAggregatorService(NewsAggregateCache cache,
                                 com.finance.portal.news.application.port.ArticleContentPort contentPort,
                                 TranslationPort translationPort,
                                 UserHoldingsPort holdingsPort) {
        this.cache = cache;
        this.contentPort = contentPort;
        this.translationPort = translationPort;
        this.holdingsPort = holdingsPort;
    }

    @WithSpan("NewsAggregatorService.query")
    public NewsQueryResult query(String category, String source, String keyword, String region,
                                 Long fromMillis, Long toMillis, int page, int pageSize, String lang) {
        // Bölge (Türkiye/Global) filtresi — facet'ler bunun ÜZERİNDEN hesaplanır,
        // böylece "Türkiye" seçiliyken kategori/kaynak sayıları yalnız TR haberlerini yansıtır.
        List<NewsArticle> base = cache.getAll().stream()
                .filter(a -> matchesRegion(a, region))
                .collect(Collectors.toList());

        List<String> sources = base.stream()
                .map(NewsArticle::getSource)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        Map<String, Long> categoryCounts = new TreeMap<>();
        for (NewsArticle a : base) {
            if (a.getCategory() != null) {
                categoryCounts.merge(a.getCategory(), 1L, Long::sum);
            }
        }

        NewsCategory cat = NewsCategory.fromString(category);
        String kw = keyword != null && !keyword.isBlank()
                ? keyword.trim().toLowerCase(Locale.forLanguageTag("tr")) : null;

        List<NewsArticle> filtered = base.stream()
                .filter(a -> cat == null || cat.name().equals(a.getCategory()))
                .filter(a -> source == null || source.isBlank() || matchesSource(a, source))
                .filter(a -> kw == null || matchesKeyword(a, kw))
                .filter(a -> withinRange(a, fromMillis, toMillis))
                .collect(Collectors.toList());

        long total = filtered.size();
        int size = Math.max(1, Math.min(pageSize, 60));
        int pageIndex = Math.max(1, page);
        int totalPages = (int) Math.ceil((double) total / size);
        int fromIdx = Math.min((pageIndex - 1) * size, filtered.size());
        int toIdx = Math.min(fromIdx + size, filtered.size());
        List<NewsArticle> pageItems = filtered.subList(fromIdx, toIdx);

        // UI dili makale dilinden farklıysa başlık+özeti çevir (paralel + süre bütçeli, best-effort).
        List<NewsArticle> outItems = translatePage(pageItems, lang);

        return new NewsQueryResult(outItems, pageIndex, size, total, totalPages, sources, categoryCounts);
    }

    @WithSpan("NewsAggregatorService.getById")
    public NewsDetail getById(String id, String lang) {
        if (id == null || id.isBlank()) {
            return null;
        }
        List<NewsArticle> all = cache.getAll();
        NewsArticle match = all.stream()
                .filter(a -> id.equals(NewsIdUtil.idFor(a.getUrl())))
                .findFirst()
                .orElse(null);
        if (match == null) {
            return null;
        }
        String matchUrl = match.getUrl();
        List<NewsArticle> related = all.stream()
                .filter(a -> match.getCategory() != null && match.getCategory().equals(a.getCategory()))
                .filter(a -> !matchUrl.equals(a.getUrl()))
                .limit(RELATED_LIMIT)
                .collect(Collectors.toList());
        // 1) RSS content:encoded varsa (kriptofoni/ekonomimtv/Patronlar/Hürriyet) onu kullan — scrape etmeden.
        // 2) Sağlayıcı içeriği yoksa makale sayfasını scrape et — ANCAK Finnhub hariç: global aggregator
        //    URL'leri (forexlive/investinglive vb.) scrape'te alakasız bloklar veriyor; özeti (description) yeterli.
        String content = null;
        String raw = match.getContent();
        if (raw != null && !raw.isBlank()) {
            content = contentPort.extractFromHtml(raw);
        } else if (!"Finnhub".equals(match.getSource())) {
            content = contentPort.fetchContent(matchUrl);
        }

        // UI dili makale dilinden farklıysa başlık+özet+içerik (ve ilgili başlıkları) çevir — best-effort.
        NewsArticle out = match;
        String tgt = norm(lang);
        if (!tgt.isEmpty() && match.getLanguage() != null && !tgt.equals(norm(match.getLanguage()))) {
            String src = match.getLanguage();
            out = match.withText(
                    translationPort.translate(match.getTitle(), src, tgt),
                    translationPort.translate(match.getDescription(), src, tgt));
            if (content != null) {
                content = translationPort.translate(content, src, tgt);
            }
            related = translatePage(related, tgt);
        }
        return new NewsDetail(out, related, content);
    }

    /**
     * Kullanıcının portföyüne göre "Size Özel" haberler: tuttuğu sembol/isim geçen (yüksek puan)
     * veya varlık türüne uyan kategorideki (orta puan) haberler; puana + güncelliğe göre sıralı.
     */
    // Sonar S2259 false-positive: bu metotta matched/safeMatched Stream#toList'ten
    // gelir → asla null değildir; Objects.requireNonNullElse ile bile garantilense
    // Sonar dataflow tanımıyor. Method-level suppress.
    @SuppressWarnings("java:S2259")
    @WithSpan("NewsAggregatorService.forUser")
    public NewsQueryResult forUser(String userId, String lang, int limit) {
        int size = Math.max(1, Math.min(limit, 30));
        List<UserHoldingsPort.Holding> holdings = holdingsPort.holdingsForUser(userId);
        if (holdings.isEmpty()) {
            return new NewsQueryResult(List.of(), 1, size, 0L, 0, List.of(), Map.of());
        }

        Set<String> keywords = new java.util.LinkedHashSet<>();
        Set<NewsCategory> cats = EnumSet.noneOf(NewsCategory.class);
        for (UserHoldingsPort.Holding h : holdings) {
            if (h.symbol() != null && h.symbol().trim().length() >= 3) {
                keywords.add(h.symbol().trim().toLowerCase(TR));
            }
            if (h.name() != null && h.name().trim().length() >= 4) {
                keywords.add(h.name().trim().toLowerCase(TR));
            }
            NewsCategory c = assetTypeToCategory(h.assetType());
            if (c != null) {
                cats.add(c);
            }
        }
        Pattern kwPattern = buildKeywordPattern(keywords);

        List<NewsArticle> matched = cache.getAll().stream()
                .map(a -> Map.entry(a, scoreFor(a, kwPattern, cats)))
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator
                        .comparingInt((Map.Entry<NewsArticle, Integer> e) -> e.getValue()).reversed()
                        .thenComparing(e -> NewsDateUtil.toEpochMillis(e.getKey().getPublishedAt()),
                                Comparator.reverseOrder()))
                .limit(size)
                .map(Map.Entry::getKey)
                .toList();

        List<NewsArticle> out = translatePage(matched, lang);
        // int → long widening implicit; explicit cast gereksiz (S1905).
        return new NewsQueryResult(out, 1, size, matched.size(), 1, List.of(), Map.of());
    }

    private static int scoreFor(NewsArticle a, Pattern kwPattern, Set<NewsCategory> cats) {
        int score = 0;
        if (kwPattern != null) {
            String text = ((a.getTitle() != null ? a.getTitle() : "") + " "
                    + (a.getDescription() != null ? a.getDescription() : "")).toLowerCase(TR);
            if (kwPattern.matcher(text).find()) {
                score += 10;
            }
        }
        NewsCategory c = NewsCategory.fromString(a.getCategory());
        if (c != null && cats.contains(c)) {
            score += 3;
        }
        return score;
    }

    private static Pattern buildKeywordPattern(Set<String> keywords) {
        if (keywords.isEmpty()) {
            return null;
        }
        String alt = keywords.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        return Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + alt + ")(?![\\p{L}\\p{N}])",
                Pattern.UNICODE_CHARACTER_CLASS);
    }

    private static NewsCategory assetTypeToCategory(String assetType) {
        if (assetType == null) {
            return null;
        }
        return switch (assetType.toUpperCase(Locale.ROOT)) {
            case "STOCK", "FUND" -> NewsCategory.STOCKS;
            case "CRYPTO" -> NewsCategory.CRYPTO;
            case "FX" -> NewsCategory.FX;
            case "GOLD", "COMMODITY", "FUTURE" -> NewsCategory.COMMODITIES;
            case "BOND" -> NewsCategory.BONDS;
            default -> null;
        };
    }

    /** Kategori facet'lerini etiketleriyle birlikte sıralı döndürür (UI için). */
    public Map<String, String> categoryLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (NewsCategory c : NewsCategory.values()) {
            labels.put(c.name(), c.getLabel());
        }
        return labels;
    }

    /** Kalemlerin başlık+özetini hedef dile çevirir (paralel, süre bütçeli, best-effort). Aynı dildekiler atlanır. */
    private List<NewsArticle> translatePage(List<NewsArticle> items, String lang) {
        String tgt = norm(lang);
        if (tgt.isEmpty() || items == null || items.isEmpty()) {
            return items;
        }
        NewsArticle[] arr = items.toArray(new NewsArticle[0]);
        List<Integer> targets = new ArrayList<>();
        for (int i = 0; i < arr.length; i++) {
            String al = arr[i].getLanguage();
            if (al != null && !tgt.equals(norm(al))) {
                targets.add(i);
            }
        }
        if (targets.isEmpty()) {
            return items;
        }
        // Java 21: ExecutorService AutoCloseable — close() submitted task'lerin bitmesini bekler
        try (ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, targets.size()))) {
            for (int idx : targets) {
                final int i = idx;
                pool.submit(() -> {
                    NewsArticle a = arr[i];
                    arr[i] = a.withText(
                            translationPort.translate(a.getTitle(), a.getLanguage(), tgt),
                            translationPort.translate(a.getDescription(), a.getLanguage(), tgt));
                });
            }
            pool.shutdown();
            if (!pool.awaitTermination(TRANSLATE_BUDGET_SEC, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Arrays.asList(arr);
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesRegion(NewsArticle a, String region) {
        if (region == null || region.isBlank() || "ALL".equalsIgnoreCase(region)) {
            return true;
        }
        String lang = a.getLanguage();
        if ("TR".equalsIgnoreCase(region)) {
            return "tr".equalsIgnoreCase(lang);
        }
        if ("GLOBAL".equalsIgnoreCase(region)) {
            return lang != null && !"tr".equalsIgnoreCase(lang);
        }
        return true;
    }

    /** Kaynak filtresi: tam ad ("Kriptofoni") VEYA grup öneki ("Kriptofoni " → Kriptofoni Altcoin/Bitcoin…). */
    private static boolean matchesSource(NewsArticle a, String source) {
        String s = a.getSource();
        return s != null && (s.equals(source) || s.startsWith(source + " "));
    }

    private static boolean matchesKeyword(NewsArticle a, String kw) {
        String text = ((a.getTitle() != null ? a.getTitle() : "") + " "
                + (a.getDescription() != null ? a.getDescription() : ""))
                .toLowerCase(Locale.forLanguageTag("tr"));
        return text.contains(kw);
    }

    private static boolean withinRange(NewsArticle a, Long fromMillis, Long toMillis) {
        if (fromMillis == null && toMillis == null) {
            return true;
        }
        long t = NewsDateUtil.toEpochMillis(a.getPublishedAt());
        if (t == 0L) {
            return true; // tarihi parse edilemeyenleri eleme
        }
        if (fromMillis != null && t < fromMillis) {
            return false;
        }
        return toMillis == null || t <= toMillis;
    }

    // Comparator referansı (gelecekte ikincil sıralama gerekirse)
    @SuppressWarnings("unused")
    private static final Comparator<NewsArticle> BY_DATE_DESC =
            Comparator.comparingLong((NewsArticle a) -> NewsDateUtil.toEpochMillis(a.getPublishedAt())).reversed();
}
