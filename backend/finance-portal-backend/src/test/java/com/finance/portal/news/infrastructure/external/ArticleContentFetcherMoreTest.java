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
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Branş kapsamını artıran ek testler — mevcut ArticleContentFetcherTest'in DEĞMEDİĞİ
 * dalları hedefler: detectCharset arm'ları (header/meta/invalid/default), densest-cluster
 * gap-split + truncation, flat fallback truncation, looksLikeJunk (foreign-script mix +
 * çoklu domain), decodeEntities (sayısal hex/decimal/invalid + isimli bilinen/bilinmeyen +
 * çift-encode multi-pass), boş/null gövde, ikinci og:image deseni miss.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleContentFetcher — ek branş kapsamı (MoreTest)")
class ArticleContentFetcherMoreTest {

    @Mock
    RestTemplate restTemplate;

    private ArticleContentFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new ArticleContentFetcher(restTemplate);
    }

    /** Verilen byte gövde + header ile bir HTTP 200 yanıtı kurar. */
    private void stubBytes(byte[] body, HttpHeaders headers) {
        ResponseEntity<byte[]> resp = new ResponseEntity<>(body, headers, 200);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class))).thenReturn(resp);
    }

    /** UTF-8 byte gövdeli, opsiyonel Content-Type charset'li bir HTTP 200 yanıtı kurar. */
    private void stubHtml(String html, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        if (contentType != null) {
            headers.setContentType(contentType);
        }
        stubBytes(html.getBytes(StandardCharsets.UTF_8), headers);
    }

    /** N adet ~70 karakterlik anlamlı (junk olmayan) paragraf üretir. */
    private static String paragraphs(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append("<p>Piyasalarda bugun onemli gelismeler yasandi ve yatirimcilar dikkatli izledi tamam.</p>");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // fetchHtml: null gövde (length==0 değil, gerçek null) -> null
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("fetchContent: gövde null (resp.getBody()==null) -> null")
    void fetchContent_nullBody_returnsNull() {
        stubBytes(null, new HttpHeaders());
        assertThat(fetcher.fetchContent("https://example.com/nullbody")).isNull();
    }

    @Test
    @DisplayName("fetchOgImage: gövde null -> null (fetchHtml null döner)")
    void fetchOgImage_nullBody_returnsNull() {
        stubBytes(null, new HttpHeaders());
        assertThat(fetcher.fetchOgImage("https://example.com/img-nullbody")).isNull();
    }

    @Test
    @DisplayName("fetchOgImage: boş gövde (0 byte) -> null")
    void fetchOgImage_emptyBody_returnsNull() {
        stubBytes(new byte[0], new HttpHeaders());
        assertThat(fetcher.fetchOgImage("https://example.com/img-empty")).isNull();
    }

    // ---------------------------------------------------------------------
    // detectCharset: meta <meta charset=...> yolu (Content-Type header'ında charset YOK)
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("detectCharset: header charset yok -> <meta charset> okunur (geçerli charset)")
    void detectCharset_metaCharsetValid() {
        String html = "<html><head><meta charset=\"UTF-8\"></head><body>"
                + paragraphs(5) + "</body></html>";
        // Content-Type var ama charset YOK -> ct.getCharset()==null dalı, meta'ya düşer
        stubHtml(html, MediaType.valueOf("text/html"));

        String out = fetcher.fetchContent("https://example.com/meta-charset");
        assertThat(out).isNotNull();
        assertThat(out).contains("Piyasalarda");
    }

    @Test
    @DisplayName("detectCharset: <meta charset> geçersiz isim -> Charset.forName patlar, UTF-8'e düşer")
    void detectCharset_metaCharsetInvalid_fallsBackToUtf8() {
        String html = "<html><head><meta charset=\"NOTACHARSET-123\"></head><body>"
                + paragraphs(5) + "</body></html>";
        // Hiç Content-Type yok -> header dalı atlanır, meta bulunur ama forName patlar -> UTF-8
        stubHtml(html, null);

        String out = fetcher.fetchContent("https://example.com/meta-invalid");
        assertThat(out).isNotNull();
        assertThat(out).contains("Piyasalarda");
    }

    @Test
    @DisplayName("detectCharset: ne header ne meta charset -> UTF-8 varsayılan")
    void detectCharset_noHeaderNoMeta_defaultUtf8() {
        // Türkçe karakterli içerik; UTF-8 ile çözülürse 'ş','ç','ı' doğru gelir
        String html = "<html><head><title>baslik</title></head><body>"
                + "<p>Sirketin gelirleri arttı ve hisse senedi yukseldi cunku piyasa cok olumlu degerlendirdi.</p>"
                + "<p>Yatirimcilar onumuzdeki donemde sirketin buyumesini surdurecegini one surdu bugun.</p>"
                + "<p>Analistler hedef fiyatlari yukseltti ve alim tavsiyesi verdiler son raporlarinda iste.</p>"
                + "</body></html>";
        stubHtml(html, null);

        String out = fetcher.fetchContent("https://example.com/no-charset");
        assertThat(out).isNotNull();
        // UTF-8 ile doğru çözülen Türkçe karakter
        assertThat(out).contains("arttı");
    }

    // ---------------------------------------------------------------------
    // densestParagraphCluster: gap-split — iki küme, en yoğun (uzun) olanı seçilir
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("densestCluster: CLUSTER_GAP üstü boşlukla ayrılmış iki küme -> uzun küme seçilir")
    void densestCluster_gapSplit_picksLongerCluster() {
        // İlk küme: küçük (2 paragraf). Sonra >2000 karakterlik boşluk. Sonra büyük küme (6 paragraf).
        StringBuilder gap = new StringBuilder("<div>");
        for (int i = 0; i < 2500; i++) {
            gap.append('.');
        }
        gap.append("</div>");

        String html = "<html><body>"
                + paragraphs(2)       // küçük küme (best olur, sonra gap'te kapanır)
                + gap                  // > CLUSTER_GAP boşluk
                + paragraphs(6)        // büyük küme (final currentLen > bestLen)
                + "</body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        String out = fetcher.fetchContent("https://example.com/gap");
        assertThat(out).isNotNull();
        // İki paragraf arası \n\n; büyük küme 6 paragraf -> en az 5 ayraç
        int separators = out.split("\n\n").length;
        assertThat(separators).isGreaterThanOrEqualTo(6);
    }

    @Test
    @DisplayName("densestCluster: ilk küme daha uzun -> gap split sonrası ilk küme korunur")
    void densestCluster_gapSplit_picksFirstCluster() {
        StringBuilder gap = new StringBuilder("<div>");
        for (int i = 0; i < 2500; i++) {
            gap.append('.');
        }
        gap.append("</div>");

        String html = "<html><body>"
                + paragraphs(6)        // büyük küme (best olur)
                + gap                  // > CLUSTER_GAP boşluk -> currentLen<=bestLen, best değişmez
                + paragraphs(2)        // küçük son küme
                + "</body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        String out = fetcher.fetchContent("https://example.com/gap2");
        assertThat(out).isNotNull();
        assertThat(out.split("\n\n").length).isGreaterThanOrEqualTo(6);
    }

    // ---------------------------------------------------------------------
    // densestParagraphCluster: MAX_CONTENT (14000) üstü -> kesilir + "…"
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("densestCluster: MAX_CONTENT üstü içerik kesilir ve '…' ile biter")
    void densestCluster_overMaxContent_truncated() {
        // Her paragraf ~80 karakter; 14000'i aşmak için bolca paragraf
        String html = "<html><body>" + paragraphs(300) + "</body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        String out = fetcher.fetchContent("https://example.com/big");
        assertThat(out).isNotNull();
        assertThat(out).endsWith("…");
        // MAX_CONTENT(14000) + '…'(1)
        assertThat(out.length()).isLessThanOrEqualTo(14001);
        assertThat(out.length()).isGreaterThan(13000);
    }

    @Test
    @DisplayName("densestCluster: <50 karakterlik paragraflar elenir -> küme boş -> null")
    void densestCluster_allShortParagraphs_returnsNull() {
        // Her biri < 50 karakter, junk değil ama kısa -> hepsi atlanır -> best boş -> null
        String html = "<html><body><p>Kisa bir cumle.</p><p>Yine kisa.</p>"
                + "<p>Bu da kisa kaldi.</p></body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        assertThat(fetcher.fetchContent("https://example.com/short")).isNull();
    }

    @Test
    @DisplayName("densestCluster: paragraflar var ama toplam MIN_CONTENT altı -> null")
    void densestCluster_belowMinContent_returnsNull() {
        // Tek bir 50-249 karakter arası paragraf: cluster'a girer ama joined<MIN_CONTENT
        String para = "<p>Bu paragraf elli karakterden uzun ancak iki yuz elli karakterin altinda kalir tamam.</p>";
        String html = "<html><body>" + para + "</body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        assertThat(fetcher.fetchContent("https://example.com/min")).isNull();
    }

    // ---------------------------------------------------------------------
    // looksLikeJunk: yabancı yazı-sistemi karışımı (Kiril + Arap) -> junk
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("looksLikeJunk: Kiril+Arap karışık (dil menüsü) paragraf elenir -> sadece temiz metin kalır")
    void looksLikeJunk_foreignScriptMix_dropped() {
        // Junk paragraf: hem Kiril (Русский) hem Arap (العربية) -> hasForeignScriptMix=true
        String junk = "<p>EDITION secenekleri Русский العربية ve diger diller burada listelenir secim yapabilirsiniz.</p>";
        String html = "<html><body>" + junk + paragraphs(5) + "</body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        String out = fetcher.fetchContent("https://example.com/mix");
        assertThat(out).isNotNull();
        assertThat(out).contains("Piyasalarda");
        assertThat(out).doesNotContain("Русский");
    }

    @Test
    @DisplayName("looksLikeJunk: çok sayıda domain (>=2) içeren satır footer link listesi -> elenir")
    void looksLikeJunk_multipleDomains_dropped() {
        // İki+ alan adı içeren paragraf (footer menü) -> DOMAIN_TOKEN >=2 -> junk
        String junk = "<p>Diger sitelerimiz ntv.com.tr ntvspor.net ve cnnturk.com baglantilari asagidadir hemen bakin.</p>";
        String html = "<html><body>" + junk + paragraphs(5) + "</body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        String out = fetcher.fetchContent("https://example.com/domains");
        assertThat(out).isNotNull();
        assertThat(out).contains("Piyasalarda");
        assertThat(out).doesNotContain("ntvspor");
    }

    // ---------------------------------------------------------------------
    // hasForeignScriptMix: yalnızca Kiril (Arap yok) -> false (junk değil)
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("hasForeignScriptMix: yalnızca Kiril (Arap yok) -> junk değil, içerik korunur")
    void hasForeignScriptMix_cyrillicOnly_notJunk() {
        // Yeterince uzun, junk-keyword yok, tek domain yok, sadece Kiril var -> arabic=false dalı
        String para = "<p>Rusya ekonomisi hakkinda Русский dilinde bir alinti var ama metin Turkce devam ediyor genis sekilde tamam.</p>";
        String html = "<html><body>" + para + paragraphs(4) + "</body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        String out = fetcher.fetchContent("https://example.com/cyr");
        assertThat(out).isNotNull();
        // Kiril-only paragraf junk SAYILMADIĞI için içerikte kalmalı
        assertThat(out).contains("Русский");
    }

    // ---------------------------------------------------------------------
    // extractFromHtml: flat fallback MAX_CONTENT üstü -> kesilir + "…"
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("extractFromHtml: <p> yok + çok uzun düz metin -> MAX_CONTENT'te kesilir + '…'")
    void extractFromHtml_flatFallback_truncated() {
        StringBuilder sb = new StringBuilder("<div>");
        for (int i = 0; i < 400; i++) {
            sb.append("Bu duz metin govdesidir ve paragraf etiketi icermez yeterince uzun olsun diye tekrarlanir. ");
        }
        sb.append("</div>");

        String out = fetcher.extractFromHtml(sb.toString());
        assertThat(out).isNotNull();
        assertThat(out).endsWith("…");
        assertThat(out.length()).isLessThanOrEqualTo(14001);
    }

    @Test
    @DisplayName("extractFromHtml: paragraf yolu MAX_CONTENT üstü -> kesilir + '…'")
    void extractFromHtml_paragraphPath_truncated() {
        // paragraphsFrom non-null döner ve joined > MAX_CONTENT -> truncation dalı
        String html = paragraphs(300);
        String out = fetcher.extractFromHtml(html);
        assertThat(out).isNotNull();
        assertThat(out).endsWith("…");
        assertThat(out.length()).isLessThanOrEqualTo(14001);
    }

    @Test
    @DisplayName("extractFromHtml: <p> yok + düz metin de MIN_CONTENT altı -> null")
    void extractFromHtml_flatTooShort_returnsNull() {
        // <p> yok -> paragraphsFrom null; stripTags sonrası < 250 -> null
        assertThat(fetcher.extractFromHtml("<div>cok kisa duz metin</div>")).isNull();
    }

    // ---------------------------------------------------------------------
    // decodeEntities: sayısal (decimal + hex) + isimli (bilinen/bilinmeyen) + invalid
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("decodeEntities: ondalık & hex sayısal varlıklar çözülür")
    void decodeEntities_numericDecimalAndHex() {
        // &#304; = İ ; &#x131; = ı  -> "İstanbulı" beklenir; paragrafı MIN_CONTENT üstüne taşı
        String entity = "&#304;stanbul&#x131;"; // İstanbulı
        StringBuilder sb = new StringBuilder("<p>");
        sb.append(entity);
        for (int i = 0; i < 5; i++) {
            sb.append(" sehrinde bugun piyasalar hareketliydi ve yatirimcilar dikkatle izledi tamam.");
        }
        sb.append("</p>");
        String html = "<html><body>" + sb + "</body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        String out = fetcher.fetchContent("https://example.com/num");
        assertThat(out).isNotNull();
        assertThat(out).contains("İstanbulı");
    }

    @Test
    @DisplayName("decodeEntities: bilinmeyen sayısal kod-noktası fazlalığı catch -> ham bırakılır")
    void decodeEntities_invalidNumeric_keptRaw() {
        // &#xFFFFFFFF; -> Integer.parseInt(hex) overflow/NumberFormatException -> catch -> ham kalır
        // (Character.toChars'a varmadan parse patlar.) Ham "&#xFFFFFFFF;" metinde durur.
        StringBuilder sb = new StringBuilder("<p>Kod &#xFFFFFFFF; gecersizdir ");
        for (int i = 0; i < 5; i++) {
            sb.append("ve bu paragraf yeterince uzun olsun diye anlamli sekilde devam ediyor tamam.");
        }
        sb.append("</p>");
        String html = "<html><body>" + sb + "</body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        String out = fetcher.fetchContent("https://example.com/badnum");
        assertThat(out).isNotNull();
        assertThat(out).contains("&#xFFFFFFFF;");
    }

    @Test
    @DisplayName("decodeEntities: isimli bilinen varlık çözülür, bilinmeyen ham kalır")
    void decodeEntities_namedKnownAndUnknown() {
        // &uuml; -> ü (bilinen), &foobar; -> ham kalır (NAMED.get null)
        StringBuilder sb = new StringBuilder("<p>T&uuml;rkiye ve &foobar; ");
        for (int i = 0; i < 5; i++) {
            sb.append("ekonomisi hakkinda detayli bir analiz yazisi burada yer aliyor genisce tamam.");
        }
        sb.append("</p>");
        String html = "<html><body>" + sb + "</body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        String out = fetcher.fetchContent("https://example.com/named");
        assertThat(out).isNotNull();
        assertThat(out).contains("Türkiye");      // &uuml; çözüldü
        assertThat(out).contains("&foobar;");      // bilinmeyen ham kaldı
    }

    @Test
    @DisplayName("decodeEntities: çift-encode (&amp;ccedil;) çok-geçişte çözülür")
    void decodeEntities_doubleEncoded_multiPass() {
        // "Gu&amp;ccedil;lu" -> 1. geçiş "Gu&ccedil;lu" -> 2. geçiş "Guçlu"
        StringBuilder sb = new StringBuilder("<p>Gu&amp;ccedil;lu ");
        for (int i = 0; i < 5; i++) {
            sb.append("ekonomi haberleri burada uzun uzun anlatiliyor ve aciklamalar yapiliyor tamam.");
        }
        sb.append("</p>");
        String html = "<html><body>" + sb + "</body></html>";
        stubHtml(html, MediaType.valueOf("text/html;charset=UTF-8"));

        String out = fetcher.fetchContent("https://example.com/dbl");
        assertThat(out).isNotNull();
        assertThat(out).contains("Guçlu"); // çift-encode iki geçişte çözülür
    }

    // ---------------------------------------------------------------------
    // extractFromHtml: '&' içermeyen metin -> decodeEntities erken-dönüş (indexOf('&')<0)
    // (entity yokken de düz akış doğrulanır)
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("extractFromHtml: entity içermeyen sade paragraflar -> doğrudan döner")
    void extractFromHtml_noEntities_returnsContent() {
        String out = fetcher.extractFromHtml(paragraphs(5));
        assertThat(out).isNotNull();
        assertThat(out).contains("Piyasalarda");
        assertThat(out).doesNotContain("&");
    }
}
