package com.finance.portal.news.infrastructure.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link GoogleFreeTranslationAdapter} branch-coverage testleri.
 *
 * <p>Constructor bir {@link RestTemplateBuilder} alıp {@link RestTemplate}'i içeride
 * fluent zincirle kurar; builder mock'lanıp {@code build()} mock RestTemplate döndürür.
 * ENDPOINT sabit (hardcoded googleapis) olduğundan WireMock yerine doğrudan Mockito ile
 * {@code exchange(URI, ...)} stub'lanır. Kapsanan dallar: blank/null kısa-devre,
 * aynı-dil atlama, hedef-dil boş, cache hit, mutlu çeviri (iç içe diziler), boş gövde,
 * hata statüsü → orijinal, bozuk JSON → orijinal, çeviri-sonucu-blank → orijinal,
 * uzun metin parçalama (chunk).</p>
 */
@DisplayName("GoogleFreeTranslationAdapter — gtx çeviri parse + best-effort hata dalları")
class GoogleFreeTranslationAdapterTest {

    private RestTemplate restTemplate;
    private GoogleFreeTranslationAdapter adapter;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        // Fluent zincir: setConnectTimeout(...).setReadTimeout(...).build()
        lenient().when(builder.setConnectTimeout(any(Duration.class))).thenReturn(builder);
        lenient().when(builder.setReadTimeout(any(Duration.class))).thenReturn(builder);
        lenient().when(builder.build()).thenReturn(restTemplate);
        adapter = new GoogleFreeTranslationAdapter(builder, new ObjectMapper());
    }

    /** 200 + verilen JSON gövdeli (UTF-8 byte) bir exchange(URI,...) yanıtı kurar. */
    private void stubBody(String json) {
        ResponseEntity<byte[]> resp =
                new ResponseEntity<>(json.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class))).thenReturn(resp);
    }

    // ── Kısa-devre dalları (ağ isteği yapılmaz) ──────────────────────────────

    @Test
    @DisplayName("translate: null metin -> aynen null döner, RestTemplate'e dokunulmaz")
    void translate_nullText_returnsNull() {
        assertThat(adapter.translate(null, "en", "tr")).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("translate: blank metin -> aynen döner")
    void translate_blankText_returnsSame() {
        assertThat(adapter.translate("   ", "en", "tr")).isEqualTo("   ");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("translate: null targetLang -> orijinal metin döner")
    void translate_nullTarget_returnsOriginal() {
        assertThat(adapter.translate("hello", "en", null)).isEqualTo("hello");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("translate: targetLang sadece boşluk (norm -> boş) -> orijinal döner")
    void translate_blankTarget_returnsOriginal() {
        assertThat(adapter.translate("hello", "en", "   ")).isEqualTo("hello");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("translate: kaynak == hedef dil (norm sonrası eşit) -> orijinal döner")
    void translate_sameLanguage_returnsOriginal() {
        // norm trim+lowercase yapar: " EN " ve "en" eşitlenir
        assertThat(adapter.translate("hello", " EN ", "en")).isEqualTo("hello");
        verifyNoInteractions(restTemplate);
    }

    // ── Mutlu yol + cache ────────────────────────────────────────────────────

    @Test
    @DisplayName("translate: iç içe dizilerden çeviri parse edilir ve cache'lenir")
    void translate_happyPath_parsesNestedArraysAndCaches() {
        stubBody("[[[\"merhaba dunya\",\"hello world\",null,null,1]],null,\"en\"]");

        String first = adapter.translate("hello world", "en", "tr");
        assertThat(first).isEqualTo("merhaba dunya");

        // İkinci çağrı cache'ten gelir — exchange yalnızca 1 kez çağrılır
        String second = adapter.translate("hello world", "en", "tr");
        assertThat(second).isEqualTo("merhaba dunya");
        verify(restTemplate, times(1)).exchange(any(URI.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class));
    }

    @Test
    @DisplayName("translate: birden çok segment birleştirilir (sb append döngüsü)")
    void translate_multiSegment_concatenates() {
        stubBody("[[[\"Bir. \",\"One. \"],[\"Iki.\",\"Two.\"]],null,\"en\"]");

        assertThat(adapter.translate("One. Two.", "en", "tr")).isEqualTo("Bir. Iki.");
    }

    @Test
    @DisplayName("translate: kaynak dil boş -> sl=auto dalı; çeviri yine döner")
    void translate_blankSource_autoDetectBranch() {
        // sourceLang null -> norm -> "" -> sl=auto; src!=tgt (""!="tr") koşulu sağlanır
        stubBody("[[[\"selam\",\"hi\"]]]");

        assertThat(adapter.translate("hi", null, "tr")).isEqualTo("selam");
    }

    // ── Gövde / statü hata dalları (best-effort → orijinal) ───────────────────

    @Test
    @DisplayName("translate: boş gövde (0 byte) -> chunk(orijinal) döner")
    void translate_emptyBody_returnsOriginal() {
        ResponseEntity<byte[]> resp = new ResponseEntity<>(new byte[0], HttpStatus.OK);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class))).thenReturn(resp);

        assertThat(adapter.translate("hello", "en", "tr")).isEqualTo("hello");
    }

    @Test
    @DisplayName("translate: null gövde -> chunk(orijinal) döner")
    void translate_nullBody_returnsOriginal() {
        byte[] nullBody = null;
        ResponseEntity<byte[]> resp = new ResponseEntity<>(nullBody, HttpStatus.OK);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class))).thenReturn(resp);

        assertThat(adapter.translate("hello", "en", "tr")).isEqualTo("hello");
    }

    @Test
    @DisplayName("translate: hata statüsü (5xx exception) -> orijinal metin döner")
    void translate_serverError_returnsOriginal() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "boom", null, null, null));

        assertThat(adapter.translate("hello", "en", "tr")).isEqualTo("hello");
    }

    @Test
    @DisplayName("translate: RestClientException (timeout vb.) -> orijinal metin döner")
    void translate_restClientException_returnsOriginal() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(new RestClientException("connection reset"));

        assertThat(adapter.translate("hello", "en", "tr")).isEqualTo("hello");
    }

    // ── Bozuk / beklenmedik JSON dalları (chunk fallback) ─────────────────────

    @Test
    @DisplayName("translate: bozuk JSON (parse hatası) -> chunk(orijinal) döner")
    void translate_malformedJson_returnsOriginal() {
        stubBody("this is not json {");

        assertThat(adapter.translate("hello", "en", "tr")).isEqualTo("hello");
    }

    @Test
    @DisplayName("translate: root[0] dizi değil -> chunk(orijinal) döner")
    void translate_rootZeroNotArray_returnsOriginal() {
        // root.get(0) = string -> isArray() false -> chunk dönülür
        stubBody("[\"notArray\",null]");

        assertThat(adapter.translate("hello", "en", "tr")).isEqualTo("hello");
    }

    @Test
    @DisplayName("translate: root[0] null/eksik -> chunk(orijinal) döner")
    void translate_rootZeroNull_returnsOriginal() {
        // Boş dizi -> root.get(0) == null -> segments null -> chunk
        stubBody("[]");

        assertThat(adapter.translate("hello", "en", "tr")).isEqualTo("hello");
    }

    @Test
    @DisplayName("translate: segment null/dizi-değil/ilk-eleman-null atlanır -> sb boş -> chunk")
    void translate_segmentsAllSkipped_returnsOriginal() {
        // Segmentler: null, düz string (dizi değil), [null] (ilk eleman null) -> hiçbiri eklenmez
        stubBody("[[null,\"plain\",[null,\"x\"]],null,\"en\"]");

        assertThat(adapter.translate("hello", "en", "tr")).isEqualTo("hello");
    }

    @Test
    @DisplayName("translate: çeviri sonucu blank (sb dolu ama boşluk) -> orijinal metin döner")
    void translate_translatedBlank_returnsOriginal() {
        // seg.get(0) = " " -> sb=" " (length>0 -> chunk değil " " döner) -> result.isBlank() -> text
        stubBody("[[[\" \",\"hello\"]],null,\"en\"]");

        assertThat(adapter.translate("hello", "en", "tr")).isEqualTo("hello");
    }

    // ── Uzun metin: chunk() bölme dalları ─────────────────────────────────────

    @Test
    @DisplayName("translate: 1800 üzeri metin parçalara bölünür, her parça için exchange çağrılır")
    void translate_longText_chunkedMultipleCalls() {
        // Her exchange çağrısına çevirinin ilk segmentini geri döndüren genel stub.
        // (Parça başına farklı URI ama hep aynı gövdeyi verir.)
        stubBody("[[[\"X\",\"x\"]],null,\"en\"]");

        // ~2100 karakter: ilk parçada (~1800) bir nokta sınırı bulunur, ikinci parça kalan.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("Bu uzun bir cumledir ve bircok kez tekrarlanir boylece sinir asilir. ");
        }
        String longText = sb.toString();
        assertThat(longText.length()).isGreaterThan(1800);

        String out = adapter.translate(longText, "en", "tr");
        // En az iki parça -> en az iki "X" birleşir
        assertThat(out).startsWith("X").contains("XX");
        verify(restTemplate, times(2)).exchange(any(URI.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class));
    }
}
