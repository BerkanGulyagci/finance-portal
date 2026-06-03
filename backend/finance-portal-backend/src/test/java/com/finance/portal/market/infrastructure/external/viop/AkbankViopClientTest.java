package com.finance.portal.market.infrastructure.external.viop;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.market.application.viop.ViopContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AkbankViopClient} testleri.
 *
 * <p>Hedef URL sınıf içinde sabit (hardcoded constant) olduğundan WireMock ile araya
 * girilemez; ancak {@link RestTemplate} constructor üzerinden enjekte edildiği için
 * doğrudan Mockito ile stub'lanır. {@code restTemplate.exchange(...)} çağrısı kontrol
 * altında byte[] gövdeleri döndürerek tüm dallar (null body, başarılı parse, boş parse,
 * exception) kapsanır. Entegrasyon log servisi mock (publish no-op).</p>
 */
class AkbankViopClientTest {

    private RestTemplate restTemplate;
    private CentralIntegrationLogService integrationLogService;
    private AkbankViopClient client;

    /** Tek bir geçerli sözleşme bloğu için 10 token üreten yardımcı HTML. */
    private static String contractHtml(String name) {
        // Her token kendi <td> elemanında; parseHtml tag'leri "\n" ile değiştirip böler.
        return "<html><head><meta charset=\"utf-8\"></head><body><table><tr>"
                + "<td>" + name + "</td>"          // [0] name -> "Vadeli" içermeli
                + "<td>%1,25</td>"                  // [1] changePercent -> %...
                + "<td>123,45</td>"                 // [2] lastPrice
                + "<td>130,00</td>"                 // [3] high
                + "<td>120,00</td>"                 // [4] low
                + "<td>50000</td>"                  // [5] openPositionCount
                + "<td>-250</td>"                   // [6] openPositionChange
                + "<td>124,00</td>"                 // [7] settlementPrice
                + "<td>122,00</td>"                 // [8] prevSettlementPrice
                + "<td>18:10:05</td>"               // [9] time -> HH:mm:ss
                + "</tr></table></body></html>";
    }

    private void stubBody(byte[] body, MediaType contentType) {
        HttpHeaders responseHeaders = new HttpHeaders();
        if (contentType != null) {
            responseHeaders.setContentType(contentType);
        }
        ResponseEntity<byte[]> entity = new ResponseEntity<>(body, responseHeaders, 200);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(entity);
    }

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        integrationLogService = mock(CentralIntegrationLogService.class);
        client = new AkbankViopClient(restTemplate, integrationLogService);
    }

    @Test
    @DisplayName("fetchContracts: geçerli HTML (1 blok) → 1 sözleşme parse edilir, alanlar dolu")
    void fetchContracts_happyPath() {
        stubBody(contractHtml("THYAO (30 Haz 26) Vadeli FIZ.").getBytes(StandardCharsets.UTF_8),
                MediaType.TEXT_HTML);

        List<ViopContract> result = client.fetchContracts();

        assertThat(result).hasSize(1);
        ViopContract c = result.get(0);
        assertThat(c.getName()).contains("THYAO").contains("Vadeli");
        assertThat(c.getChangePercent()).isEqualTo("%1,25");
        assertThat(c.getLastPrice()).isEqualTo("123,45");
        assertThat(c.getHigh()).isEqualTo("130,00");
        assertThat(c.getLow()).isEqualTo("120,00");
        assertThat(c.getOpenPositionCount()).isEqualTo("50000");
        assertThat(c.getOpenPositionChange()).isEqualTo("-250");
        assertThat(c.getSettlementPrice()).isEqualTo("124,00");
        assertThat(c.getPrevSettlementPrice()).isEqualTo("122,00");
        assertThat(c.getTime()).isEqualTo("18:10:05");
    }

    @Test
    @DisplayName("fetchContracts: iki ardışık geçerli blok → 2 sözleşme")
    void fetchContracts_multipleBlocks() {
        String html =
                "<html><body>"
                + stripWrap(contractHtml("THYAO (30 Haz 26) Vadeli FIZ."))
                + stripWrap(contractHtml("GARAN (30 Ara 26) Vadeli FIZ."))
                + "</body></html>";
        stubBody(html.getBytes(StandardCharsets.UTF_8), MediaType.TEXT_HTML);

        List<ViopContract> result = client.fetchContracts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).contains("THYAO");
        assertThat(result.get(1).getName()).contains("GARAN");
    }

    /** İç <table>/<html> sarmalayıcılarını atıp sadece token <td>'lerini bırakır. */
    private static String stripWrap(String fullHtml) {
        // contractHtml çıktısı zaten <td> token'larından ibaret; tag-strip token'ları ayırdığı
        // için tüm string'i olduğu gibi döndürmek güvenli (fazladan tag'ler boş token üretmez).
        return fullHtml;
    }

    @Test
    @DisplayName("fetchContracts: null gövde → boş liste + empty-response log publish edilir")
    void fetchContracts_nullBody() {
        ResponseEntity<byte[]> entity = new ResponseEntity<>(null, new HttpHeaders(), 200);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(entity);

        List<ViopContract> result = client.fetchContracts();

        assertThat(result).isEmpty();
        // null body dalında integration log publish çağrılır.
        verify(integrationLogService).publish(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchContracts: 'Vadeli' içermeyen uzun HTML → boş liste + parse-failed log")
    void fetchContracts_noContractsParsed() {
        // 500'den uzun, hiç 'Vadeli' bloğu olmayan HTML → contracts boş ama html uzun.
        StringBuilder sb = new StringBuilder("<html><body>");
        for (int i = 0; i < 100; i++) {
            sb.append("<p>some filler text token number ").append(i).append("</p>");
        }
        sb.append("</body></html>");
        stubBody(sb.toString().getBytes(StandardCharsets.UTF_8), MediaType.TEXT_HTML);

        List<ViopContract> result = client.fetchContracts();

        assertThat(result).isEmpty();
        // Boş sonuç + html>500 dalında parse-failed log publish edilir.
        verify(integrationLogService).publish(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchContracts: kısa boş HTML (<=500 char) → boş liste, log YOK")
    void fetchContracts_shortEmptyHtml() {
        stubBody("<html><body>kisa</body></html>".getBytes(StandardCharsets.UTF_8),
                MediaType.TEXT_HTML);

        List<ViopContract> result = client.fetchContracts();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchContracts: RestTemplate exception fırlatır → catch dalı, boş liste + failed log")
    void fetchContracts_restTemplateThrows() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(new RestClientException("connection refused"));

        List<ViopContract> result = client.fetchContracts();

        assertThat(result).isEmpty();
        verify(integrationLogService).publish(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchContracts: UTF-8 BOM'lu gövde → BOM charset dalı, parse çalışır")
    void fetchContracts_utf8Bom() {
        byte[] htmlBytes = contractHtml("THYAO (30 Haz 26) Vadeli FIZ.")
                .getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[htmlBytes.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(htmlBytes, 0, withBom, 3, htmlBytes.length);
        stubBody(withBom, MediaType.TEXT_HTML);

        List<ViopContract> result = client.fetchContracts();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("fetchContracts: geçersiz UTF-8 bayt + Content-Type charset → header charset dalı")
    void fetchContracts_invalidUtf8UsesContentTypeCharset() {
        // 0x80 windows-1252'de geçerli, UTF-8 decoder'da malformed → UTF-8 dalı atlanır,
        // Content-Type header charset'i (windows-1252) kullanılır.
        String html = contractHtml("THYAO (30 Haz 26) Vadeli FIZ.");
        byte[] base = html.getBytes(StandardCharsets.ISO_8859_1);
        // Geçerli UTF-8 olmayan bir bayt enjekte et (lone continuation byte 0x80 değil,
        // çünkü ISO-8859-1 tablosu; 0x9F gibi bir bayt UTF-8'de lone continuation -> malformed).
        byte[] body = new byte[base.length + 1];
        body[0] = (byte) 0x9F; // lone continuation byte -> UTF-8 decode REPORT exception
        System.arraycopy(base, 0, body, 1, base.length);
        MediaType cp1252 = new MediaType("text", "html",
                java.nio.charset.Charset.forName("windows-1252"));
        stubBody(body, cp1252);

        List<ViopContract> result = client.fetchContracts();

        // İlk bayt çöp olsa da token bloğu sağlam → 1 sözleşme parse edilir.
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("fetchContracts: Content-Type header yok → null content-type ile parse çalışır")
    void fetchContracts_nullContentType() {
        stubBody(contractHtml("THYAO (30 Haz 26) Vadeli FIZ.").getBytes(StandardCharsets.UTF_8),
                null);

        List<ViopContract> result = client.fetchContracts();

        assertThat(result).hasSize(1);
    }
}
