package com.finance.portal.news.application.service;

import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.model.NewsDetail;
import com.finance.portal.news.application.model.NewsQueryResult;
import com.finance.portal.news.application.port.ArticleContentPort;
import com.finance.portal.news.application.port.TranslationPort;
import com.finance.portal.news.application.port.UserHoldingsPort;
import com.finance.portal.news.domain.NewsCategory;
import com.finance.portal.news.domain.NewsIdUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewsAggregatorServiceTest {

    @Mock
    private NewsAggregateCache cache;
    @Mock
    private ArticleContentPort contentPort;
    @Mock
    private TranslationPort translationPort;
    @Mock
    private UserHoldingsPort holdingsPort;
    @Mock
    private NewsAssetMatcher assetMatcher;

    private NewsAggregatorService service;

    @BeforeEach
    void setUp() {
        service = new NewsAggregatorService(cache, contentPort, translationPort, holdingsPort, assetMatcher);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static NewsArticle article(String title, String desc, String url, String publishedAt,
                                       String source, String category, String language) {
        return new NewsArticle(title, desc, url, null, publishedAt, source, null, category, language, null);
    }

    private List<NewsArticle> sampleArticles() {
        List<NewsArticle> list = new ArrayList<>();
        // TR economy article
        list.add(article("Borsa yükseldi", "BIST 100 endeksi", "https://a.com/1",
                "2026-05-24T12:00:00Z", "Hürriyet", NewsCategory.STOCKS.name(), "tr"));
        // Global crypto article
        list.add(article("Bitcoin rallies", "BTC price up", "https://b.com/2",
                "2026-05-25T12:00:00Z", "Finnhub", NewsCategory.CRYPTO.name(), "en"));
        // TR fx article
        list.add(article("Dolar düştü", "USD/TRY haberi", "https://c.com/3",
                "2026-05-23T12:00:00Z", "Bloomberg HT", NewsCategory.FX.name(), "tr"));
        // Global stocks article with same source-prefix grouping
        list.add(article("Nasdaq update", "tech stocks", "https://d.com/4",
                "2026-05-22T12:00:00Z", "Kriptofoni Altcoin", NewsCategory.STOCKS.name(), "en"));
        return list;
    }

    // ── query: filtering / facets / paging ─────────────────────────────────────

    @Test
    @DisplayName("query: bölge=ALL → tüm haberler; kaynak facet'leri sıralı/distinct, kategori sayıları")
    void queryAllRegionFacets() {
        when(cache.getAll()).thenReturn(sampleArticles());

        NewsQueryResult r = service.query(null, null, null, "ALL", null, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(4);
        assertThat(r.items()).hasSize(4);
        assertThat(r.page()).isEqualTo(1);
        // sources sorted case-insensitive, distinct
        assertThat(r.sources()).containsExactly("Bloomberg HT", "Finnhub", "Hürriyet", "Kriptofoni Altcoin");
        // category counts
        assertThat(r.categoryCounts()).containsEntry(NewsCategory.STOCKS.name(), 2L);
        assertThat(r.categoryCounts()).containsEntry(NewsCategory.CRYPTO.name(), 1L);
        assertThat(r.categoryCounts()).containsEntry(NewsCategory.FX.name(), 1L);
    }

    @Test
    @DisplayName("query: bölge=TR → yalnızca tr dilli haberler")
    void queryRegionTr() {
        when(cache.getAll()).thenReturn(sampleArticles());

        NewsQueryResult r = service.query(null, null, null, "TR", null, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(2);
        assertThat(r.items()).allMatch(a -> "tr".equalsIgnoreCase(a.getLanguage()));
    }

    @Test
    @DisplayName("query: bölge=GLOBAL → tr olmayan haberler")
    void queryRegionGlobal() {
        when(cache.getAll()).thenReturn(sampleArticles());

        NewsQueryResult r = service.query(null, null, null, "GLOBAL", null, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(2);
        assertThat(r.items()).allMatch(a -> !"tr".equalsIgnoreCase(a.getLanguage()));
    }

    @Test
    @DisplayName("query: kategori filtresi")
    void queryCategoryFilter() {
        when(cache.getAll()).thenReturn(sampleArticles());

        NewsQueryResult r = service.query("STOCKS", null, null, "ALL", null, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(2);
        assertThat(r.items()).allMatch(a -> NewsCategory.STOCKS.name().equals(a.getCategory()));
    }

    @Test
    @DisplayName("query: bilinmeyen kategori → filtre uygulanmaz (tümü)")
    void queryUnknownCategory() {
        when(cache.getAll()).thenReturn(sampleArticles());

        NewsQueryResult r = service.query("NOPE", null, null, "ALL", null, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("query: kaynak filtresi — tam ad ve grup öneki eşleşmesi")
    void querySourceFilterExactAndPrefix() {
        when(cache.getAll()).thenReturn(sampleArticles());

        NewsQueryResult exact = service.query(null, "Hürriyet", null, "ALL", null, null, 1, 20, null);
        assertThat(exact.totalElements()).isEqualTo(1);

        // prefix match: "Kriptofoni" matches "Kriptofoni Altcoin"
        NewsQueryResult prefix = service.query(null, "Kriptofoni", null, "ALL", null, null, 1, 20, null);
        assertThat(prefix.totalElements()).isEqualTo(1);
        assertThat(prefix.items().get(0).getSource()).isEqualTo("Kriptofoni Altcoin");
    }

    @Test
    @DisplayName("query: keyword araması başlık+özet içinde (case-insensitive)")
    void queryKeyword() {
        when(cache.getAll()).thenReturn(sampleArticles());

        NewsQueryResult r = service.query(null, null, "bitcoin", "ALL", null, null, 1, 20, null);
        assertThat(r.totalElements()).isEqualTo(1);
        assertThat(r.items().get(0).getTitle()).isEqualTo("Bitcoin rallies");

        // blank keyword → no filter
        NewsQueryResult blank = service.query(null, null, "   ", "ALL", null, null, 1, 20, null);
        assertThat(blank.totalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("query: tarih aralığı (from/to millis) filtreler")
    void queryDateRange() {
        when(cache.getAll()).thenReturn(sampleArticles());

        long from = java.time.Instant.parse("2026-05-24T00:00:00Z").toEpochMilli();
        long to = java.time.Instant.parse("2026-05-25T23:59:59Z").toEpochMilli();

        NewsQueryResult r = service.query(null, null, null, "ALL", from, to, 1, 20, null);
        // articles on 05-24 and 05-25 only
        assertThat(r.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("query: sayfalama — pageSize clamp (max 60, min 1) ve totalPages")
    void queryPaging() {
        when(cache.getAll()).thenReturn(sampleArticles());

        NewsQueryResult r = service.query(null, null, null, "ALL", null, null, 1, 2, null);
        assertThat(r.pageSize()).isEqualTo(2);
        assertThat(r.items()).hasSize(2);
        assertThat(r.totalPages()).isEqualTo(2);

        // page 2
        NewsQueryResult page2 = service.query(null, null, null, "ALL", null, null, 2, 2, null);
        assertThat(page2.items()).hasSize(2);
        assertThat(page2.page()).isEqualTo(2);

        // page beyond range → empty
        NewsQueryResult page99 = service.query(null, null, null, "ALL", null, null, 99, 2, null);
        assertThat(page99.items()).isEmpty();

        // pageSize > 60 clamped to 60
        NewsQueryResult big = service.query(null, null, null, "ALL", null, null, 1, 999, null);
        assertThat(big.pageSize()).isEqualTo(60);

        // pageSize 0 → clamped to 1
        NewsQueryResult zero = service.query(null, null, null, "ALL", null, null, 1, 0, null);
        assertThat(zero.pageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("query: empty cache → boş sonuç, facet'ler boş")
    void queryEmptyCache() {
        when(cache.getAll()).thenReturn(List.of());

        NewsQueryResult r = service.query(null, null, null, "ALL", null, null, 1, 20, null);
        assertThat(r.totalElements()).isZero();
        assertThat(r.items()).isEmpty();
        assertThat(r.sources()).isEmpty();
        assertThat(r.categoryCounts()).isEmpty();
    }

    @Test
    @DisplayName("query: UI dili tr → en haberlerin başlık/özeti çevrilir")
    void queryTranslatesForeignLanguage() {
        when(cache.getAll()).thenReturn(sampleArticles());
        when(translationPort.translate(anyString(), anyString(), eq("tr")))
                .thenAnswer(inv -> "TR:" + inv.getArgument(0));

        NewsQueryResult r = service.query(null, null, null, "GLOBAL", null, null, 1, 20, "tr");

        assertThat(r.items()).isNotEmpty().allMatch(a -> a.getTitle().startsWith("TR:"));
        verify(translationPort, never()).translate(anyString(), eq("tr"), anyString());
    }

    @Test
    @DisplayName("query: lang boş/null → çeviri yok")
    void queryNoTranslationWhenLangBlank() {
        when(cache.getAll()).thenReturn(sampleArticles());

        NewsQueryResult r = service.query(null, null, null, "GLOBAL", null, null, 1, 20, "  ");

        assertThat(r.items().get(0).getTitle()).doesNotStartWith("TR:");
        verify(translationPort, never()).translate(anyString(), anyString(), anyString());
    }

    // ── getById: detail + related + content ────────────────────────────────────

    @Test
    @DisplayName("getById: null/blank id → null")
    void getByIdNullId() {
        assertThat(service.getById(null, null)).isNull();
        assertThat(service.getById("   ", null)).isNull();
    }

    @Test
    @DisplayName("getById: bulunamayan id → null")
    void getByIdNotFound() {
        when(cache.getAll()).thenReturn(sampleArticles());
        assertThat(service.getById("does-not-exist", null)).isNull();
    }

    @Test
    @DisplayName("getById: content:encoded varsa extractFromHtml kullanılır (scrape yok)")
    void getByIdUsesRawContent() {
        NewsArticle withContent = new NewsArticle("T", "D", "https://x.com/raw", null,
                "2026-05-24T12:00:00Z", "Kriptofoni", null, NewsCategory.CRYPTO.name(), "tr", "<p>html</p>");
        when(cache.getAll()).thenReturn(List.of(withContent));
        when(contentPort.extractFromHtml("<p>html</p>")).thenReturn("extracted text");

        String id = NewsIdUtil.idFor("https://x.com/raw");
        NewsDetail d = service.getById(id, null);

        assertThat(d).isNotNull();
        assertThat(d.content()).isEqualTo("extracted text");
        verify(contentPort, never()).fetchContent(anyString());
    }

    @Test
    @DisplayName("getById: içerik yok + kaynak Finnhub değilse fetchContent çağrılır")
    void getByIdScrapesNonFinnhub() {
        NewsArticle a = article("T", "D", "https://x.com/scrape", "2026-05-24T12:00:00Z",
                "Investing", NewsCategory.STOCKS.name(), "en");
        when(cache.getAll()).thenReturn(List.of(a));
        when(contentPort.fetchContent("https://x.com/scrape")).thenReturn("scraped");

        NewsDetail d = service.getById(NewsIdUtil.idFor("https://x.com/scrape"), null);

        assertThat(d.content()).isEqualTo("scraped");
        verify(contentPort).fetchContent("https://x.com/scrape");
    }

    @Test
    @DisplayName("getById: Finnhub kaynağı + içerik yok → scrape edilmez")
    void getByIdFinnhubNoScrape() {
        NewsArticle a = article("T", "D", "https://x.com/fh", "2026-05-24T12:00:00Z",
                "Finnhub", NewsCategory.STOCKS.name(), "en");
        when(cache.getAll()).thenReturn(List.of(a));

        NewsDetail d = service.getById(NewsIdUtil.idFor("https://x.com/fh"), null);

        assertThat(d.content()).isNull();
        verify(contentPort, never()).fetchContent(anyString());
    }

    @Test
    @DisplayName("getById: ilgili haberler aynı kategoriden, kendisi hariç, limitli")
    void getByIdRelated() {
        List<NewsArticle> list = new ArrayList<>();
        NewsArticle main = article("Main", "D", "https://x.com/main", "2026-05-24T12:00:00Z",
                "Finnhub", NewsCategory.STOCKS.name(), "en");
        list.add(main);
        for (int i = 0; i < 8; i++) {
            list.add(article("Rel" + i, "D", "https://x.com/rel" + i, "2026-05-24T12:00:00Z",
                    "Finnhub", NewsCategory.STOCKS.name(), "en"));
        }
        // a different-category one that must be excluded
        list.add(article("Other", "D", "https://x.com/other", "2026-05-24T12:00:00Z",
                "Finnhub", NewsCategory.CRYPTO.name(), "en"));
        when(cache.getAll()).thenReturn(list);

        NewsDetail d = service.getById(NewsIdUtil.idFor("https://x.com/main"), null);

        assertThat(d.related()).hasSize(6); // RELATED_LIMIT
        assertThat(d.related()).noneMatch(a -> "https://x.com/main".equals(a.getUrl()));
        assertThat(d.related()).allMatch(a -> NewsCategory.STOCKS.name().equals(a.getCategory()));
    }

    @Test
    @DisplayName("getById: dil farklıysa başlık+özet+içerik+ilgili çevrilir")
    void getByIdTranslates() {
        NewsArticle main = new NewsArticle("Title", "Desc", "https://x.com/main", null,
                "2026-05-24T12:00:00Z", "Investing", null, NewsCategory.STOCKS.name(), "en", null);
        NewsArticle rel = article("RelTitle", "RelDesc", "https://x.com/rel", "2026-05-24T12:00:00Z",
                "Investing", NewsCategory.STOCKS.name(), "en");
        when(cache.getAll()).thenReturn(List.of(main, rel));
        when(contentPort.fetchContent("https://x.com/main")).thenReturn("body");
        when(translationPort.translate(anyString(), eq("en"), eq("tr")))
                .thenAnswer(inv -> "TR:" + inv.getArgument(0));

        NewsDetail d = service.getById(NewsIdUtil.idFor("https://x.com/main"), "tr");

        assertThat(d.article().getTitle()).isEqualTo("TR:Title");
        assertThat(d.article().getDescription()).isEqualTo("TR:Desc");
        assertThat(d.content()).isEqualTo("TR:body");
        assertThat(d.related().get(0).getTitle()).isEqualTo("TR:RelTitle");
    }

    @Test
    @DisplayName("getById: aynı dil → çeviri yapılmaz")
    void getByIdSameLanguageNoTranslate() {
        NewsArticle main = article("Title", "Desc", "https://x.com/main", "2026-05-24T12:00:00Z",
                "Finnhub", NewsCategory.STOCKS.name(), "tr");
        when(cache.getAll()).thenReturn(List.of(main));

        NewsDetail d = service.getById(NewsIdUtil.idFor("https://x.com/main"), "tr");

        assertThat(d.article().getTitle()).isEqualTo("Title");
        verify(translationPort, never()).translate(anyString(), anyString(), anyString());
    }

    // ── forUser: personalized scoring ──────────────────────────────────────────

    @Test
    @DisplayName("forUser: portföy boş → boş sonuç (cache'e hiç bakılmaz)")
    void forUserEmptyHoldings() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of());

        NewsQueryResult r = service.forUser("u1", null, 10);

        assertThat(r.totalElements()).isZero();
        assertThat(r.items()).isEmpty();
        verify(cache, never()).getAll();
    }

    @Test
    @DisplayName("forUser: sembol/isim eşleşen + kategori eşleşen haberler puana göre sıralı")
    void forUserScoringAndSorting() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of(
                new UserHoldingsPort.Holding("THYAO", "Türk Hava Yolları", "STOCK"),
                new UserHoldingsPort.Holding("BTC", "Bitcoin", "CRYPTO")));

        List<NewsArticle> list = new ArrayList<>();
        // keyword match (10) + category STOCKS match (3) = 13 → highest
        list.add(article("THYAO rekor kırdı", "havayolu", "https://x.com/1",
                "2026-05-20T12:00:00Z", "Hürriyet", NewsCategory.STOCKS.name(), "tr"));
        // only category match CRYPTO (3)
        list.add(article("Genel kripto haberi", "blokzincir", "https://x.com/2",
                "2026-05-25T12:00:00Z", "Finnhub", NewsCategory.CRYPTO.name(), "en"));
        // no match at all (score 0) → excluded
        list.add(article("Hava durumu", "yağmur", "https://x.com/3",
                "2026-05-26T12:00:00Z", "Hürriyet", NewsCategory.WORLD.name(), "tr"));
        when(cache.getAll()).thenReturn(list);

        NewsQueryResult r = service.forUser("u1", null, 10);

        assertThat(r.totalElements()).isEqualTo(2);
        // highest score first
        assertThat(r.items().get(0).getTitle()).isEqualTo("THYAO rekor kırdı");
        assertThat(r.items().get(1).getTitle()).isEqualTo("Genel kripto haberi");
    }

    @Test
    @DisplayName("forUser: limit clamp (max 30, min 1) ve size sonuçta yansır")
    void forUserLimitClamp() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of(
                new UserHoldingsPort.Holding("BTC", "Bitcoin", "CRYPTO")));
        when(cache.getAll()).thenReturn(List.of());

        NewsQueryResult big = service.forUser("u1", null, 999);
        assertThat(big.pageSize()).isEqualTo(30);

        NewsQueryResult zero = service.forUser("u1", null, 0);
        assertThat(zero.pageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("forUser: kısa sembol (<3) ve kısa isim (<4) keyword'e eklenmez")
    void forUserShortIdentifiersIgnored() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of(
                new UserHoldingsPort.Holding("X", "Au", "GOLD")));
        // 'X'/'Au' too short → only category COMMODITIES match counts
        List<NewsArticle> list = new ArrayList<>();
        list.add(article("X haberi Au", "kısa", "https://x.com/1",
                "2026-05-20T12:00:00Z", "Hürriyet", NewsCategory.STOCKS.name(), "tr"));
        list.add(article("Altın yükseldi", "emtia", "https://x.com/2",
                "2026-05-21T12:00:00Z", "Hürriyet", NewsCategory.COMMODITIES.name(), "tr"));
        when(cache.getAll()).thenReturn(list);

        NewsQueryResult r = service.forUser("u1", null, 10);

        // only the COMMODITIES one matches (category), the short keyword one does not
        assertThat(r.totalElements()).isEqualTo(1);
        assertThat(r.items().get(0).getCategory()).isEqualTo(NewsCategory.COMMODITIES.name());
    }

    @Test
    @DisplayName("forUser: çeviri uygulanır")
    void forUserTranslates() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of(
                new UserHoldingsPort.Holding("BTC", "Bitcoin", "CRYPTO")));
        when(cache.getAll()).thenReturn(List.of(
                article("Bitcoin news", "BTC up", "https://x.com/1",
                        "2026-05-20T12:00:00Z", "Finnhub", NewsCategory.CRYPTO.name(), "en")));
        when(translationPort.translate(anyString(), eq("en"), eq("tr")))
                .thenAnswer(inv -> "TR:" + inv.getArgument(0));

        NewsQueryResult r = service.forUser("u1", "tr", 10);

        assertThat(r.items().get(0).getTitle()).isEqualTo("TR:Bitcoin news");
    }

    // ── categoryLabels ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("categoryLabels: tüm kategoriler etiketleriyle döner")
    void categoryLabels() {
        Map<String, String> labels = service.categoryLabels();

        assertThat(labels).hasSize(NewsCategory.values().length);
        assertThat(labels).containsEntry(NewsCategory.STOCKS.name(), NewsCategory.STOCKS.getLabel());
        assertThat(labels).containsEntry(NewsCategory.CRYPTO.name(), NewsCategory.CRYPTO.getLabel());
    }

    @Test
    @DisplayName("query: tarihi parse edilemeyen haber range filtresinde elenmez")
    void queryUnparseableDateNotFiltered() {
        NewsArticle bad = article("Tarihsiz", "D", "https://x.com/bad", "not-a-date",
                "Hürriyet", NewsCategory.STOCKS.name(), "tr");
        when(cache.getAll()).thenReturn(List.of(bad));

        long from = java.time.Instant.parse("2026-05-24T00:00:00Z").toEpochMilli();
        NewsQueryResult r = service.query(null, null, null, "ALL", from, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("forUser: assetType FUND da STOCKS kategorisine maplenir")
    void forUserFundMapsToStocks() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of(
                new UserHoldingsPort.Holding(null, null, "FUND")));
        when(cache.getAll()).thenReturn(List.of(
                article("Hisse haberi", "borsa", "https://x.com/1",
                        "2026-05-20T12:00:00Z", "Hürriyet", NewsCategory.STOCKS.name(), "tr")));

        NewsQueryResult r = service.forUser("u1", null, 10);

        assertThat(r.totalElements()).isEqualTo(1);
    }
}
