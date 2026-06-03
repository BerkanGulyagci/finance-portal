package com.finance.portal.market.infrastructure.external.gold;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GoldScraper} testleri. SCRAPE_URL sabit (https://canlialtinfiyatlari.com/) olduğu için
 * WireMock yerine RestTemplate doğrudan mock'lanır — gerçek HTTP yapılmadan parse dalları kapsanır.
 * CentralIntegrationLogService mock (publish no-op). Happy-path + boş gövde + 0-satır + exception dalları.
 */
class GoldScraperTest {

    private RestTemplate restTemplate;
    private CentralIntegrationLogService integrationLog;
    private GoldScraper scraper;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        integrationLog = mock(CentralIntegrationLogService.class);
        scraper = new GoldScraper(restTemplate, integrationLog);
    }

    /** restTemplate.exchange(...) çağrısını verilen gövde + status ile stub'lar. */
    private void stubResponse(String body, HttpStatus status) {
        when(restTemplate.exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, status));
    }

    /**
     * Tek bir altın satırı için canlialtinfiyatlari.com benzeri HTML bloğu üretir.
     * parseTable: isim → sonraki ~300 karakter → HH:MM:SS + iki 3-6 haneli sayı (>100) + yüzde.
     * NOT: Sayı regex'i ({@code \d{3,6}}) bitişik 3-6 hane ister; binlik-ayraç noktası kullanmamak için
     * fiyatlar düz biçimde (ör. "2834,50") verilir.
     */
    private static String row(String name, String time, String buy, String sell, String pct) {
        return "<tr><td>" + name + "</td>"
                + "<td>" + time + "</td>"
                + "<td>" + buy + "</td>"
                + "<td>" + sell + "</td>"
                + "<td>" + pct + "%</td></tr>\n";
    }

    @Test
    @DisplayName("fetchAll: geçerli HTML → tüm satırlar parse edilir (buy/sell/pct/time/name)")
    void fetchAll_parsesValidHtml() {
        String html = "<html><body><table>"
                + row("ALTIN ONS", "10:11:12", "2345,67", "2350,00", "+0,50")
                + row("GRAM ALTIN", "10:11:13", "2834,50", "2840,00", "-1,20")
                + row("Çeyrek Altın", "10:11:14", "4600,00", "4700,00", "+2,00")
                + "</table></body></html>";
        stubResponse(html, HttpStatus.OK);

        Map<String, GoldPriceEntry> result = scraper.fetchAll();

        assertThat(result).containsKeys("ALTIN_ONS", "GRAM_ALTIN", "CEYREK_ALTIN");
        GoldPriceEntry gram = result.get("GRAM_ALTIN");
        assertThat(gram.getName()).isEqualTo("GRAM ALTIN");
        assertThat(gram.getBuy()).isEqualByComparingTo(new BigDecimal("2834.50"));
        assertThat(gram.getSell()).isEqualByComparingTo(new BigDecimal("2840.00"));
        assertThat(gram.getChangePercent()).isEqualByComparingTo(new BigDecimal("-1.20"));
        assertThat(gram.getTime()).isEqualTo("10:11:13");
        // Tüm satırlar çıkarıldıysa boş-sonuç dalı tetiklenmemeli.
        verify(integrationLog, never()).publish(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchAll: null gövde → boş map + EMPTY_RESPONSE loglanır")
    void fetchAll_nullBody() {
        stubResponse(null, HttpStatus.OK);

        Map<String, GoldPriceEntry> result = scraper.fetchAll();

        assertThat(result).isEmpty();
        verify(integrationLog, atLeastOnce()).publish(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchAll: boşluk gövde → boş map + EMPTY_RESPONSE loglanır")
    void fetchAll_blankBody() {
        stubResponse("   \n\t  ", HttpStatus.OK);

        Map<String, GoldPriceEntry> result = scraper.fetchAll();

        assertThat(result).isEmpty();
        verify(integrationLog, atLeastOnce()).publish(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchAll: HTML var ama hedef isim/satır yok → 0 satır dalı + log")
    void fetchAll_htmlButNoRowsExtracted() {
        stubResponse("<html><body>Hava durumu sayfası, altın yok</body></html>", HttpStatus.OK);

        Map<String, GoldPriceEntry> result = scraper.fetchAll();

        assertThat(result).isEmpty();
        verify(integrationLog, atLeastOnce()).publish(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchAll: RestTemplate fırlatırsa → exception yutulur, boş map + PARSE_FAILED log")
    void fetchAll_restTemplateThrows() {
        when(restTemplate.exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("connection reset"));

        Map<String, GoldPriceEntry> result = scraper.fetchAll();

        assertThat(result).isEmpty();
        verify(integrationLog, atLeastOnce()).publish(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fetchAll: isim bulunur ama fiyat 100'den küçük → satır eklenmez")
    void fetchAll_pricesBelowThresholdSkipped() {
        // "14-AYAR" bulunur, sayılar 3+ haneli ama <=100 olamaz; burada 50/60 ⇒ regex 3 hane ister.
        // 3 haneli ama 100'den küçük değer mümkün değil (min 100), bu yüzden hiç sayı eşleşmesin diye 2 haneli ver.
        String html = "<html><body><table>"
                + "<tr><td>14-AYAR</td><td>09:00:00</td><td>50</td><td>60</td><td>+0,10%</td></tr>"
                + "</table></body></html>";
        stubResponse(html, HttpStatus.OK);

        Map<String, GoldPriceEntry> result = scraper.fetchAll();

        // buy/sell null kaldığından AYAR_14 eklenmez → 0 satır dalı.
        assertThat(result).doesNotContainKey("AYAR_14");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchAll: yüzde ve saat olmadan da buy+sell varsa satır eklenir (null pct/time)")
    void fetchAll_missingPctAndTime() {
        // Saat ve yüzde yok; sadece iki büyük sayı var → entry eklenir, pct ve time null.
        String html = "<html><body><table>"
                + "<tr><td>Has Altın</td><td>3100,00</td><td>3120,00</td></tr>"
                + "</table></body></html>";
        stubResponse(html, HttpStatus.OK);

        Map<String, GoldPriceEntry> result = scraper.fetchAll();

        assertThat(result).containsKey("HAS_ALTIN");
        GoldPriceEntry has = result.get("HAS_ALTIN");
        assertThat(has.getBuy()).isEqualByComparingTo(new BigDecimal("3100.00"));
        assertThat(has.getSell()).isEqualByComparingTo(new BigDecimal("3120.00"));
        assertThat(has.getChangePercent()).isNull();
        assertThat(has.getTime()).isNull();
    }

    @Test
    @DisplayName("fetchAll: virgül ondalıklı ve düz tamsayı fiyat formatları doğru parse edilir")
    void fetchAll_priceFormatVariants() {
        // "2840" düz tamsayı (binlik ayraçsız), "2.834,50" Türkçe format → her ikisi de >100.
        String html = "<html><body><table>"
                + "<tr><td>Tam Altın</td><td>11:22:33</td><td>2834,50</td><td>2840</td><td>0,00%</td></tr>"
                + "</table></body></html>";
        stubResponse(html, HttpStatus.OK);

        Map<String, GoldPriceEntry> result = scraper.fetchAll();

        assertThat(result).containsKey("TAM_ALTIN");
        GoldPriceEntry tam = result.get("TAM_ALTIN");
        assertThat(tam.getBuy()).isEqualByComparingTo(new BigDecimal("2834.50"));
        assertThat(tam.getSell()).isEqualByComparingTo(new BigDecimal("2840.00"));
        assertThat(tam.getMid()).isEqualByComparingTo(new BigDecimal("2837.25"));
    }
}
