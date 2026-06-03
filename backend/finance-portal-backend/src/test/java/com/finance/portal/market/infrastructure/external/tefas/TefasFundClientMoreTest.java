package com.finance.portal.market.infrastructure.external.tefas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.market.application.funds.model.FundPriceHistoryPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TefasFundClient} — {@link TefasFundClientTest}'in kapsamadığı dalları kapatan ek testler.
 *
 * <p><b>Seam çözümü:</b> Client HTTP'yi enjekte edilebilir RestTemplate ile değil, harici {@code curl}
 * alt-süreciyle ({@code ProcessBuilder}) yapar. Bu testler curl'ü gerçekten çalıştırır ama ağa ÇIKMADAN:
 * {@code props.historyUrl} bir {@code file:///...} URL'sine ayarlanır. curl, {@code file://} şemasında
 * {@code -X POST}/{@code --data-binary @-}'i yok sayıp dosya içeriğini exit 0 ile döndürür; var olmayan
 * dosyada exit 37 (sıfırdan farklı) döner. Böylece {@code curlPost}/{@code fetchChunk}'ın yanıt-ayrıştırma
 * ve hata kolları DETERMİNİSTİK ve ağsız test edilir. Geçerli (boş olmayan resultList) yanıtta backoff
 * {@code Thread.sleep} HİÇ tetiklenmez (1. denemede kesin sonuç döner). Yalnızca bilinçli olarak
 * resultList'siz/boş yanıt veren 1-2 testte kısa backoff (≈1.5s) beklenir.
 *
 * <p>Private static yardımcılar ({@code buildDateWindows}, {@code text}, {@code decimal}) ve private
 * örnek metotlar ({@code collectUniqueByDay}, {@code fetchChunk}, {@code curlPost}, {@code buildBody})
 * reflection ile çağrılır; imzalar kaynaktan birebir doğrulanmıştır.
 */
class TefasFundClientMoreTest {

    private ObjectMapper mapper;
    private TefasProperties props;
    private TefasFundClient client;

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        props = new TefasProperties();
        // Backoff sürelerini ve timeout'u kısa tut; geçerli yanıt yolunda zaten sleep olmaz.
        props.setTimeoutSeconds(20);
        client = new TefasFundClient(mapper, props);
    }

    @AfterEach
    void tearDown() {
        // Daemon executor'u kapatıp testler arası thread sızıntısını engelle.
        try {
            ExecutorService ex = (ExecutorService) field("executor").get(client);
            ex.shutdownNow();
        } catch (Exception ignored) {
            // best-effort
        }
    }

    // ───────────────────────── helpers ─────────────────────────

    /** Bir {@code file:///...} URL'si üretir (curl Windows'ta {@code file:///C:/...} formatını kabul eder). */
    private String fileUrl(String content) throws Exception {
        Path f = tmp.resolve("tefas-resp-" + System.nanoTime() + ".json");
        Files.write(f, content.getBytes(StandardCharsets.UTF_8));
        return f.toUri().toString(); // ör: file:///C:/.../tefas-resp-123.json
    }

    private static java.lang.reflect.Field field(String name) throws Exception {
        java.lang.reflect.Field fld = TefasFundClient.class.getDeclaredField(name);
        fld.setAccessible(true);
        return fld;
    }

    private static Method method(String name, Class<?>... params) throws Exception {
        Method m = TefasFundClient.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<FundPriceHistoryPoint> invokeFetchChunk(String code, String type, LocalDate from, LocalDate to)
            throws Exception {
        Method m = method("fetchChunk", String.class, String.class, LocalDate.class, LocalDate.class);
        return (List<FundPriceHistoryPoint>) m.invoke(client, code, type, from, to);
    }

    @SuppressWarnings("unchecked")
    private NavigableMap<String, FundPriceHistoryPoint> invokeCollect(
            List<CompletableFuture<List<FundPriceHistoryPoint>>> futures, String code, String type) throws Exception {
        Method m = method("collectUniqueByDay", List.class, String.class, String.class);
        return (NavigableMap<String, FundPriceHistoryPoint>) m.invoke(client, futures, code, type);
    }

    private static String invokeText(JsonNode node, String fld) throws Exception {
        return (String) method("text", JsonNode.class, String.class).invoke(null, node, fld);
    }

    private static BigDecimal invokeDecimal(JsonNode node, String fld) throws Exception {
        return (BigDecimal) method("decimal", JsonNode.class, String.class).invoke(null, node, fld);
    }

    // ───────────────────────── fetchPriceHistory: full happy path via file:// ─────────────────────────

    /**
     * Zengin resultList içeren bir {@code file://} yanıtıyla TÜM mutlu yol koşar:
     * guard geçer, çok-pencereli {@code buildDateWindows}, executor + {@code CompletableFuture}
     * (daemon ThreadFactory lambda'sı), {@code fetchChunk} satır döngüsü (geçerli + tüm "atla"
     * kolları: tarih null / fiyat null / signum&lt;=0 / NaN / parse-hata / kısa-tarih), {@code curlPost}
     * başarı yolu (exit 0, token!=null), {@code collectUniqueByDay} ve gün-tekilleştirme.
     */
    @Test
    @DisplayName("fetchPriceHistory: file:// zengin yanıt → uçtan uca mutlu yol + satır filtre kolları")
    void fetchPriceHistory_happyPath_richResponse() throws Exception {
        String json = "{\"resultList\":["
                // geçerli, tarih uzunluğu 19 (>=10) → substring(0,10)
                + "{\"tarih\":\"2025-01-02T00:00:00\",\"fiyat\":\"1.234567\",\"portfoyBuyukluk\":\"1000000\"},"
                // geçerli, tarih uzunluğu 10 → substring(0,10); portfoyBuyukluk yok → decimal null
                + "{\"tarih\":\"2025-01-03\",\"fiyat\":\"2.5\"},"
                // geçerli, tarih uzunluğu 8 (<10) → day = date (kısa dal)
                + "{\"tarih\":\"20250104\",\"fiyat\":\"3.0\",\"portfoyBuyukluk\":\"500000\"},"
                // tarih alanı yok → text null → atla
                + "{\"fiyat\":\"4.0\"},"
                // fiyat signum<=0 → atla
                + "{\"tarih\":\"2025-01-05\",\"fiyat\":\"0\"},"
                // fiyat NaN → decimal null → atla
                + "{\"tarih\":\"2025-01-06\",\"fiyat\":\"NaN\"},"
                // fiyat ayrıştırılamaz → decimal null → atla
                + "{\"tarih\":\"2025-01-07\",\"fiyat\":\"abc\"},"
                // tarih null node → text null → atla
                + "{\"tarih\":null,\"fiyat\":\"5.0\"}"
                + "]}";
        props.setHistoryUrl(fileUrl(json));
        props.setChunkDays(28);

        // 45 günlük aralık → 2 pencere (çok-pencere döngüsü + son pencere overshoot → end=to).
        List<FundPriceHistoryPoint> result =
                client.fetchPriceHistory("afa", "YAT", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 14));

        assertThat(result).isNotNull();
        // 3 geçerli gün (Jan2/Jan3/Jan4); her pencere aynı dosyayı döndürür → tekilleştirilir.
        assertThat(result).hasSize(3);
        assertThat(result).extracting(FundPriceHistoryPoint::getDate)
                .containsExactly("2025-01-02", "2025-01-03", "20250104"); // TreeMap → artan sıra
        assertThat(result.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("1.234567"));
        assertThat(result.get(0).getPortfolioSize()).isEqualByComparingTo(new BigDecimal("1000000"));
        // portfoyBuyukluk yok → null
        assertThat(result.get(1).getPortfolioSize()).isNull();
    }

    /** {@code fonTipi} null → "YAT" defaultu (guard sonrası dal); yine file:// ile ağsız. */
    @Test
    @DisplayName("fetchPriceHistory: fonTipi null → YAT defaultu (mutlu yol, file://)")
    void fetchPriceHistory_fonTipiNull_defaultsYat() throws Exception {
        props.setHistoryUrl(fileUrl("{\"resultList\":[{\"tarih\":\"2025-03-03\",\"fiyat\":\"7.77\"}]}"));
        List<FundPriceHistoryPoint> result =
                client.fetchPriceHistory("XYZ", null, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 5));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDate()).isEqualTo("2025-03-03");
        assertThat(result.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("7.77"));
    }

    /** {@code fonTipi} boşluktan ibaret (isBlank) → "YAT"; ayrı bir guard-sonrası dal. */
    @Test
    @DisplayName("fetchPriceHistory: fonTipi blank → YAT defaultu")
    void fetchPriceHistory_fonTipiBlank_defaultsYat() throws Exception {
        props.setHistoryUrl(fileUrl("{\"resultList\":[]}"));
        List<FundPriceHistoryPoint> result =
                client.fetchPriceHistory("XYZ", "   ", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 2));
        assertThat(result).isNotNull().isEmpty(); // resultList boş dizi → veri yok
    }

    /** {@code fonTipi} dolu (non-blank) → trim().toUpperCase dalı ("emk" → "EMK"). */
    @Test
    @DisplayName("fetchPriceHistory: fonTipi dolu → trim+upper dalı")
    void fetchPriceHistory_fonTipiProvided_trimUpper() throws Exception {
        props.setHistoryUrl(fileUrl("{\"resultList\":[{\"tarih\":\"2025-04-04\",\"fiyat\":\"9.9\"}]}"));
        List<FundPriceHistoryPoint> result =
                client.fetchPriceHistory("XYZ", " emk ", LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 6));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDate()).isEqualTo("2025-04-04");
    }

    // ───────────────────────── fetchChunk: hata/throttle kolları (reflection) ─────────────────────────

    /**
     * Boş dosya → curl exit 0 ama gövde "" → {@code response != null && !response.isBlank()} FALSE kolu
     * (isBlank true) → resultList görülmez → 3 deneme backoff → boş liste.
     */
    @Test
    @DisplayName("fetchChunk: boş yanıt (blank) → isBlank dalı → retry → boş liste")
    void fetchChunk_blankResponse_returnsEmpty() throws Exception {
        props.setHistoryUrl(fileUrl("")); // boş gövde
        List<FundPriceHistoryPoint> out =
                invokeFetchChunk("AFA", "YAT", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 5));
        assertThat(out).isNotNull().isEmpty();
    }

    /**
     * resultList var ama dizi DEĞİL (string) → {@code response} non-blank (TRUE kolu) ama
     * {@code list.isArray()} FALSE → return etmez → 3 deneme backoff → boş liste.
     */
    @Test
    @DisplayName("fetchChunk: resultList dizi değil → isArray false dalı → retry → boş liste")
    void fetchChunk_resultListNotArray_returnsEmpty() throws Exception {
        props.setHistoryUrl(fileUrl("{\"resultList\":\"not-an-array\"}"));
        List<FundPriceHistoryPoint> out =
                invokeFetchChunk("AFA", "YAT", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 5));
        assertThat(out).isNotNull().isEmpty();
    }

    /**
     * Var olmayan {@code file://} → curl exit != 0 → {@code curlPost} null → {@code response == null}
     * (ilk operand FALSE, kısa-devre) → 3 deneme backoff → boş liste.
     */
    @Test
    @DisplayName("fetchChunk: curl exit!=0 (null response) → response==null kısa-devre → boş liste")
    void fetchChunk_curlFailure_nullResponse_returnsEmpty() throws Exception {
        String bad = tmp.resolve("nope-" + System.nanoTime() + ".json").toUri().toString();
        props.setHistoryUrl(bad); // dosya yok → exit 37
        List<FundPriceHistoryPoint> out =
                invokeFetchChunk("AFA", "YAT", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 5));
        assertThat(out).isNotNull().isEmpty();
    }

    // ───────────────────────── curlPost: doğrudan kollar (reflection, retry'siz) ─────────────────────────

    /**
     * {@code curlPost} başarı yolu: geçerli file:// + token!=null (default bearerToken dolu) → exit 0,
     * dosya içeriği döner. ({@code !done} FALSE, exit!=0 FALSE, token!=null TRUE kolları.)
     */
    @Test
    @DisplayName("curlPost: geçerli file:// + bearer token → içerik döner (exit 0)")
    void curlPost_success_withBearer_returnsBody() throws Exception {
        String url = fileUrl("{\"ok\":true}");
        Method m = method("curlPost", String.class, String.class);
        Object res = m.invoke(client, url, "{\"fonKodu\":\"AFA\"}");
        assertThat(res).isInstanceOf(String.class);
        assertThat((String) res).contains("\"ok\":true");
    }

    /**
     * {@code curlPost} token==null kolu: bearerToken null → Authorization header eklenmez
     * ({@code token != null && !token.isBlank()} ilk operand FALSE). Yine geçerli file:// → içerik döner.
     */
    @Test
    @DisplayName("curlPost: bearer token null → Authorization eklenmez (token==null dalı)")
    void curlPost_nullToken_omitsAuthHeader() throws Exception {
        props.setBearerToken(null);
        String url = fileUrl("{\"ok\":1}");
        Method m = method("curlPost", String.class, String.class);
        Object res = m.invoke(client, url, "{}");
        assertThat((String) res).contains("\"ok\":1");
    }

    /**
     * {@code curlPost} token boşluk (blank) kolu: bearerToken "  " → {@code !token.isBlank()} FALSE →
     * Authorization eklenmez. Geçerli file:// → içerik döner.
     */
    @Test
    @DisplayName("curlPost: bearer token blank → Authorization eklenmez (!isBlank dalı)")
    void curlPost_blankToken_omitsAuthHeader() throws Exception {
        props.setBearerToken("   ");
        String url = fileUrl("{\"ok\":2}");
        Method m = method("curlPost", String.class, String.class);
        Object res = m.invoke(client, url, "{}");
        assertThat((String) res).contains("\"ok\":2");
    }

    /**
     * {@code curlPost} hata yolu: var olmayan file:// → curl exit != 0 → {@code exitValue() != 0} TRUE
     * dalı → null döner. (retry'siz; doğrudan curlPost.)
     */
    @Test
    @DisplayName("curlPost: curl exit!=0 → null döner (exitValue!=0 dalı)")
    void curlPost_nonZeroExit_returnsNull() throws Exception {
        String bad = tmp.resolve("missing-" + System.nanoTime() + ".json").toUri().toString();
        Method m = method("curlPost", String.class, String.class);
        Object res = m.invoke(client, bad, "{}");
        assertThat(res).isNull();
    }

    // ───────────────────────── collectUniqueByDay (reflection) ─────────────────────────

    /**
     * Tamamlanmış future: geçerli nokta + tarih-null nokta + fiyat-null nokta →
     * {@code p.getDate()!=null && p.getPrice()!=null} TÜM kolları (TT eklenir, T-null/F atlanır).
     * Aynı gün iki kez → {@code putIfAbsent} tekilleştirir.
     */
    @Test
    @DisplayName("collectUniqueByDay: date/price null kolları + gün tekilleştirme")
    void collectUniqueByDay_filtersAndDedupes() throws Exception {
        List<FundPriceHistoryPoint> list = new ArrayList<>();
        list.add(new FundPriceHistoryPoint("2025-01-10", new BigDecimal("1.5"), null)); // geçerli
        list.add(new FundPriceHistoryPoint("2025-01-10", new BigDecimal("9.9"), null)); // aynı gün → atlanır
        list.add(new FundPriceHistoryPoint(null, new BigDecimal("2.0"), null));         // date null → atla
        list.add(new FundPriceHistoryPoint("2025-01-11", null, null));                  // price null → atla
        list.add(new FundPriceHistoryPoint("2025-01-09", new BigDecimal("3.0"), null)); // geçerli (erken gün)

        List<CompletableFuture<List<FundPriceHistoryPoint>>> futures = new ArrayList<>();
        futures.add(CompletableFuture.completedFuture(list));

        NavigableMap<String, FundPriceHistoryPoint> byDay = invokeCollect(futures, "AFA", "YAT");

        assertThat(byDay).containsOnlyKeys("2025-01-09", "2025-01-10"); // 01-11 fiyat-null elendi
        // ilk gelen 01-10 (1.5) korunur (putIfAbsent)
        assertThat(byDay.get("2025-01-10").getPrice()).isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(byDay.firstKey()).isEqualTo("2025-01-09"); // TreeMap sıralı
    }

    /**
     * Future istisnayla biter → {@code f.get} ExecutionException atar → generic {@code catch (Exception)}
     * dalı; diğer (sağlam) future yine işlenir.
     */
    @Test
    @DisplayName("collectUniqueByDay: future exception → catch(Exception) dalı, diğerleri işlenir")
    void collectUniqueByDay_futureException_isCaught() throws Exception {
        CompletableFuture<List<FundPriceHistoryPoint>> failing = new CompletableFuture<>();
        failing.completeExceptionally(new RuntimeException("boom"));
        CompletableFuture<List<FundPriceHistoryPoint>> ok =
                CompletableFuture.completedFuture(List.of(new FundPriceHistoryPoint("2025-02-02", new BigDecimal("4.4"), null)));

        List<CompletableFuture<List<FundPriceHistoryPoint>>> futures = new ArrayList<>();
        futures.add(failing);
        futures.add(ok);

        NavigableMap<String, FundPriceHistoryPoint> byDay = invokeCollect(futures, "AFA", "YAT");

        assertThat(byDay).containsOnlyKeys("2025-02-02");
    }

    /**
     * Çağıran thread interrupt edilirse {@code f.get(timeout)} InterruptedException atar →
     * {@code catch (InterruptedException)} dalı + {@code break}. Tamamlanmamış future kullanılır.
     */
    @Test
    @DisplayName("collectUniqueByDay: interrupt → InterruptedException dalı + break")
    void collectUniqueByDay_interrupted_breaksOut() throws Exception {
        CompletableFuture<List<FundPriceHistoryPoint>> never = new CompletableFuture<>(); // asla tamamlanmaz
        List<CompletableFuture<List<FundPriceHistoryPoint>>> futures = new ArrayList<>();
        futures.add(never);

        Thread.currentThread().interrupt(); // bir sonraki blocking get() hemen InterruptedException atar
        NavigableMap<String, FundPriceHistoryPoint> byDay;
        try {
            byDay = invokeCollect(futures, "AFA", "YAT");
        } finally {
            // interrupt bayrağını test sonrası temizle (collectUniqueByDay yeniden set etmiş olabilir).
            Thread.interrupted();
        }
        assertThat(byDay).isEmpty();
    }

    // ───────────────────────── buildDateWindows (static, reflection) ─────────────────────────

    @SuppressWarnings("unchecked")
    private List<LocalDate[]> invokeWindows(LocalDate from, LocalDate to, int chunkDays) throws Exception {
        Method m = method("buildDateWindows", LocalDate.class, LocalDate.class, int.class);
        return (List<LocalDate[]>) m.invoke(null, from, to, chunkDays);
    }

    /** Tek günlük aralık: while bir tur, {@code end.isAfter(to)} TRUE → end=to. */
    @Test
    @DisplayName("buildDateWindows: tek gün → tek pencere, overshoot kırpılır")
    void buildDateWindows_singleDay() throws Exception {
        LocalDate d = LocalDate.of(2025, 5, 5);
        List<LocalDate[]> w = invokeWindows(d, d, 28);
        assertThat(w).hasSize(1);
        assertThat(w.get(0)[0]).isEqualTo(d);
        assertThat(w.get(0)[1]).isEqualTo(d);
    }

    /**
     * Tam hizalı aralık (uzunluk = chunkDays katı): son pencerede {@code end.isAfter(to)} FALSE dalı,
     * ortadaki pencerelerde TRUE/sınır. 56 gün, chunkDays=28 → 2 pencere, ikisi de tam.
     */
    @Test
    @DisplayName("buildDateWindows: tam hizalı çok-pencere → end.isAfter FALSE dalı")
    void buildDateWindows_exactMultiWindow() throws Exception {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = from.plusDays(55); // 56 gün kapalı aralık
        List<LocalDate[]> w = invokeWindows(from, to, 28);
        assertThat(w).hasSize(2);
        assertThat(w.get(0)[0]).isEqualTo(from);
        assertThat(w.get(0)[1]).isEqualTo(from.plusDays(27));
        assertThat(w.get(1)[0]).isEqualTo(from.plusDays(28));
        assertThat(w.get(1)[1]).isEqualTo(to); // tam, kırpma yok
    }

    /**
     * Hizasız aralık: son pencere taşar → {@code end.isAfter(to)} TRUE → end=to (kırpma).
     * 40 gün, chunkDays=28 → 2 pencere; ikincisi kırpılır.
     */
    @Test
    @DisplayName("buildDateWindows: hizasız → son pencere kırpılır (end.isAfter TRUE dalı)")
    void buildDateWindows_clampedLastWindow() throws Exception {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = from.plusDays(39); // 40 gün
        List<LocalDate[]> w = invokeWindows(from, to, 28);
        assertThat(w).hasSize(2);
        assertThat(w.get(1)[0]).isEqualTo(from.plusDays(28));
        assertThat(w.get(1)[1]).isEqualTo(to); // taştı, to'ya kırpıldı
    }

    // ───────────────────────── text() (static, reflection) ─────────────────────────

    @Test
    @DisplayName("text: node null → null (ilk operand)")
    void text_nullNode_returnsNull() throws Exception {
        assertThat(invokeText(null, "tarih")).isNull();
    }

    @Test
    @DisplayName("text: alan yok → null")
    void text_missingField_returnsNull() throws Exception {
        JsonNode n = mapper.readTree("{\"baska\":\"x\"}");
        assertThat(invokeText(n, "tarih")).isNull();
    }

    @Test
    @DisplayName("text: alan JSON null → null")
    void text_nullValue_returnsNull() throws Exception {
        JsonNode n = mapper.readTree("{\"tarih\":null}");
        assertThat(invokeText(n, "tarih")).isNull();
    }

    @Test
    @DisplayName("text: alan boş string → null (isBlank dalı)")
    void text_blankValue_returnsNull() throws Exception {
        JsonNode n = mapper.readTree("{\"tarih\":\"   \"}");
        assertThat(invokeText(n, "tarih")).isNull();
    }

    @Test
    @DisplayName("text: geçerli değer → değer döner")
    void text_validValue_returnsValue() throws Exception {
        JsonNode n = mapper.readTree("{\"tarih\":\"2025-01-02\"}");
        assertThat(invokeText(n, "tarih")).isEqualTo("2025-01-02");
    }

    // ───────────────────────── decimal() (static, reflection) ─────────────────────────

    @Test
    @DisplayName("decimal: node null → null")
    void decimal_nullNode_returnsNull() throws Exception {
        assertThat(invokeDecimal(null, "fiyat")).isNull();
    }

    @Test
    @DisplayName("decimal: alan yok → null")
    void decimal_missingField_returnsNull() throws Exception {
        JsonNode n = mapper.readTree("{\"x\":1}");
        assertThat(invokeDecimal(n, "fiyat")).isNull();
    }

    @Test
    @DisplayName("decimal: alan JSON null → null")
    void decimal_nullValue_returnsNull() throws Exception {
        JsonNode n = mapper.readTree("{\"fiyat\":null}");
        assertThat(invokeDecimal(n, "fiyat")).isNull();
    }

    @Test
    @DisplayName("decimal: boş string → null (isBlank dalı)")
    void decimal_blankValue_returnsNull() throws Exception {
        JsonNode n = mapper.readTree("{\"fiyat\":\"  \"}");
        assertThat(invokeDecimal(n, "fiyat")).isNull();
    }

    @Test
    @DisplayName("decimal: NaN → null (NaN dalı)")
    void decimal_nan_returnsNull() throws Exception {
        JsonNode n = mapper.readTree("{\"fiyat\":\"NaN\"}");
        assertThat(invokeDecimal(n, "fiyat")).isNull();
    }

    @Test
    @DisplayName("decimal: ayrıştırılamaz → null (catch dalı)")
    void decimal_unparseable_returnsNull() throws Exception {
        JsonNode n = mapper.readTree("{\"fiyat\":\"12,3x\"}");
        assertThat(invokeDecimal(n, "fiyat")).isNull();
    }

    @Test
    @DisplayName("decimal: geçerli sayı → BigDecimal")
    void decimal_valid_returnsBigDecimal() throws Exception {
        JsonNode n = mapper.readTree("{\"fiyat\":\"1.2345\"}");
        assertThat(invokeDecimal(n, "fiyat")).isEqualByComparingTo(new BigDecimal("1.2345"));
    }

    // ───────────────────────── buildBody + daemonFactory ─────────────────────────

    @Test
    @DisplayName("buildBody: alanlar yyyyMMdd biçimli ve tam doldurulur")
    @SuppressWarnings("unchecked")
    void buildBody_populatesFields() throws Exception {
        Method m = method("buildBody", String.class, String.class, LocalDate.class, LocalDate.class);
        java.util.Map<String, Object> body = (java.util.Map<String, Object>)
                m.invoke(client, "AFA", "YAT", LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 30));
        assertThat(body.get("fonTipi")).isEqualTo("YAT");
        assertThat(body.get("fonKodu")).isEqualTo("AFA");
        assertThat(body.get("basTarih")).isEqualTo("20250102");
        assertThat(body.get("bitTarih")).isEqualTo("20250130");
        assertThat(body.get("basSira")).isEqualTo(1);
        assertThat(body.get("bitSira")).isEqualTo(100000);
        assertThat(body.get("dil")).isEqualTo("TR");
    }

    /**
     * Daemon {@code ThreadFactory} lambda'sı (yeni Thread, setDaemon, return) yalnızca executor gerçek
     * thread ürettiğinde koşar; executor'a doğrudan bir görev verip thread oluşumunu zorlar.
     */
    @Test
    @DisplayName("daemonFactory: executor görevi → thread fabrikası lambda'sı çalışır (daemon)")
    void daemonFactory_threadCreated() throws Exception {
        ExecutorService ex = (ExecutorService) field("executor").get(client);
        CompletableFuture<String> probe = new CompletableFuture<>();
        ex.submit(() -> probe.complete(Thread.currentThread().getName()));
        String name = probe.get(5, TimeUnit.SECONDS);
        assertThat(name).startsWith("tefas-history-");
    }
}
