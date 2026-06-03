package com.finance.portal.market.infrastructure.external.tefas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.market.application.funds.model.FundPriceHistoryPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TefasFundClient} branch-coverage testleri.
 *
 * <p><b>Seam kısıtı:</b> Bu client HTTP'yi enjekte edilebilir bir RestTemplate ile değil,
 * {@code ProcessBuilder} üzerinden harici {@code curl} alt-süreciyle yapar (TEFAS, JVM'in JSSE
 * TLS parmak izini engellediği için — bkz. sınıf javadoc'u). curlPost / fetchChunk için enjekte
 * edilebilir bir kesim (RestTemplate / HTTP client) YOKTUR; geçerli girdilerle çağrı yapmak
 * gerçek bir alt-süreç doğurur ve gerçek {@code Thread.sleep} backoff'larını tetikler. Bu yüzden
 * burada YALNIZCA alt-sürece HİÇ dokunmadan, kuruluş gereği (by construction) deterministik olarak
 * test edilebilen kısım hedeflenir: {@code fetchPriceHistory}'nin baştaki doğrulama guard'ının
 * (early-return) her bir {@code ||} kolu. Bu kollar curl, executor ve OpenTelemetry Span'inden
 * ÖNCE çalışır; dolayısıyla alt-süreç/sleep tetiklemez.
 */
class TefasFundClientTest {

    private TefasFundClient client;

    @BeforeEach
    void setUp() {
        // Geçerli, gerçekçi defaultlu props; guard early-return testlerinde executor'a hiç ulaşılmaz.
        TefasProperties props = new TefasProperties();
        client = new TefasFundClient(new ObjectMapper(), props);
    }

    // ── fetchPriceHistory guard: her ||-kolu için early-return (List.of()) ─────────────

    @Test
    @DisplayName("guard: code == null → boş liste (curl/executor'a ulaşılmaz)")
    void guard_codeNull() {
        List<FundPriceHistoryPoint> result =
                client.fetchPriceHistory(null, "YAT", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("guard: code boşluktan ibaret (isBlank) → boş liste")
    void guard_codeBlank() {
        // code != null ama isBlank() true → ikinci ||-kolu karar verir.
        List<FundPriceHistoryPoint> result =
                client.fetchPriceHistory("   ", "YAT", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("guard: code \"\" (empty) → boş liste (isBlank true)")
    void guard_codeEmpty() {
        List<FundPriceHistoryPoint> result =
                client.fetchPriceHistory("", "EMK", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("guard: from == null → boş liste (code geçerli, üçüncü ||-kolu)")
    void guard_fromNull() {
        List<FundPriceHistoryPoint> result =
                client.fetchPriceHistory("AFA", "YAT", null, LocalDate.of(2025, 1, 10));

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("guard: to == null → boş liste (code & from geçerli, dördüncü ||-kolu)")
    void guard_toNull() {
        List<FundPriceHistoryPoint> result =
                client.fetchPriceHistory("AFA", "YAT", LocalDate.of(2025, 1, 1), null);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("guard: from.isAfter(to) → boş liste (tüm önceki kollar false, beşinci ||-kolu)")
    void guard_fromAfterTo() {
        List<FundPriceHistoryPoint> result =
                client.fetchPriceHistory("AFA", "YAT", LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 1));

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("guard: fonTipi null + diğer guard tetikli → yine boş (fonTipi guard'a girmeden döner)")
    void guard_fonTipiNullWithInvalidRange() {
        // from.isAfter(to) guard'ı tetikler; fonTipi null olsa da curl'e ulaşılmaz.
        List<FundPriceHistoryPoint> result =
                client.fetchPriceHistory("AFA", null, LocalDate.of(2025, 2, 1), LocalDate.of(2025, 1, 1));

        assertThat(result).isNotNull().isEmpty();
    }

    // ── İade edilen liste değiştirilemez (List.of()) — sözleşme doğrulaması ───────────

    @Test
    @DisplayName("guard early-return List.of() döner — immutable boş liste sözleşmesi")
    void guard_returnsImmutableEmptyList() {
        List<FundPriceHistoryPoint> result =
                client.fetchPriceHistory(null, null, null, null);

        assertThat(result).isEmpty();
        // List.of() değiştirilemez olmalı; ekleme denemesi UnsupportedOperationException atar.
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> result.add(new FundPriceHistoryPoint("2025-01-01", null, null)));
    }
}
