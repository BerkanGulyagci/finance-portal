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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage tamamlayıcısı: NewsAggregatorServiceTest'in DOKUNMADIĞI dalları hedefler —
 * region null/blank/bilinmeyen, GLOBAL+dil null, matchesSource null/uyuşmaz, matchesKeyword null
 * başlık/özet, withinRange yalnız-to & to-aşımı, assetTypeToCategory FX/COMMODITY/FUTURE/BOND/
 * bilinmeyen/null, buildKeywordPattern boş→null + scoreFor kwPattern null, scoreFor kategori null/
 * dışında, translatePage hepsi-aynı-dil & dil-null atlama, getById dil-null & içerik-null+çeviri.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewsAggregatorServiceMoreTest {

    @Mock
    private NewsAggregateCache cache;
    @Mock
    private ArticleContentPort contentPort;
    @Mock
    private TranslationPort translationPort;
    @Mock
    private UserHoldingsPort holdingsPort;

    private NewsAggregatorService service;

    @BeforeEach
    void setUp() {
        service = new NewsAggregatorService(cache, contentPort, translationPort, holdingsPort);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static NewsArticle article(String title, String desc, String url, String publishedAt,
                                       String source, String category, String language) {
        return new NewsArticle(title, desc, url, null, publishedAt, source, null, category, language, null);
    }

    // ── matchesRegion: null / blank / unrecognized / GLOBAL with null lang ──────

    @Test
    @DisplayName("query: region=null → tüm haberler (TR + en karışık)")
    void queryRegionNull() {
        when(cache.getAll()).thenReturn(List.of(
                article("TR haber", "d", "https://x.com/1", "2026-05-24T12:00:00Z", "Hürriyet",
                        NewsCategory.STOCKS.name(), "tr"),
                article("EN news", "d", "https://x.com/2", "2026-05-24T12:00:00Z", "Finnhub",
                        NewsCategory.CRYPTO.name(), "en")));

        NewsQueryResult r = service.query(null, null, null, null, null, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("query: region=blank → tüm haberler")
    void queryRegionBlank() {
        when(cache.getAll()).thenReturn(List.of(
                article("TR haber", "d", "https://x.com/1", "2026-05-24T12:00:00Z", "Hürriyet",
                        NewsCategory.STOCKS.name(), "tr")));

        NewsQueryResult r = service.query(null, null, null, "   ", null, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("query: region=tanınmayan (XX) → matchesRegion final return true → hepsi")
    void queryRegionUnrecognized() {
        when(cache.getAll()).thenReturn(List.of(
                article("TR haber", "d", "https://x.com/1", "2026-05-24T12:00:00Z", "Hürriyet",
                        NewsCategory.STOCKS.name(), "tr"),
                article("EN news", "d", "https://x.com/2", "2026-05-24T12:00:00Z", "Finnhub",
                        NewsCategory.CRYPTO.name(), "en")));

        NewsQueryResult r = service.query(null, null, null, "XX", null, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("query: region=GLOBAL + dil null → o haber elenir (lang != null false arm)")
    void queryGlobalExcludesNullLanguage() {
        when(cache.getAll()).thenReturn(List.of(
                article("Dilsiz", "d", "https://x.com/1", "2026-05-24T12:00:00Z", "Finnhub",
                        NewsCategory.CRYPTO.name(), null),
                article("EN news", "d", "https://x.com/2", "2026-05-24T12:00:00Z", "Finnhub",
                        NewsCategory.CRYPTO.name(), "en")));

        NewsQueryResult r = service.query(null, null, null, "GLOBAL", null, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(1);
        assertThat(r.items().get(0).getTitle()).isEqualTo("EN news");
    }

    // ── matchesSource: article source null / non-matching ───────────────────────

    @Test
    @DisplayName("query: kaynak filtresi — makale source null (facet'ten düşer, eşleşmez)")
    void querySourceNullArticleSource() {
        when(cache.getAll()).thenReturn(List.of(
                // source null: facet listesinden (filter s!=null) düşer + matchesSource s!=null false
                article("Kaynaksız", "d", "https://x.com/1", "2026-05-24T12:00:00Z", null,
                        NewsCategory.STOCKS.name(), "tr"),
                article("Hürriyet haber", "d", "https://x.com/2", "2026-05-24T12:00:00Z", "Hürriyet",
                        NewsCategory.STOCKS.name(), "tr")));

        NewsQueryResult r = service.query(null, "Hürriyet", null, "ALL", null, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(1);
        assertThat(r.items().get(0).getTitle()).isEqualTo("Hürriyet haber");
        // null source facet listesinde yer almaz
        assertThat(r.sources()).containsExactly("Hürriyet");
    }

    @Test
    @DisplayName("query: kaynak filtresi — eşleşmeyen source (equals & startsWith ikisi de false)")
    void querySourceNonMatching() {
        when(cache.getAll()).thenReturn(List.of(
                article("Finnhub haber", "d", "https://x.com/1", "2026-05-24T12:00:00Z", "Finnhub",
                        NewsCategory.STOCKS.name(), "en")));

        NewsQueryResult r = service.query(null, "Hürriyet", null, "ALL", null, null, 1, 20, null);

        assertThat(r.totalElements()).isZero();
    }

    // ── matchesKeyword: article with null title AND null description ─────────────

    @Test
    @DisplayName("query: keyword — başlık+özet null olan haber eşleşmez (ternary null kolları)")
    void queryKeywordNullTitleAndDesc() {
        when(cache.getAll()).thenReturn(List.of(
                // title+desc null → "" + " " + "" → keyword bulunmaz
                article(null, null, "https://x.com/1", "2026-05-24T12:00:00Z", "Hürriyet",
                        NewsCategory.STOCKS.name(), "tr"),
                article("Bitcoin haber", "btc", "https://x.com/2", "2026-05-24T12:00:00Z", "Finnhub",
                        NewsCategory.CRYPTO.name(), "tr")));

        NewsQueryResult r = service.query(null, null, "bitcoin", "ALL", null, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(1);
        assertThat(r.items().get(0).getTitle()).isEqualTo("Bitcoin haber");
    }

    // ── withinRange: only-to (from null) & to-exceeded false arm ────────────────

    @Test
    @DisplayName("query: yalnız toMillis verili — sınırı aşan haber elenir (t<=to false arm)")
    void queryOnlyToMillisExcludesNewer() {
        when(cache.getAll()).thenReturn(List.of(
                article("Eski", "d", "https://x.com/old", "2026-05-20T12:00:00Z", "Hürriyet",
                        NewsCategory.STOCKS.name(), "tr"),
                article("Yeni", "d", "https://x.com/new", "2026-05-30T12:00:00Z", "Hürriyet",
                        NewsCategory.STOCKS.name(), "tr")));

        long to = java.time.Instant.parse("2026-05-25T00:00:00Z").toEpochMilli();
        NewsQueryResult r = service.query(null, null, null, "ALL", null, to, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(1);
        assertThat(r.items().get(0).getTitle()).isEqualTo("Eski");
    }

    @Test
    @DisplayName("query: yalnız fromMillis verili — eskisini eler, yenisini tutar (to null arm)")
    void queryOnlyFromMillisExcludesOlder() {
        when(cache.getAll()).thenReturn(List.of(
                article("Eski", "d", "https://x.com/old", "2026-05-20T12:00:00Z", "Hürriyet",
                        NewsCategory.STOCKS.name(), "tr"),
                article("Yeni", "d", "https://x.com/new", "2026-05-30T12:00:00Z", "Hürriyet",
                        NewsCategory.STOCKS.name(), "tr")));

        long from = java.time.Instant.parse("2026-05-25T00:00:00Z").toEpochMilli();
        NewsQueryResult r = service.query(null, null, null, "ALL", from, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(1);
        assertThat(r.items().get(0).getTitle()).isEqualTo("Yeni");
    }

    // ── assetTypeToCategory: FX / COMMODITY / FUTURE / BOND / unknown / null ─────

    @Test
    @DisplayName("forUser: assetType FX → FX kategorisi eşleşir")
    void forUserAssetTypeFx() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of(
                new UserHoldingsPort.Holding(null, null, "FX")));
        when(cache.getAll()).thenReturn(List.of(
                article("Döviz haberi", "usd", "https://x.com/1", "2026-05-20T12:00:00Z", "Hürriyet",
                        NewsCategory.FX.name(), "tr")));

        NewsQueryResult r = service.forUser("u1", null, 10);

        assertThat(r.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("forUser: assetType COMMODITY ve FUTURE → COMMODITIES kategorisine maplenir")
    void forUserAssetTypeCommodityAndFuture() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of(
                new UserHoldingsPort.Holding(null, null, "COMMODITY"),
                new UserHoldingsPort.Holding(null, null, "FUTURE")));
        when(cache.getAll()).thenReturn(List.of(
                article("Emtia haberi", "altın", "https://x.com/1", "2026-05-20T12:00:00Z", "Hürriyet",
                        NewsCategory.COMMODITIES.name(), "tr")));

        NewsQueryResult r = service.forUser("u1", null, 10);

        assertThat(r.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("forUser: assetType BOND → BONDS kategorisine maplenir")
    void forUserAssetTypeBond() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of(
                new UserHoldingsPort.Holding(null, null, "BOND")));
        when(cache.getAll()).thenReturn(List.of(
                article("Tahvil haberi", "faiz", "https://x.com/1", "2026-05-20T12:00:00Z", "Hürriyet",
                        NewsCategory.BONDS.name(), "tr")));

        NewsQueryResult r = service.forUser("u1", null, 10);

        assertThat(r.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("forUser: bilinmeyen assetType → kategori null (default arm), eşleşme yok")
    void forUserUnknownAssetType() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of(
                // symbol/name yeterince uzun → keyword eklenir; assetType bilinmiyor → default null
                new UserHoldingsPort.Holding("REALESTATE", "Gayrimenkul Fonu", "REALESTATE")));
        when(cache.getAll()).thenReturn(List.of(
                // kategori eşleşmez (cats boş) AND keyword de geçmez → score 0 → elenir
                article("Borsa haberi", "hisse", "https://x.com/1", "2026-05-20T12:00:00Z", "Hürriyet",
                        NewsCategory.STOCKS.name(), "tr")));

        NewsQueryResult r = service.forUser("u1", null, 10);

        assertThat(r.totalElements()).isZero();
    }

    @Test
    @DisplayName("forUser: assetType null → assetTypeToCategory null guard; isim eşleşince yine bulunur")
    void forUserNullAssetType() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of(
                new UserHoldingsPort.Holding("BTC", "Bitcoin", null)));
        when(cache.getAll()).thenReturn(List.of(
                article("Bitcoin haberi", "btc", "https://x.com/1", "2026-05-20T12:00:00Z", "Finnhub",
                        NewsCategory.WORLD.name(), "tr")));

        NewsQueryResult r = service.forUser("u1", null, 10);

        // keyword (10) eşleşir, kategori (cats boş) eşleşmez ama yine de score>0
        assertThat(r.totalElements()).isEqualTo(1);
    }

    // ── buildKeywordPattern empty → null + scoreFor kwPattern null path ──────────

    @Test
    @DisplayName("forUser: keyword'ler boş (kısa sembol/isim) ama kategori var → kwPattern null, yalnız kategori puanlar")
    void forUserNoKeywordsCategoryOnly() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of(
                // symbol<3, name<4 → keyword yok → buildKeywordPattern boş set → null
                new UserHoldingsPort.Holding("X", "Au", "CRYPTO")));
        when(cache.getAll()).thenReturn(List.of(
                article("Kripto piyasası", "blokzincir", "https://x.com/1", "2026-05-20T12:00:00Z",
                        "Finnhub", NewsCategory.CRYPTO.name(), "en"),
                article("Borsa", "hisse", "https://x.com/2", "2026-05-20T12:00:00Z",
                        "Hürriyet", NewsCategory.STOCKS.name(), "tr")));

        NewsQueryResult r = service.forUser("u1", null, 10);

        // sadece CRYPTO kategorisi eşleşir (kwPattern null → keyword puanı yok)
        assertThat(r.totalElements()).isEqualTo(1);
        assertThat(r.items().get(0).getCategory()).isEqualTo(NewsCategory.CRYPTO.name());
    }

    // ── scoreFor: article category null/unparseable & not in cats ────────────────

    @Test
    @DisplayName("forUser: haber kategorisi parse edilemez → +3 yok; keyword ile yine bulunur")
    void forUserArticleCategoryUnparseable() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of(
                new UserHoldingsPort.Holding("BTC", "Bitcoin", "CRYPTO")));
        when(cache.getAll()).thenReturn(List.of(
                // kategori "BOGUS" → NewsCategory.fromString null → +3 atlanır; keyword Bitcoin → +10
                article("Bitcoin coştu", "btc", "https://x.com/1", "2026-05-20T12:00:00Z", "Finnhub",
                        "BOGUS", "en"),
                // kategori parse olur ama cats'te değil (STOCKS), keyword de yok → score 0 → elenir
                article("Genel borsa", "hisse", "https://x.com/2", "2026-05-20T12:00:00Z", "Hürriyet",
                        NewsCategory.STOCKS.name(), "tr")));

        NewsQueryResult r = service.forUser("u1", null, 10);

        assertThat(r.totalElements()).isEqualTo(1);
        assertThat(r.items().get(0).getTitle()).isEqualTo("Bitcoin coştu");
    }

    @Test
    @DisplayName("forUser: scoreFor — başlık+özet null olan haberde keyword aranır (NPE yok), eşleşmez")
    void forUserScoreNullTitleDesc() {
        when(holdingsPort.holdingsForUser("u1")).thenReturn(List.of(
                new UserHoldingsPort.Holding("BTC", "Bitcoin", "CRYPTO")));
        when(cache.getAll()).thenReturn(List.of(
                // title+desc null + kategori cats'te (CRYPTO) → keyword puanı yok ama kategori +3
                new NewsArticle(null, null, "https://x.com/1", null, "2026-05-20T12:00:00Z",
                        "Finnhub", null, NewsCategory.CRYPTO.name(), "en", null)));

        NewsQueryResult r = service.forUser("u1", null, 10);

        // kategori eşleşmesiyle (3) score>0 → 1 sonuç (başlık/özet null'a rağmen NPE atmaz)
        assertThat(r.totalElements()).isEqualTo(1);
    }

    // ── translatePage: all-same-language early return & null-language skip ───────

    @Test
    @DisplayName("query: hepsi hedef dilde → targets boş, çeviri çağrısı yok (early return)")
    void queryAllAlreadyTargetLanguage() {
        when(cache.getAll()).thenReturn(List.of(
                article("TR1", "d", "https://x.com/1", "2026-05-24T12:00:00Z", "Hürriyet",
                        NewsCategory.STOCKS.name(), "tr"),
                article("TR2", "d", "https://x.com/2", "2026-05-24T12:00:00Z", "Hürriyet",
                        NewsCategory.FX.name(), "tr")));

        // lang=tr ve tüm haberler tr → targets boş → çeviri çağrısı yok
        NewsQueryResult r = service.query(null, null, null, "TR", null, null, 1, 20, "tr");

        assertThat(r.totalElements()).isEqualTo(2);
        verify(translationPort, never()).translate(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("query: dili null olan haber çeviriye dahil edilmez (al != null false), diğeri çevrilir")
    void queryNullLanguageSkippedInTranslate() {
        when(cache.getAll()).thenReturn(List.of(
                // language null → translatePage'de al != null false → atlanır
                article("Dilsiz", "d", "https://x.com/1", "2026-05-24T12:00:00Z", "Finnhub",
                        NewsCategory.CRYPTO.name(), null),
                article("EN news", "d", "https://x.com/2", "2026-05-24T12:00:00Z", "Finnhub",
                        NewsCategory.CRYPTO.name(), "en")));
        when(translationPort.translate(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "TR:" + inv.getArgument(0));

        // region=ALL, lang=tr: dilsiz atlanır, en olan çevrilir
        NewsQueryResult r = service.query(null, null, null, "ALL", null, null, 1, 20, "tr");

        NewsArticle dilsiz = r.items().stream()
                .filter(a -> "https://x.com/1".equals(a.getUrl())).findFirst().orElseThrow();
        NewsArticle en = r.items().stream()
                .filter(a -> "https://x.com/2".equals(a.getUrl())).findFirst().orElseThrow();
        assertThat(dilsiz.getTitle()).isEqualTo("Dilsiz");      // çevrilmedi
        assertThat(en.getTitle()).isEqualTo("TR:EN news");      // çevrildi
    }

    // ── getById: language null (no translate) & content null + translate ────────

    @Test
    @DisplayName("getById: makale dili null + lang=tr → match.getLanguage() null → çeviri yok")
    void getByIdNullArticleLanguageNoTranslate() {
        NewsArticle main = new NewsArticle("Title", "Desc", "https://x.com/main", null,
                "2026-05-24T12:00:00Z", "Finnhub", null, NewsCategory.STOCKS.name(), null, null);
        when(cache.getAll()).thenReturn(List.of(main));

        NewsDetail d = service.getById(NewsIdUtil.idFor("https://x.com/main"), "tr");

        assertThat(d).isNotNull();
        assertThat(d.article().getTitle()).isEqualTo("Title");
        verify(translationPort, never()).translate(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("getById: içerik null + dil farklı → başlık/özet çevrilir, content null kalır (content!=null false)")
    void getByIdTranslateWithNullContent() {
        // Finnhub + ham içerik yok → scrape edilmez (content null) ama dil en≠tr → başlık/özet çevrilir
        NewsArticle main = new NewsArticle("Title", "Desc", "https://x.com/fh", null,
                "2026-05-24T12:00:00Z", "Finnhub", null, NewsCategory.STOCKS.name(), "en", null);
        when(cache.getAll()).thenReturn(List.of(main));
        when(translationPort.translate(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "TR:" + inv.getArgument(0));

        NewsDetail d = service.getById(NewsIdUtil.idFor("https://x.com/fh"), "tr");

        assertThat(d.article().getTitle()).isEqualTo("TR:Title");
        assertThat(d.article().getDescription()).isEqualTo("TR:Desc");
        assertThat(d.content()).isNull(); // content == null → çeviri bloğunda atlanır
        verify(contentPort, never()).fetchContent(anyString());
    }

    @Test
    @DisplayName("getById: ham content blank → fetchContent'e düşülür (raw isBlank true arm)")
    void getByIdBlankRawContentFallsBackToFetch() {
        // raw content "   " (blank) → extractFromHtml çağrılmaz, Finnhub değil → fetchContent
        NewsArticle main = new NewsArticle("Title", "Desc", "https://x.com/blank", null,
                "2026-05-24T12:00:00Z", "Investing", null, NewsCategory.STOCKS.name(), "tr", "   ");
        when(cache.getAll()).thenReturn(List.of(main));
        when(contentPort.fetchContent("https://x.com/blank")).thenReturn("fetched body");

        NewsDetail d = service.getById(NewsIdUtil.idFor("https://x.com/blank"), null);

        assertThat(d.content()).isEqualTo("fetched body");
        verify(contentPort, never()).extractFromHtml(anyString());
        verify(contentPort).fetchContent("https://x.com/blank");
    }

    // ── query: category present but article category null (cat.name != null filter) ──

    @Test
    @DisplayName("query: kategori filtreliyken kategorisi null olan haber elenir; facet'te sayılmaz")
    void queryCategoryFilterExcludesNullCategory() {
        when(cache.getAll()).thenReturn(List.of(
                // kategori null → categoryCounts'a girmez (a.getCategory() != null false) + STOCKS filtresine takılır
                article("Kategorisiz", "d", "https://x.com/1", "2026-05-24T12:00:00Z", "Hürriyet",
                        null, "tr"),
                article("Borsa", "d", "https://x.com/2", "2026-05-24T12:00:00Z", "Hürriyet",
                        NewsCategory.STOCKS.name(), "tr")));

        NewsQueryResult r = service.query("STOCKS", null, null, "ALL", null, null, 1, 20, null);

        assertThat(r.totalElements()).isEqualTo(1);
        assertThat(r.items().get(0).getTitle()).isEqualTo("Borsa");
        // null kategori facet'te yok
        assertThat(r.categoryCounts()).containsOnlyKeys(NewsCategory.STOCKS.name());
    }
}
