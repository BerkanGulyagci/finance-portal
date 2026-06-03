package com.finance.portal.news.infrastructure.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleContentFetcher — makale tam metni / og:image / RSS HTML çıkarımı")
class ArticleContentFetcherTest {

    @Mock
    RestTemplate restTemplate;

    private ArticleContentFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new ArticleContentFetcher(restTemplate);
    }

    /** UTF-8 byte gövdeli, opsiyonel Content-Type charset'li bir HTTP 200 yanıtı kurar. */
    private void stubHtml(String html, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        if (contentType != null) {
            headers.setContentType(contentType);
        }
        ResponseEntity<byte[]> resp =
                new ResponseEntity<>(html.getBytes(StandardCharsets.UTF_8), headers, 200);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class))).thenReturn(resp);
    }

    /** En az MIN_CONTENT (250) karakterlik, junk olmayan tek bir paragraf üretir. */
    private static String longParagraph() {
        StringBuilder sb = new StringBuilder("<p>");
        // ~70 karakterlik anlamlı cümleyi tekrarla → toplam 250+ karakter, junk anahtar kelimesi yok
        for (int i = 0; i < 6; i++) {
            sb.append("Piyasalarda bugun onemli gelismeler yasandi ve yatirimcilar dikkatli. ");
        }
        sb.append("</p>");
        return sb.toString();
    }

    @Test
    @DisplayName("fetchContent: null/blank url -> null, RestTemplate'e dokunulmaz")
    void fetchContent_nullOrBlankUrl_returnsNull() {
        assertThat(fetcher.fetchContent(null)).isNull();
        assertThat(fetcher.fetchContent("")).isNull();
        assertThat(fetcher.fetchContent("   ")).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("fetchContent: gecerli HTML'den paragraflar cikarilir ve sonuc cache'lenir")
    void fetchContent_happyPath_extractsAndCaches() {
        String html = "<html><body><div>" + longParagraph() + "</div></body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        String first = fetcher.fetchContent("https://example.com/a");
        assertThat(first).isNotNull();
        assertThat(first).contains("Piyasalarda");

        // Ikinci cagri cache'ten gelir — RestTemplate yalnizca 1 kez cagrilir
        String second = fetcher.fetchContent("https://example.com/a");
        assertThat(second).isEqualTo(first);
        verify(restTemplate, times(1)).exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class));
    }

    @Test
    @DisplayName("fetchContent: RestTemplate patlarsa null doner, null cache'lenir (tekrar denenmez)")
    void fetchContent_restTemplateThrows_returnsNullAndCaches() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(new RestClientException("boom"));

        assertThat(fetcher.fetchContent("https://example.com/err")).isNull();

        // Null da cache'lendigi icin ikinci cagri yeni istek YAPMAZ
        assertThat(fetcher.fetchContent("https://example.com/err")).isNull();
        verify(restTemplate, times(1)).exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class));
    }

    @Test
    @DisplayName("fetchContent: bos govde (0 byte) -> null")
    void fetchContent_emptyBody_returnsNull() {
        ResponseEntity<byte[]> resp = new ResponseEntity<>(new byte[0], new HttpHeaders(), 200);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class))).thenReturn(resp);

        assertThat(fetcher.fetchContent("https://example.com/empty")).isNull();
    }

    @Test
    @DisplayName("fetchContent: yalnizca kisa/junk paragraflar -> null")
    void fetchContent_onlyJunkParagraphs_returnsNull() {
        // Cok kisa (<50) + junk anahtar kelimeli paragraflar — densest cluster bos doner
        String html = "<html><body><p>Cerez politikasi</p><p>kisa</p>"
                + "<p>tum haklari saklidir</p></body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        assertThat(fetcher.fetchContent("https://example.com/junk")).isNull();
    }

    @Test
    @DisplayName("fetchOgImage: null url -> null; property-once-content-after deseni yakalanir")
    void fetchOgImage_extractsFromMeta() {
        assertThat(fetcher.fetchOgImage(null)).isNull();
        assertThat(fetcher.fetchOgImage("  ")).isNull();

        String html = "<html><head>"
                + "<meta property=\"og:image\" content=\"https://cdn.example.com/pic.jpg\">"
                + "</head><body></body></html>";
        stubHtml(html, null); // Content-Type yok -> meta-charset/UTF-8 fallback yolu

        String img = fetcher.fetchOgImage("https://example.com/img");
        assertThat(img).isEqualTo("https://cdn.example.com/pic.jpg");

        // Cache hit: ikinci cagri istek yapmaz
        assertThat(fetcher.fetchOgImage("https://example.com/img"))
                .isEqualTo("https://cdn.example.com/pic.jpg");
        verify(restTemplate, times(1)).exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class));
    }

    @Test
    @DisplayName("fetchOgImage: content-once-property-after deseni (ikinci pattern) yakalanir")
    void fetchOgImage_secondPattern() {
        String html = "<html><head>"
                + "<meta content=\"https://cdn.example.com/alt.png\" property=\"og:image\">"
                + "</head></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        assertThat(fetcher.fetchOgImage("https://example.com/img2"))
                .isEqualTo("https://cdn.example.com/alt.png");
    }

    @Test
    @DisplayName("fetchOgImage: og:image yoksa null")
    void fetchOgImage_noMeta_returnsNull() {
        String html = "<html><head><title>Yok</title></head><body><p>metin</p></body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        assertThat(fetcher.fetchOgImage("https://example.com/noimg")).isNull();
    }

    @Test
    @DisplayName("extractFromHtml: null/blank -> null (ag istegi yapilmaz)")
    void extractFromHtml_nullOrBlank_returnsNull() {
        assertThat(fetcher.extractFromHtml(null)).isNull();
        assertThat(fetcher.extractFromHtml("")).isNull();
        assertThat(fetcher.extractFromHtml("   ")).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("extractFromHtml: paragraflardan metin cikarir; script/style atilir")
    void extractFromHtml_paragraphPath() {
        String html = "<script>var x=1;</script>" + longParagraph();

        String out = fetcher.extractFromHtml(html);
        assertThat(out).isNotNull();
        assertThat(out).contains("Piyasalarda");
        assertThat(out).doesNotContain("var x=1");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("extractFromHtml: <p> yoksa duz metne duser (>=250 char)")
    void extractFromHtml_flatFallback() {
        StringBuilder sb = new StringBuilder("<div>");
        for (int i = 0; i < 6; i++) {
            sb.append("Bu duz bir metin govdesidir ve paragraf etiketi icermez tamam. ");
        }
        sb.append("</div>");

        String out = fetcher.extractFromHtml(sb.toString());
        assertThat(out).isNotNull();
        assertThat(out).contains("duz bir metin govdesidir");
    }

    @Test
    @DisplayName("extractFromHtml: cok kisa icerik (MIN_CONTENT alti) -> null")
    void extractFromHtml_tooShort_returnsNull() {
        assertThat(fetcher.extractFromHtml("<p>kisa metin</p>")).isNull();
    }
}
