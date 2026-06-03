package com.finance.portal.market.infrastructure.external.midas;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.market.application.stock.MidasStockDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MidasStockClient} testleri.
 *
 * <p>Midas URL'leri sınıf içinde sabit (hardcoded getmidas.com) olduğundan WireMock ile
 * yakalanamaz; ancak {@link RestTemplate} constructor bağımlılığı olduğu için doğrudan
 * Mockito ile stub'lanır. {@code exchange(...)} çağrısına kontrollü HTML gövdeli bir
 * {@link ResponseEntity} döndürülerek parse mantığı (sembol listesi + detay alanları)
 * ve hata dalları (boş gövde / 0 parse / exception) test edilir.
 * Entegrasyon log servisi mock (publish no-op).</p>
 */
class MidasStockClientTest {

    private RestTemplate restTemplate;
    private CentralIntegrationLogService integrationLog;
    private MidasStockClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        integrationLog = mock(CentralIntegrationLogService.class);
        client = new MidasStockClient(restTemplate, integrationLog);
    }

    /** Helper: stub the single exchange(...) call with a 200 + given HTML body. */
    private void stubHtml(String html) {
        ResponseEntity<String> resp = new ResponseEntity<>(html, HttpStatus.OK);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(resp);
    }

    // ── fetchSymbols ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchSymbols: HTML'den sembolleri parse eder, endeksleri (XU..) filtreler")
    void fetchSymbols_ok() {
        String html = "<html>"
                + "<a href=\"/canli-borsa/THYAO-hisse/\">THY</a>"
                + "<a href=\"/canli-borsa/asels-hisse\">ASELSAN</a>"   // lower-case → upper
                + "<a href=\"/canli-borsa/XU030-hisse/\">BIST30</a>"   // index → filtered out
                + "<a href=\"/canli-borsa/THYAO-hisse'\">dup</a>"      // duplicate
                + "</html>";
        stubHtml(html);

        List<String> symbols = client.fetchSymbols("xu030-bist-30-hisseleri");

        assertThat(symbols).containsExactly("THYAO.IS", "ASELS.IS");
    }

    @Test
    @DisplayName("fetchSymbols: null/blank path → ana liste URL'i kullanır, yine parse eder")
    void fetchSymbols_nullPath() {
        stubHtml("<a href=\"/canli-borsa/GARAN-hisse/\">x</a>");

        assertThat(client.fetchSymbols(null)).containsExactly("GARAN.IS");
        assertThat(client.fetchSymbols("")).containsExactly("GARAN.IS");
    }

    @Test
    @DisplayName("fetchSymbols: boş gövde → boş liste (empty-response dalı)")
    void fetchSymbols_emptyBody() {
        stubHtml("   ");
        assertThat(client.fetchSymbols("xu100")).isEmpty();
    }

    @Test
    @DisplayName("fetchSymbols: HTML var ama hiç sembol yok → boş liste (parse-failed dalı)")
    void fetchSymbols_zeroParsed() {
        stubHtml("<html><body>no stock links here</body></html>");
        assertThat(client.fetchSymbols("xu050")).isEmpty();
    }

    @Test
    @DisplayName("fetchSymbols: RestTemplate exception fırlatırsa → boş liste (catch dalı)")
    void fetchSymbols_exception() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("boom"));
        assertThat(client.fetchSymbols("xu030")).isEmpty();
    }

    // ── fetchDetail ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchDetail: zengin HTML → tüm temel alanlar parse edilir")
    void fetchDetail_ok() {
        String html = "<html>"
                // logo
                + "<img src=\"https://webcdn.getmidas.com/uploads/a1cap-logo-big.png\"/>"
                // name (h1 contains 'Hisse', cleaned after ' - ')
                + "<h1>A1CAP Hisse - A1 Capital Yatırım</h1>"
                // current price: value element just before the 'Güncel Fiyat' label
                + "<p class=\"val\">₺43,50</p><span class=\"title\">Güncel Fiyat</span>"
                // daily volume: value before 'Günlük İşlem Hacmi'
                + "<p class=\"val\">₺1.250.000</p><span>Günlük İşlem Hacmi</span>"
                // daily change percent: ( ..% )
                + "<span>(2,35%)</span>"
                // financial table values (label then ₺value within 400 chars)
                + "<td>Günlük Değişim (TL)</td><td>₺1,05</td>"
                + "<td>Haftalık En Yüksek</td><td>₺44,00</td>"
                + "<td>Haftalık En Düşük</td><td>₺40,00</td>"
                + "<td>Aylık En Yüksek</td><td>₺50,00</td>"
                + "<td>Aylık En Düşük</td><td>₺38,00</td>"
                + "<td>Açılış Fiyatı</td><td>₺42,00</td>"
                + "<td>Alış</td><td>₺43,40</td>"
                + "<td>Satış</td><td>₺43,60</td>"
                + "<td>Tavan</td><td>₺47,00</td>"
                + "<td>Taban</td><td>₺39,00</td>"
                + "<td>Piyasa Değeri</td><td>₺5.000.000.000</td>"
                + "<td>Sermaye</td><td>₺100.000.000</td>"
                + "<td>F/K</td><td>8,50</td>"
                + "<td>PD/DD</td><td>1,20</td>"
                + "<td>Halka Açıklık Oranı (%)</td><td>35</td>"
                + "<td>Yabancı Oranı (%)</td><td>12</td>"
                + "<td>Volatilite</td><td>2,1</td>"
                // company fields
                + "<div>CEO</div><div>Mehmet Yılmaz</div>"
                + "<div>Çalışan Sayısı</div><div>540</div>"
                + "<div>Kuruluş Tarihi</div><div>1996</div>"
                + "<div>Halka Arz Tarihi</div><div>2021</div>"
                + "<div>Sektör</div><div>Finansal Kuruluşlar</div>"
                + "<div>Adres</div><div>İstanbul Türkiye</div>"
                + "<div>Merkez</div><div>Türkiye</div>"
                // description
                + "<h2>Hakkında</h2>"
                + "<p>A1 Capital Yatırım Menkul Değerler aracı kurum olarak sermaye piyasalarinda "
                + "uzun yıllardır faaliyet gösteren köklü bir kurumdur ve müşterilerine hizmet verir.</p>"
                // shareholders
                + "<h2>Ortaklık Yapısı</h2>"
                + "<table><tr><td>Halka Açık Kısım</td><td>35,00</td></tr>"
                + "<tr><td>Diğer Ortaklar</td><td>65,00</td></tr></table>"
                // plain value placed at the very end → no ₺ in its 400-char window → '>num</' branch
                + "<td>Günlük Hacim (Lot)</td><td>29.000</td>"
                + "</html>";
        stubHtml(html);

        MidasStockDetail d = client.fetchDetail("A1CAP.IS");

        assertThat(d).isNotNull();
        assertThat(d.getSymbol()).isEqualTo("A1CAP");
        assertThat(d.getName()).isEqualTo("A1 Capital Yatırım");
        assertThat(d.getLogoUrl()).contains("a1cap-logo-big.png");
        assertThat(d.getCurrentPrice()).isEqualTo("₺43,50");
        assertThat(d.getDailyVolume()).isEqualTo("₺1.250.000");
        assertThat(d.getDailyChangePercent()).isEqualTo("2,35%");
        assertThat(d.getDailyChange()).isEqualTo("₺1,05");
        assertThat(d.getWeeklyHigh()).isEqualTo("₺44,00");
        assertThat(d.getWeeklyLow()).isEqualTo("₺40,00");
        assertThat(d.getMonthlyHigh()).isEqualTo("₺50,00");
        assertThat(d.getMonthlyLow()).isEqualTo("₺38,00");
        assertThat(d.getOpenPrice()).isEqualTo("₺42,00");
        assertThat(d.getBid()).isEqualTo("₺43,40");
        assertThat(d.getAsk()).isEqualTo("₺43,60");
        assertThat(d.getUpperLimit()).isEqualTo("₺47,00");
        assertThat(d.getLowerLimit()).isEqualTo("₺39,00");
        assertThat(d.getVolumeLot()).isEqualTo("29.000");
        assertThat(d.getMarketCap()).isEqualTo("₺5.000.000.000");
        assertThat(d.getCapital()).isEqualTo("₺100.000.000");
        assertThat(d.getPeRatio()).isEqualTo("8,50");
        assertThat(d.getPbRatio()).isEqualTo("1,20");
        assertThat(d.getCeo()).isEqualTo("Mehmet Yılmaz");
        assertThat(d.getSector()).isEqualTo("Finansal Kuruluşlar");
        assertThat(d.getDescription()).isNotBlank();
        assertThat(d.getShareholders()).isNotEmpty();
        assertThat(d.getShareholders().get(0).getSharePercent()).isEqualTo("35,00");
    }

    @Test
    @DisplayName("fetchDetail: minimal HTML (name+price yok) → detail döner ama key alanlar null (parse-failed dalı)")
    void fetchDetail_missingKeyFields() {
        stubHtml("<html><body><div>Tamamen ilgisiz içerik</div></body></html>");

        MidasStockDetail d = client.fetchDetail("garan");

        assertThat(d).isNotNull();
        assertThat(d.getSymbol()).isEqualTo("GARAN");
        assertThat(d.getName()).isNull();
        assertThat(d.getCurrentPrice()).isNull();
        assertThat(d.getShareholders()).isEmpty();
    }

    @Test
    @DisplayName("fetchDetail: boş gövde → null (empty-response dalı)")
    void fetchDetail_emptyBody() {
        stubHtml("");
        assertThat(client.fetchDetail("THYAO.IS")).isNull();
    }

    @Test
    @DisplayName("fetchDetail: RestTemplate exception → null (catch dalı)")
    void fetchDetail_exception() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("network down"));
        assertThat(client.fetchDetail("asels.is")).isNull();
    }

    @Test
    @DisplayName("fetchDetail: sadece isim var fiyat yok → name dolu, price null (kısmi parse)")
    void fetchDetail_namePresentPriceMissing() {
        stubHtml("<html><h1>THYAO Hisse - Türk Hava Yolları</h1></html>");

        MidasStockDetail d = client.fetchDetail("THYAO");

        assertThat(d).isNotNull();
        assertThat(d.getName()).isEqualTo("Türk Hava Yolları");
        assertThat(d.getCurrentPrice()).isNull();
    }
}
