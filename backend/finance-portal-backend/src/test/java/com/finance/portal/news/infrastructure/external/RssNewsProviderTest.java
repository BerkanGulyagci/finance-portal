package com.finance.portal.news.infrastructure.external;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.infrastructure.config.NewsSourcesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RssNewsProvider — branch coverage.
 *
 * Tek bagimlilik HTTP cagrisi:
 *   restTemplate.exchange(url, GET, HttpEntity, byte[].class) -> ham RSS/Atom govdesi.
 *
 * Hedeflenen dallar: isEnabled (null/bos/dolu), fetch (null rss / blank url continue /
 * feed istisnasi catch+log), fetchFeed (null/0-byte govde), parse (RSS vs Atom, content:encoded
 * vs description vs text, atomLink vs link, pubDate varyantlari, title|link null -> continue,
 * language null -> "tr", truncate), extractImage (media/enclosure/image http), firstImageIn,
 * cleanHtml/childText/firstNonBlank yardimcilari.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RssNewsProvider — RSS/Atom parse + gorsel/tarih + hata yollari")
class RssNewsProviderTest {

    @Mock
    RestTemplate restTemplate;

    @Mock
    CentralIntegrationLogService integrationLog;

    private NewsSourcesProperties properties;
    private RssNewsProvider provider;

    @BeforeEach
    void setUp() {
        properties = new NewsSourcesProperties();
        provider = new RssNewsProvider(restTemplate, properties, integrationLog);
    }

    private static NewsSourcesProperties.RssFeed feed(String name, String url) {
        NewsSourcesProperties.RssFeed f = new NewsSourcesProperties.RssFeed();
        f.setName(name);
        f.setUrl(url);
        return f;
    }

    private void stubBody(String url, byte[] body) {
        when(restTemplate.exchange(eq(url), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));
    }

    private void stubXml(String url, String xml) {
        stubBody(url, xml == null ? null : xml.getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- id / isEnabled

    @Test
    @DisplayName("id() -> 'rss'")
    void id_isRss() {
        assertThat(provider.id()).isEqualTo("rss");
    }

    @Test
    @DisplayName("isEnabled: rss null -> false")
    void isEnabled_nullRss_false() {
        properties.setRss(null);
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEnabled: rss bos liste -> false")
    void isEnabled_emptyRss_false() {
        properties.setRss(new ArrayList<>());
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEnabled: en az bir feed -> true")
    void isEnabled_nonEmptyRss_true() {
        properties.setRss(List.of(feed("X", "https://x/rss")));
        assertThat(provider.isEnabled()).isTrue();
    }

    // ---------------------------------------------------------------- fetch() ust dallar

    @Test
    @DisplayName("fetch: rss null -> bos liste (HTTP cagrisi yok)")
    void fetch_nullRss_returnsEmpty() {
        properties.setRss(null);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).isEmpty();
        verify(restTemplate, never()).exchange(any(String.class), any(HttpMethod.class),
                any(HttpEntity.class), eq(byte[].class));
    }

    @Test
    @DisplayName("fetch: url null veya blank feed'ler atlanir (continue)")
    void fetch_nullAndBlankUrl_skipped() {
        properties.setRss(List.of(feed("NoUrl", null), feed("Blank", "   ")));

        List<NewsArticle> result = provider.fetch();

        assertThat(result).isEmpty();
        verify(restTemplate, never()).exchange(any(String.class), any(HttpMethod.class),
                any(HttpEntity.class), eq(byte[].class));
    }

    @Test
    @DisplayName("fetch: feed istisnasi -> catch + WARN + NEWS_FETCH_FAILED publish, dongu devam")
    void fetch_feedThrows_logsAndContinues() {
        properties.setRss(List.of(feed("Patlar", "https://x/boom")));
        when(restTemplate.exchange(eq("https://x/boom"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(new RestClientException("network down"));

        List<NewsArticle> result = provider.fetch();

        assertThat(result).isEmpty();
        verify(integrationLog).publish(
                eq(IntegrationLogSupport.EVENT_NEWS_FETCH_FAILED), eq("WARN"),
                any(), eq("rss"), eq("rss_fetch"),
                any(), any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- fetchFeed govde dallari

    @Test
    @DisplayName("fetchFeed: null govde -> bos (parse cagrilmaz, hata loglanmaz)")
    void fetchFeed_nullBody_empty() {
        properties.setRss(List.of(feed("NullBody", "https://x/null")));
        stubBody("https://x/null", null);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).isEmpty();
        verify(integrationLog, never()).publish(
                eq(IntegrationLogSupport.EVENT_NEWS_FETCH_FAILED),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchFeed: 0-byte govde -> bos")
    void fetchFeed_zeroLengthBody_empty() {
        properties.setRss(List.of(feed("Empty", "https://x/empty")));
        stubBody("https://x/empty", new byte[0]);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchFeed: bicimsiz XML -> parse istisnasi catch ile yutulur (WARN publish)")
    void fetchFeed_malformedXml_logsAndEmpty() {
        properties.setRss(List.of(feed("Bozuk", "https://x/bad")));
        stubXml("https://x/bad", "<rss><item><title>acik");

        List<NewsArticle> result = provider.fetch();

        assertThat(result).isEmpty();
        verify(integrationLog).publish(
                eq(IntegrationLogSupport.EVENT_NEWS_FETCH_FAILED), eq("WARN"),
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchFeed: DOCTYPE iceren XML -> disallow-doctype ihlali -> bos")
    void fetchFeed_doctype_empty() {
        properties.setRss(List.of(feed("Doctype", "https://x/dt")));
        stubXml("https://x/dt", "<?xml version=\"1.0\"?><!DOCTYPE rss><rss><channel></channel></rss>");

        List<NewsArticle> result = provider.fetch();

        assertThat(result).isEmpty();
        verify(integrationLog).publish(
                eq(IntegrationLogSupport.EVENT_NEWS_FETCH_FAILED), eq("WARN"),
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- parse: item olmama / atom

    @Test
    @DisplayName("parse: hicbir item yok -> bos, hata loglanmaz")
    void parse_noItems_empty() {
        properties.setRss(List.of(feed("BosKanal", "https://x/none")));
        stubXml("https://x/none", "<?xml version=\"1.0\"?><rss><channel><title>x</title></channel></rss>");

        List<NewsArticle> result = provider.fetch();

        assertThat(result).isEmpty();
        verify(integrationLog, never()).publish(
                eq(IntegrationLogSupport.EVENT_NEWS_FETCH_FAILED),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("parse: Atom <entry> + alternate <link href> + <updated> -> atomLink/atom dali")
    void parse_atomEntry_usesAtomLinkAndUpdated() {
        NewsSourcesProperties.RssFeed f = feed("AtomKaynak", "https://x/atom");
        f.setLanguage("en");
        f.setCategory("TECH");
        properties.setRss(List.of(f));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\">"
                + "<entry>"
                + "<title>Atom Baslik</title>"
                + "<summary>Ozet metin</summary>"
                + "<link rel=\"self\" href=\"https://x/self\"/>"
                + "<link rel=\"alternate\" href=\"https://x/article\"/>"
                + "<updated>2026-05-24T12:15:00Z</updated>"
                + "</entry>"
                + "</feed>";
        stubXml("https://x/atom", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).hasSize(1);
        NewsArticle a = result.get(0);
        assertThat(a.getTitle()).isEqualTo("Atom Baslik");
        assertThat(a.getDescription()).isEqualTo("Ozet metin");
        // alternate href tercih edilir (self atlanir)
        assertThat(a.getUrl()).isEqualTo("https://x/article");
        assertThat(a.getSource()).isEqualTo("AtomKaynak");
        assertThat(a.getCategory()).isEqualTo("TECH");
        assertThat(a.getLanguage()).isEqualTo("en");
        // updated ISO -> instant'a normalize
        assertThat(a.getPublishedAt()).isEqualTo("2026-05-24T12:15:00Z");
    }

    @Test
    @DisplayName("parse: Atom rel-siz <link> -> rel blank dali href doner")
    void parse_atomEntry_noRelLink_usesHref() {
        properties.setRss(List.of(feed("AtomNoRel", "https://x/atom2")));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\">"
                + "<entry>"
                + "<title>NoRel</title>"
                + "<link href=\"https://x/norel\"/>"
                + "</entry>"
                + "</feed>";
        stubXml("https://x/atom2", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUrl()).isEqualTo("https://x/norel");
    }

    @Test
    @DisplayName("parse: Atom <link> href yok/blank -> atomLink fallback childText(link) null -> item atlanir")
    void parse_atomEntry_blankHref_fallbackNullLink_skipped() {
        properties.setRss(List.of(feed("AtomBlank", "https://x/atom3")));
        // alternate ama href blank -> atom donguden gecmez; childText("link") yok -> link null -> continue
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\">"
                + "<entry>"
                + "<title>BlankHref</title>"
                + "<link rel=\"alternate\" href=\"  \"/>"
                + "</entry>"
                + "</feed>";
        stubXml("https://x/atom3", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).isEmpty(); // link null -> continue
    }

    // ---------------------------------------------------------------- parse: RSS icerik dallari

    @Test
    @DisplayName("parse: RSS item, content:encoded -> content alanini ve icerideki ilk http <img>'i alir")
    void parse_rssItem_contentEncoded_imageFromContent() {
        properties.setRss(List.of(feed("Kripto", "https://x/k")));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss xmlns:content=\"http://purl.org/rss/1.0/modules/content/\">"
                + "<channel>"
                + "<item>"
                + "<title>Kripto Haber</title>"
                + "<link>https://x/k/1</link>"
                + "<pubDate>Sun, 24 May 2026 12:16:21 +0300</pubDate>"
                + "<content:encoded><![CDATA[<p>Giris</p>"
                + "<img src=\"/relatif.png\"/>"
                + "<img src=\"https://cdn.x/gercek.jpg\"/> devam metni]]></content:encoded>"
                + "</item>"
                + "</channel></rss>";
        stubXml("https://x/k", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).hasSize(1);
        NewsArticle a = result.get(0);
        // description content:encoded'dan temizlenmis HTML (description yok)
        assertThat(a.getDescription()).contains("Giris");
        assertThat(a.getDescription()).doesNotContain("<p>");
        // image media/enclosure yok -> firstImageIn ile ilk http img (relatif atlanir)
        assertThat(a.getImageUrl()).isEqualTo("https://cdn.x/gercek.jpg");
        // content alani dolu (truncate uygulanmis ham HTML)
        assertThat(a.getContent()).contains("Giris");
        // RFC-2822 pubDate normalize edildi (Z'li instant)
        assertThat(a.getPublishedAt()).isEqualTo("2026-05-24T09:16:21Z");
    }

    @Test
    @DisplayName("parse: description varsa encoded yerine description; media:content url -> image")
    void parse_rssItem_descriptionAndMediaContent() {
        properties.setRss(List.of(feed("Medya", "https://x/m")));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss xmlns:media=\"http://search.yahoo.com/mrss/\">"
                + "<channel>"
                + "<item>"
                + "<title>Medyali</title>"
                + "<description>Aciklama &amp; ozet</description>"
                + "<link>https://x/m/1</link>"
                + "<media:content url=\"https://cdn.x/media.jpg\"/>"
                + "</item>"
                + "</channel></rss>";
        stubXml("https://x/m", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).hasSize(1);
        NewsArticle a = result.get(0);
        assertThat(a.getDescription()).isEqualTo("Aciklama & ozet"); // &amp; decode
        assertThat(a.getImageUrl()).isEqualTo("https://cdn.x/media.jpg");
        // encoded yok -> content null
        assertThat(a.getContent()).isNull();
    }

    @Test
    @DisplayName("parse: media:content url blank -> enclosure'a duser; enclosure url -> image")
    void parse_rssItem_mediaBlank_fallsToEnclosure() {
        properties.setRss(List.of(feed("Enc", "https://x/e")));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss xmlns:media=\"http://search.yahoo.com/mrss/\">"
                + "<channel>"
                + "<item>"
                + "<title>Enclosure</title>"
                + "<link>https://x/e/1</link>"
                + "<media:content url=\"\"/>"
                + "<enclosure url=\"https://cdn.x/enc.jpg\"/>"
                + "</item>"
                + "</channel></rss>";
        stubXml("https://x/e", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getImageUrl()).isEqualTo("https://cdn.x/enc.jpg");
    }

    @Test
    @DisplayName("parse: media/enclosure yok, <image> http ile baslar -> image dondurulur")
    void parse_rssItem_imageTagHttp() {
        properties.setRss(List.of(feed("Img", "https://x/i")));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item>"
                + "<title>Resimli</title>"
                + "<link>https://x/i/1</link>"
                + "<image>https://cdn.x/img.png</image>"
                + "</item>"
                + "</channel></rss>";
        stubXml("https://x/i", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getImageUrl()).isEqualTo("https://cdn.x/img.png");
    }

    @Test
    @DisplayName("parse: <image> http ile baslamaz + encoded yok -> image null kalir")
    void parse_rssItem_imageNotHttp_andNoEncoded_nullImage() {
        properties.setRss(List.of(feed("ImgRel", "https://x/ir")));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item>"
                + "<title>RelImg</title>"
                + "<description>metin</description>"
                + "<link>https://x/ir/1</link>"
                + "<image>/relatif/img.png</image>"
                + "</item>"
                + "</channel></rss>";
        stubXml("https://x/ir", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).hasSize(1);
        // image http degil -> extractImage null; encoded null -> firstImageIn cagrilmaz
        assertThat(result.get(0).getImageUrl()).isNull();
    }

    @Test
    @DisplayName("parse: title yok -> item atlanir (continue)")
    void parse_rssItem_missingTitle_skipped() {
        properties.setRss(List.of(feed("NoTitle", "https://x/nt")));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item><link>https://x/nt/1</link></item>"
                + "</channel></rss>";
        stubXml("https://x/nt", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("parse: link yok -> item atlanir (continue)")
    void parse_rssItem_missingLink_skipped() {
        properties.setRss(List.of(feed("NoLink", "https://x/nl")));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item><title>Sadece Baslik</title></item>"
                + "</channel></rss>";
        stubXml("https://x/nl", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("parse: feed.language null -> 'tr' varsayilani; <text> -> encoded; published pubDate'e fallback")
    void parse_rssItem_nullLanguageDefaultsTr_textEncoded_publishedFallback() {
        NewsSourcesProperties.RssFeed f = feed("Hurriyet", "https://x/h");
        f.setLanguage(null); // dil null -> "tr"
        properties.setRss(List.of(f));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item>"
                + "<title>Hurriyet Haber</title>"
                + "<link>https://x/h/1</link>"
                + "<text>Tam icerik <b>govde</b></text>"
                + "<published>2026-05-20T08:00:00+03:00</published>"
                + "</item>"
                + "</channel></rss>";
        stubXml("https://x/h", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).hasSize(1);
        NewsArticle a = result.get(0);
        assertThat(a.getLanguage()).isEqualTo("tr"); // null -> default
        // description yok -> encoded (<text>) fallback ile temizlenir
        assertThat(a.getDescription()).contains("Tam icerik");
        assertThat(a.getContent()).contains("Tam icerik"); // content = encoded (text)
        // pubDate yok -> published kullanilir, +03:00 -> UTC
        assertThat(a.getPublishedAt()).isEqualTo("2026-05-20T05:00:00Z");
    }

    @Test
    @DisplayName("parse: hicbir tarih yok -> publishedAt null (NewsDateUtil null raw)")
    void parse_rssItem_noDate_publishedNull() {
        properties.setRss(List.of(feed("NoDate", "https://x/nd")));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item><title>Tarihsiz</title><link>https://x/nd/1</link></item>"
                + "</channel></rss>";
        stubXml("https://x/nd", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("parse: parse edilemeyen tarih -> orijinali korunur (toIso raw doner)")
    void parse_rssItem_unparseableDate_keepsRaw() {
        properties.setRss(List.of(feed("BadDate", "https://x/bd")));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item><title>KotuTarih</title><link>https://x/bd/1</link>"
                + "<pubDate>gecersiz-tarih</pubDate></item>"
                + "</channel></rss>";
        stubXml("https://x/bd", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPublishedAt()).isEqualTo("gecersiz-tarih");
    }

    @Test
    @DisplayName("parse: uzun description -> MAX_DESC kirpilir ve '…' eklenir")
    void parse_rssItem_longDescription_truncatedWithEllipsis() {
        properties.setRss(List.of(feed("Uzun", "https://x/u")));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            big.append("kelime ");
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item><title>UzunMetin</title><link>https://x/u/1</link>"
                + "<description>" + big + "</description></item>"
                + "</channel></rss>";
        stubXml("https://x/u", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).hasSize(1);
        String desc = result.get(0).getDescription();
        assertThat(desc).endsWith("…");
        assertThat(desc.length()).isLessThanOrEqualTo(401); // 400 + ellipsis
    }

    @Test
    @DisplayName("parse: description/summary/content/encoded hepsi yok -> description null (cleanHtml null)")
    void parse_rssItem_noBodyAtAll_nullDescription() {
        properties.setRss(List.of(feed("Cıplak", "https://x/c")));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item><title>Sadece</title><link>https://x/c/1</link></item>"
                + "</channel></rss>";
        stubXml("https://x/c", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).hasSize(1);
        NewsArticle a = result.get(0);
        assertThat(a.getDescription()).isNull();
        assertThat(a.getContent()).isNull();
        assertThat(a.getImageUrl()).isNull();
    }

    @Test
    @DisplayName("parse: birden cok item + basarili yol hata loglamaz")
    void parse_multipleItems_noErrorLog() {
        properties.setRss(List.of(feed("Coklu", "https://x/multi")));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item><title>Bir</title><link>https://x/multi/1</link></item>"
                + "<item><title>Iki</title><link>https://x/multi/2</link></item>"
                + "</channel></rss>";
        stubXml("https://x/multi", xml);

        List<NewsArticle> result = provider.fetch();

        assertThat(result).extracting(NewsArticle::getTitle).containsExactly("Bir", "Iki");
        verify(integrationLog, never()).publish(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
