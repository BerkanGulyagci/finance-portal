package com.finance.portal.market.infrastructure.bond.hmb;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Birim test: {@link HmbIsinListProvider#refreshFromXlsx(String)} için
 *  — cumulative union (eski ISIN'ler korunur)
 *  — yanlış/alakasız xlsx reddi (min sayı / TR prefix oranı / overlap)
 *  — başarılı güncelleme akışı.
 *
 * Network kullanılmaz: RestTemplate stub ile in-memory üretilmiş xlsx (zip + sharedStrings.xml) döner.
 */
@ExtendWith(MockitoExtension.class)
class HmbIsinListProviderTest {

    // Geçerli HMB Eurobond URL pattern'ı — filename'de "Dis_Borc_Stoku" anahtar kelimesi içerir.
    private static final String URL =
            "https://ms.hmb.gov.tr/uploads/2026/05/Merkezi_Yonetim_Dis_Borc_Stoku_Tahvil_Listesi-test.xlsx";

    @Mock RestTemplate restTemplate;
    @Mock CentralIntegrationLogService integrationLog;

    private HmbIsinListProvider provider;

    @BeforeEach
    void setUp() {
        provider = new HmbIsinListProvider(restTemplate, integrationLog);
    }

    // ---- helpers ------------------------------------------------------------

    /** Aktif ISIN listesini reflection ile set eder (PostConstruct seed'ini bypass et). */
    private void setActiveIsins(List<String> isins) throws Exception {
        Field f = HmbIsinListProvider.class.getDeclaredField("activeIsins");
        f.setAccessible(true);
        f.set(provider, List.copyOf(isins));
    }

    /** Verilen ISIN'leri içeren minimal bir xlsx (zip) byte[] üretir. */
    private static byte[] buildXlsx(List<String> isins) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        for (String isin : isins) {
            sb.append("<si><t>").append(isin).append("</t></si>");
        }
        sb.append("</sst>");
        byte[] xml = sb.toString().getBytes(StandardCharsets.UTF_8);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(baos)) {
                zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
                zip.write(xml);
                zip.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stubDownload(byte[] body) {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));
    }

    /** Geçerli HMB xlsx'i gibi görünen 25 adet US-prefixli ISIN üretir. */
    private static List<String> genusValidIsins(int n) {
        List<String> out = new java.util.ArrayList<>();
        // ISIN regex: ^[A-Z]{2}[A-Z0-9]{9}[0-9]$ — 12 char total
        for (int i = 0; i < n; i++) {
            // US900123XX0X formatında — son karakter rakam.
            String body = String.format("%09d", i);
            out.add("US" + body.substring(0, 9) + "0");
        }
        return out;
    }

    // ---- URL/extension validation ------------------------------------------

    @Test
    void refresh_nullUrl_throws() {
        assertThatThrownBy(() -> provider.refreshFromXlsx(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refresh_nonXlsxUrl_throws() {
        assertThatThrownBy(() -> provider.refreshFromXlsx("https://hmb.gov.tr/list.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refresh_wrongHmbFilename_withForce_bypassesKeywordCheck() throws Exception {
        // HMB filename şeması değişirse admin force=true ile override edebilmeli;
        // ama içerik doğrulamaları (min ISIN, TR prefix, overlap) hâlâ uygulanır.
        setActiveIsins(List.of());
        List<String> validIsins = genusValidIsins(25);
        String nonStandardUrl =
                "https://ms.hmb.gov.tr/uploads/2027/01/HMB_New_Naming_Scheme-abc.xlsx";
        when(restTemplate.exchange(eq(nonStandardUrl), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(buildXlsx(validIsins), HttpStatus.OK));

        // force=false → keyword check fail
        assertThatThrownBy(() -> provider.refreshFromXlsx(nonStandardUrl, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dış Borç Stoku Tahvil Listesi");

        // force=true → keyword check bypass, içerik geçerli → kabul
        int count = provider.refreshFromXlsx(nonStandardUrl, true);
        assertThat(count).isEqualTo(25);
        assertThat(provider.lastXlsxUrl()).isEqualTo(nonStandardUrl);
    }

    @Test
    void refresh_wrongHmbFilename_yurtDisiTahvilIhraclari_rejects() {
        // HMB'nin BAŞKA bir xlsx'i — "Yurt Dışı Tahvil İhraçları" (daha geniş kapsam).
        // İçerikten önce URL filename'de "Dis_Borc_Stoku" keyword'u arıyoruz.
        String wrongUrl = "https://ms.hmb.gov.tr/uploads/2026/05/"
                + "Merkezi_Yonetim_Yurt_Disi_Tahvil_Ihraclari-999a08731124a3d4.xlsx";

        assertThatThrownBy(() -> provider.refreshFromXlsx(wrongUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dış Borç Stoku Tahvil Listesi");
    }

    // ---- content validation ------------------------------------------------

    @Test
    void refresh_tooFewIsins_rejects() throws Exception {
        // 5 ISIN — minimum 20 altında.
        setActiveIsins(List.of());
        stubDownload(buildXlsx(genusValidIsins(5)));

        assertThatThrownBy(() -> provider.refreshFromXlsx(URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yalnızca 5 ISIN");
    }

    @Test
    void refresh_wrongIssuerPrefixes_rejects() throws Exception {
        // 25 adet DE/FR-prefixli ISIN — TR ihraççısı değil.
        setActiveIsins(List.of());
        List<String> nonTr = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            String body = String.format("%09d", i);
            nonTr.add((i % 2 == 0 ? "DE" : "FR") + body.substring(0, 9) + "0");
        }
        stubDownload(buildXlsx(nonTr));

        assertThatThrownBy(() -> provider.refreshFromXlsx(URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TR ihraççı formatında");
    }

    @Test
    void refresh_zeroOverlapWithExisting_rejects() throws Exception {
        // Mevcut: 25 ISIN (US-prefix). Yeni: 25 farklı ISIN (yine US-prefix, format geçer ama overlap yok).
        List<String> existing = genusValidIsins(25);
        setActiveIsins(existing);

        List<String> brandNew = new java.util.ArrayList<>();
        for (int i = 100; i < 125; i++) {
            String body = String.format("%09d", i);
            brandNew.add("US" + body.substring(0, 9) + "0");
        }
        stubDownload(buildXlsx(brandNew));

        assertThatThrownBy(() -> provider.refreshFromXlsx(URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("örtüşüyor");
    }

    // ---- cumulative union --------------------------------------------------

    @Test
    void refresh_validXlsx_preservesPreviousIsinsAndAddsNew() throws Exception {
        // Mevcut: [US...0000, US...0001, ..., US...0019] (20 ISIN).
        // Yeni xlsx: 5 mevcut + 5 yeni — toplam 10. Min 20 olmalı; geniş tutalım.
        List<String> previous = genusValidIsins(20);
        setActiveIsins(previous);

        List<String> overlapping = new java.util.ArrayList<>();
        overlapping.addAll(previous.subList(0, 15));      // 15 mevcut
        for (int i = 50; i < 60; i++) {                   // 10 yeni
            String body = String.format("%09d", i);
            overlapping.add("US" + body.substring(0, 9) + "0");
        }
        // toplam 25; overlap = 15/20 = 75% > 30% — geçer.
        stubDownload(buildXlsx(overlapping));

        int total = provider.refreshFromXlsx(URL);

        // 20 mevcut + 10 yeni = 30 cumulative.
        assertThat(total).isEqualTo(30);
        assertThat(provider.isins()).hasSize(30);
        // Mevcut ISIN'lerin TAMAMI hâlâ aktif (HİÇ DROP yok).
        assertThat(provider.isins()).containsAll(previous);
        // lastXlsxUrl güncellendi.
        assertThat(provider.lastXlsxUrl()).isEqualTo(URL);
    }

    @Test
    void refresh_firstTimeFromEmpty_acceptsAndPopulates() throws Exception {
        // Boş başlangıçtan ilk yükleme: overlap kuralı uygulanmaz (previous boş).
        setActiveIsins(List.of());
        List<String> fresh = genusValidIsins(25);
        stubDownload(buildXlsx(fresh));

        int count = provider.refreshFromXlsx(URL);

        assertThat(count).isEqualTo(25);
        assertThat(provider.isins()).containsExactlyElementsOf(fresh);
    }

    // ---- download failure --------------------------------------------------

    @Test
    void refresh_downloadFails_throws() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(new byte[0], HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> provider.refreshFromXlsx(URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("xlsx indirilemedi");
    }

    @Test
    void refresh_emptyXlsx_throws() throws Exception {
        // Geçerli zip ama ISIN yok.
        setActiveIsins(List.of());
        stubDownload(buildXlsx(List.of()));

        assertThatThrownBy(() -> provider.refreshFromXlsx(URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ISIN bulunamadı");
    }
}
