package com.finance.portal.market.application.stock;

import com.finance.portal.market.application.stock.port.MidasStockPort;
import jakarta.annotation.PostConstruct;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BIST hisse sembol listesi.
 *
 * <p>Uygulama başlangıcında ve her gün 02:00'de Midas'tan dinamik olarak çekilir.
 * Midas (getmidas.com) bir HTML scrape kaynağıdır ve hızlı ardışık isteklerde
 * anti-bot/rate-limit ile linksiz sayfa döndürebilir — bu durumda parse 0 sembol
 * verir. Bu yüzden:
 * <ul>
 *   <li>İstekler arası gecikme + boş dönerse tekrar deneme uygulanır
 *       ({@link #fetchWithRetry});</li>
 *   <li>BIST30/50/100 endeks bileşenleri için <b>gömülü güncel yedek liste</b> vardır
 *       (varsayılan değer) — scrape başarısız olsa bile liste asla boş kalmaz.
 *       Scrape başarılı olduğunda yedeğin üzerine güncel veriyi yazar.</li>
 * </ul>
 * Bileşenler resmî olarak 3 ayda bir gözden geçirildiği için yedek listenin
 * tazeliği yeterlidir (kaynak: getmidas.com, güncellenme 2026-05-22).
 */
@Component
public class StockSymbolProvider {

    private static final Logger log = LoggerFactory.getLogger(StockSymbolProvider.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1200L;

    /** Sembollere ".IS" eki ekleyerek (Midas parse formatıyla uyumlu) liste üretir. */
    private static List<String> withIs(String... syms) {
        return Arrays.stream(syms).map(s -> s + ".IS").toList();
    }

    // ── Gömülü yedek bileşen listeleri (getmidas.com, 2026-05-22) ────────────────
    private static final List<String> FALLBACK_BIST30 = withIs(
            "AEFES", "AKBNK", "ASELS", "ASTOR", "BIMAS", "DSTKF", "EKGYO", "ENKAI", "EREGL", "FROTO",
            "GARAN", "GUBRF", "ISCTR", "KCHOL", "KRDMD", "MGROS", "PETKM", "PGSUS", "SAHOL", "SASA",
            "SISE", "TAVHL", "TCELL", "THYAO", "TOASO", "TRALT", "TTKOM", "TUPRS", "VAKBN", "YKBNK");

    private static final List<String> FALLBACK_BIST50 = withIs(
            "AEFES", "AKBNK", "ALARK", "ARCLK", "ASELS", "ASTOR", "BIMAS", "BRSAN", "BTCIM", "CANTE",
            "CCOLA", "CIMSA", "DOAS", "DSTKF", "EKGYO", "ENKAI", "EREGL", "FROTO", "GARAN", "GUBRF",
            "HALKB", "HEKTS", "ISCTR", "KCHOL", "KONTR", "KRDMD", "KUYAS", "MAVI", "MGROS", "MIATK",
            "OYAKC", "PASEU", "PETKM", "PGSUS", "SAHOL", "SASA", "SISE", "TAVHL", "TCELL", "THYAO",
            "TOASO", "TRALT", "TRMET", "TSKB", "TTKOM", "TUPRS", "TURSG", "ULKER", "VAKBN", "YKBNK");

    private static final List<String> FALLBACK_BIST100 = withIs(
            "AEFES", "AGHOL", "AKBNK", "AKSA", "AKSEN", "ALARK", "ALTNY", "ANSGR", "ARCLK", "ASELS",
            "ASTOR", "BALSU", "BIMAS", "BRSAN", "BRYAT", "BSOKE", "BTCIM", "CANTE", "CCOLA", "CIMSA",
            "CVKMD", "CWENE", "DAPGM", "DOAS", "DOHOL", "DSTKF", "ECILC", "EFOR", "EKGYO", "ENERY",
            "ENJSA", "ENKAI", "EREGL", "EUPWR", "EUREN", "FENER", "FROTO", "GARAN", "GENIL", "GESAN",
            "GLRMK", "GRSEL", "GRTHO", "GSRAY", "GUBRF", "HALKB", "HEKTS", "ISCTR", "ISMEN", "IZENR",
            "KCHOL", "KLRHO", "KONTR", "KRDMD", "KTLEV", "KUYAS", "MAGEN", "MAVI", "MGROS", "MIATK",
            "MPARK", "OBAMS", "ODAS", "OTKAR", "OYAKC", "PAHOL", "PASEU", "PATEK", "PETKM", "PGSUS",
            "PSGYO", "QUAGR", "RALYH", "REEDR", "SAHOL", "SARKY", "SASA", "SISE", "SKBNK", "SOKM",
            "TABGD", "TAVHL", "TCELL", "THYAO", "TKFEN", "TOASO", "TRALT", "TRENJ", "TRMET", "TSKB",
            "TTKOM", "TUKAS", "TUPRS", "TUREX", "TURSG", "ULKER", "VAKBN", "VESTL", "YKBNK", "ZOREN");

    private final MidasStockPort midasStockPort;

    // Thread-safe listeler. Endeks listeleri gömülü yedekle başlatılır → ilk anda bile boş değil.
    private final AtomicReference<List<String>> allSymbols = new AtomicReference<>(List.of());
    private final AtomicReference<List<String>> bist30     = new AtomicReference<>(FALLBACK_BIST30);
    private final AtomicReference<List<String>> bist50     = new AtomicReference<>(FALLBACK_BIST50);
    private final AtomicReference<List<String>> bist100    = new AtomicReference<>(FALLBACK_BIST100);

    public StockSymbolProvider(MidasStockPort midasStockPort) {
        this.midasStockPort = midasStockPort;
    }

    @PostConstruct
    public void init() {
        refreshSymbols();
    }

    /** Her gün 02:00'de güncelle (endeks bileşenleri 3 ayda bir değişir).
     *  NOT: @SchedulerLock yalnızca @Scheduled proxy yolunda devreye girer;
     *  @PostConstruct -> refreshSymbols() doğrudan çağrısında her pod kendi
     *  bellek listelerini doldurur — bu da istenen davranıştır. */
    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "stock-symbol-refresh", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void refreshSymbols() {
        log.info("Refreshing BIST symbol lists from Midas...");
        try {
            List<String> all  = fetchWithRetry("");
            List<String> b30  = fetchWithRetry("xu030-bist-30-hisseleri");
            List<String> b50  = fetchWithRetry("xu050-bist-50-hisseleri");
            List<String> b100 = fetchWithRetry("xu100-bist-100-hisseleri");

            if (!all.isEmpty()) { allSymbols.set(all); log.info("Loaded {} total BIST symbols", all.size()); }
            else log.warn("Tüm hisse listesi Midas'tan boş döndü — mevcut liste korunuyor ({})", allSymbols.get().size());

            setOrKeep(bist30, b30, "BIST30");
            setOrKeep(bist50, b50, "BIST50");
            setOrKeep(bist100, b100, "BIST100");
        } catch (Exception e) {
            log.error("Failed to refresh symbol lists: {}", e.getMessage());
        }
    }

    /**
     * Midas'tan çeker; boş dönerse gecikmeyle birkaç kez tekrar dener.
     * Hızlı ardışık isteklerde getmidas anti-bot ile linksiz sayfa döndürebildiği için
     * istekler arası nefes payı bırakılır.
     */
    private List<String> fetchWithRetry(String path) {
        String label = path.isBlank() ? "all" : path;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (attempt > 1 || !path.isBlank()) sleep(RETRY_DELAY_MS);
            List<String> r = midasStockPort.fetchSymbols(path);
            if (!r.isEmpty()) return r;
            log.warn("Midas '{}' boş döndü (deneme {}/{})", label, attempt, MAX_ATTEMPTS);
        }
        return List.of();
    }

    /** Scrape doluysa onu yaz; boşsa mevcut değeri (yedek veya önceki başarılı) koru. */
    private void setOrKeep(AtomicReference<List<String>> ref, List<String> fetched, String name) {
        if (!fetched.isEmpty()) {
            ref.set(fetched);
            log.info("Loaded {} {} symbols (Midas)", fetched.size(), name);
        } else {
            log.warn("{}: Midas boş döndü — yedek/mevcut liste korunuyor ({} sembol)", name, ref.get().size());
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int getTotalElements() {
        return allSymbols.get().size();
    }

    public List<String> getAllSymbols() {
        return allSymbols.get();
    }

    public List<String> getBist30Symbols() {
        return bist30.get();
    }

    public List<String> getBist50Symbols() {
        return bist50.get();
    }

    public List<String> getBist100Symbols() {
        return bist100.get();
    }

    public List<String> getPagedSymbols(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 250) throw new IllegalArgumentException("size must be between 1 and 250");
        List<String> all = allSymbols.get();
        int total = all.size();
        int start = page * size;
        if (start >= total) return Collections.emptyList();
        return all.subList(start, Math.min(start + size, total));
    }
}
