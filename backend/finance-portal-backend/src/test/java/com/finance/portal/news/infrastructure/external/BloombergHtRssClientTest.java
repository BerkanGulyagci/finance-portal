package com.finance.portal.news.infrastructure.external;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.news.application.model.NewsArticle;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BloombergHtRssClient — RSS parse / og:image enrichment / hata yollari branch coverage.
 *
 * RestTemplate iki ayri cagri yapar:
 *  - getForObject(RSS_URL, byte[].class)         -> RSS govdesi
 *  - exchange(url, GET, entity, String.class)    -> her item icin og:image HTML scrape
 *
 * Cogu testte og:image scrape'i etkisizlestirmek icin exchange null-body doner ya da patlar,
 * boylece item'lar degismeden kalir (imageUrl null).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BloombergHtRssClient — RSS parse + og:image + hata yollari")
class BloombergHtRssClientTest {

    @Mock
    RestTemplate restTemplate;

    @Mock
    CentralIntegrationLogService integrationLogService;

    private BloombergHtRssClient client;

    @BeforeEach
    void setUp() {
        client = new BloombergHtRssClient(restTemplate, integrationLogService);
    }

    private void stubRss(String xml) {
        when(restTemplate.getForObject(eq("https://www.bloomberght.com/rss"), eq(byte[].class)))
                .thenReturn(xml.getBytes(StandardCharsets.UTF_8));
    }

    /** og:image scrape cagrisini etkisizlestirir: govde null -> null donerek item degismez. */
    private void stubOgImageNoBody() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>((String) null, org.springframework.http.HttpStatus.OK));
    }

    /** og:image scrape cagrisinda istisna firlatir -> fetchOgImage catch ile null doner. */
    private void stubOgImageThrows() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("og fail"));
    }

    @Test
    @DisplayName("fetchNews: null govde -> bos liste + EMPTY_RESPONSE WARN loglanir")
    void fetchNews_nullBody_returnsEmptyAndLogsEmpty() {
        when(restTemplate.getForObject(eq("https://www.bloomberght.com/rss"), eq(byte[].class)))
                .thenReturn(null);

        List<NewsArticle> result = client.fetchNews();

        assertThat(result).isEmpty();
        verify(integrationLogService).publish(
                eq(IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE),
                eq("WARN"), any(), eq(IntegrationLogSupport.PROVIDER_BLOOMBERG_HT),
                any(), any(), any(), any(), any(), any(), any());
        // og:image scrape hic denenmez
        verify(restTemplate, never()).exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("fetchNews: bos (0-byte) govde -> bos liste + EMPTY_RESPONSE WARN")
    void fetchNews_zeroLengthBody_returnsEmpty() {
        when(restTemplate.getForObject(eq("https://www.bloomberght.com/rss"), eq(byte[].class)))
                .thenReturn(new byte[0]);

        List<NewsArticle> result = client.fetchNews();

        assertThat(result).isEmpty();
        verify(integrationLogService).publish(
                eq(IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE),
                eq("WARN"), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchNews: getForObject patlarsa -> bos liste + NEWS_FETCH_FAILED ERROR")
    void fetchNews_restTemplateThrows_returnsEmptyAndLogsFailed() {
        when(restTemplate.getForObject(eq("https://www.bloomberght.com/rss"), eq(byte[].class)))
                .thenThrow(new RestClientException("network down"));

        List<NewsArticle> result = client.fetchNews();

        assertThat(result).isEmpty();
        verify(integrationLogService).publish(
                eq(IntegrationLogSupport.EVENT_NEWS_FETCH_FAILED),
                eq("ERROR"), any(), eq(IntegrationLogSupport.PROVIDER_BLOOMBERG_HT),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchNews: bicimsiz XML -> parse istisnasi catch -> bos liste + NEWS_FETCH_FAILED")
    void fetchNews_malformedXml_returnsEmpty() {
        stubRss("<rss><item><title>Acik etiket"); // kapanmayan etiketler -> SAXException

        List<NewsArticle> result = client.fetchNews();

        assertThat(result).isEmpty();
        verify(integrationLogService).publish(
                eq(IntegrationLogSupport.EVENT_NEWS_FETCH_FAILED),
                eq("ERROR"), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchNews: DOCTYPE iceren XML -> disallow-doctype-decl ihlali -> bos liste")
    void fetchNews_doctypeXml_returnsEmpty() {
        stubRss("<?xml version=\"1.0\"?><!DOCTYPE rss><rss><channel></channel></rss>");

        List<NewsArticle> result = client.fetchNews();

        assertThat(result).isEmpty();
        verify(integrationLogService).publish(
                eq(IntegrationLogSupport.EVENT_NEWS_FETCH_FAILED),
                eq("ERROR"), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchNews: hicbir <item> yoksa -> bos liste (parse OK, dongu calismaz)")
    void fetchNews_noItems_returnsEmpty() {
        stubRss("<?xml version=\"1.0\"?><rss><channel><title>Bos</title></channel></rss>");

        List<NewsArticle> result = client.fetchNews();

        assertThat(result).isEmpty();
        // Parse basarili -> hata loglanmaz
        verify(integrationLogService, never()).publish(
                eq(IntegrationLogSupport.EVENT_NEWS_FETCH_FAILED),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchNews: tek item tum alanlariyla map'lenir; og:image null kalir")
    void fetchNews_singleItem_mapsAllFields() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item>"
                + "<title>Piyasa Haberi</title>"
                + "<description>Onemli gelisme</description>"
                + "<link>https://www.bloomberght.com/haber/1</link>"
                + "<pubDate>Mon, 02 Jun 2026 10:00:00 GMT</pubDate>"
                + "</item>"
                + "</channel></rss>";
        stubRss(xml);
        stubOgImageNoBody(); // og scrape -> null govde -> imageUrl null kalir

        List<NewsArticle> result = client.fetchNews();

        assertThat(result).hasSize(1);
        NewsArticle a = result.get(0);
        assertThat(a.getTitle()).isEqualTo("Piyasa Haberi");
        assertThat(a.getDescription()).isEqualTo("Onemli gelisme");
        assertThat(a.getUrl()).isEqualTo("https://www.bloomberght.com/haber/1");
        assertThat(a.getPublishedAt()).isEqualTo("Mon, 02 Jun 2026 10:00:00 GMT");
        assertThat(a.getSource()).isEqualTo("BloombergHT");
        assertThat(a.getImageUrl()).isNull();
    }

    @Test
    @DisplayName("fetchNews: eksik/blank alanlar -> directChildText null doner")
    void fetchNews_missingAndBlankFields_yieldNulls() {
        // title var, description bos (blank), link YOK, pubDate YOK
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item>"
                + "<title>Sadece Baslik</title>"
                + "<description>   </description>"
                + "</item>"
                + "</channel></rss>";
        stubRss(xml);
        // link null oldugu icin enrichWithOgImages bu item'i atlar -> exchange cagrilmaz

        List<NewsArticle> result = client.fetchNews();

        assertThat(result).hasSize(1);
        NewsArticle a = result.get(0);
        assertThat(a.getTitle()).isEqualTo("Sadece Baslik");
        assertThat(a.getDescription()).isNull(); // blank -> null
        assertThat(a.getUrl()).isNull();         // eksik -> null
        assertThat(a.getPublishedAt()).isNull(); // eksik -> null
        assertThat(a.getImageUrl()).isNull();

        // url null -> og:image scrape atlanir
        verify(restTemplate, never()).exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("fetchNews: birden cok item -> hepsi map'lenir")
    void fetchNews_multipleItems() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item><title>Bir</title><link>https://x/1</link></item>"
                + "<item><title>Iki</title><link>https://x/2</link></item>"
                + "</channel></rss>";
        stubRss(xml);
        stubOgImageThrows(); // her iki scrape de patlar -> imageUrl null kalir, item'lar korunur

        List<NewsArticle> result = client.fetchNews();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(NewsArticle::getTitle).containsExactly("Bir", "Iki");
        assertThat(result).allSatisfy(a -> assertThat(a.getImageUrl()).isNull());
    }

    @Test
    @DisplayName("fetchNews: og:image (pattern1: property-once-content-after) item'a yazilir")
    void fetchNews_ogImagePattern1_setsImageUrl() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item><title>Resimli</title><link>https://x/p1</link></item>"
                + "</channel></rss>";
        stubRss(xml);
        String html = "<html><head>"
                + "<meta property=\"og:image\" content=\"https://cdn.x/p1.jpg\">"
                + "</head></html>";
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(html, org.springframework.http.HttpStatus.OK));

        List<NewsArticle> result = client.fetchNews();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getImageUrl()).isEqualTo("https://cdn.x/p1.jpg");
    }

    @Test
    @DisplayName("fetchNews: og:image (pattern2: content-once-property-after) item'a yazilir")
    void fetchNews_ogImagePattern2_setsImageUrl() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item><title>Resimli2</title><link>https://x/p2</link></item>"
                + "</channel></rss>";
        stubRss(xml);
        String html = "<html><head>"
                + "<meta content=\"https://cdn.x/p2.png\" property=\"og:image\">"
                + "</head></html>";
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(html, org.springframework.http.HttpStatus.OK));

        List<NewsArticle> result = client.fetchNews();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getImageUrl()).isEqualTo("https://cdn.x/p2.png");
    }

    @Test
    @DisplayName("fetchNews: og:image yok (eslesme yok) -> imageUrl null kalir; >5000 char head substring yolu")
    void fetchNews_ogImageNoMatch_longHtml_keepsNull() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item><title>Resimsiz</title><link>https://x/none</link></item>"
                + "</channel></rss>";
        stubRss(xml);
        // 5000+ karakterlik, og:image icermeyen HTML -> substring(0,5000) dali + iki pattern de bos
        StringBuilder big = new StringBuilder("<html><head><title>x</title>");
        while (big.length() < 6000) {
            big.append("<p>dolgu metin parcasi burada tekrar eder durur.</p>");
        }
        big.append("</head></html>");
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(big.toString(), org.springframework.http.HttpStatus.OK));

        List<NewsArticle> result = client.fetchNews();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getImageUrl()).isNull();
    }

    @Test
    @DisplayName("fetchNews: basarili parse hata logu URETMEZ (happy-path log assertion)")
    void fetchNews_happyPath_doesNotLogFailure() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss><channel>"
                + "<item><title>OK</title><link>https://x/ok</link></item>"
                + "</channel></rss>";
        stubRss(xml);
        stubOgImageThrows();

        client.fetchNews();

        // Basarili yolda hicbir entegrasyon event'i yayinlanmaz (publish hic cagrilmaz)
        verify(integrationLogService, never()).publish(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }
}
