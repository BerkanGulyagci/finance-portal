package com.finance.portal.news.application.service;

import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.stock.StockPageResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.news.application.model.RelatedAsset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Haber metninde (başlık + açıklama + içerik) geçen BİLİNEN varlıkları tespit eder
 * (haber → ilgili varlığın grafiği geçişi için). Sözlük tabanlı, deterministik:
 * BIST hisse + popüler kripto + döviz isimleri/sembolleri taranır.
 *
 * <p>Çakışma önleme önemlidir: çok kısa/yaygın semboller ("AL", "SUN" gibi gerçek
 * kelimelerle çakışabilir) ve jenerik isimler elenir. Sembol eşleşmesi KELİME SINIRIYLA
 * (tam token) yapılır; isim eşleşmesi en az 4 karakterlik anlamlı ada göre yapılır.
 */
@Service
public class NewsAssetMatcher {

    private static final Logger log = LoggerFactory.getLogger(NewsAssetMatcher.class);
    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final int MAX_ASSETS = 6;          // bir haberde en fazla bağlanacak varlık
    private static final int STOCK_PAGE = 100;

    private static final Duration DICT_TTL = Duration.ofHours(24);

    private final StockQueryService stockQueryService;
    private final CryptoMarketService cryptoMarketService;

    // Sözlük in-memory cache'lenir (Redis'e konmaz — private record serileştirme derdi yok,
    // ayrıca yalnız okuma-ağırlıklı küçük yapı). 24 saatte bir tembel yenilenir.
    private final AtomicReference<List<DictEntry>> cachedDict = new AtomicReference<>();
    private volatile Instant cachedAt = Instant.EPOCH;

    public NewsAssetMatcher(StockQueryService stockQueryService,
                            CryptoMarketService cryptoMarketService) {
        this.stockQueryService = stockQueryService;
        this.cryptoMarketService = cryptoMarketService;
    }

    /** Tek tespit kuralı: aranacak (küçük harf) anahtar + bağlanacak varlık. */
    private record DictEntry(String key, RelatedAsset asset, boolean wholeWord) {}

    // ── Döviz sözlüğü (sabit; TR + EN isimler) ───────────────────────────────
    // symbol = FxDetailPage'in beklediği kod (USD/EUR/…). DİKKAT: yaygın Türkçe
    // kelimelerle çakışan tek-kelime anahtarlar (ör. "yeni"→JPY, "avro"/"euro" tek
    // başına) KASITLI dışarıda bırakıldı — yanlış pozitif üretiyorlardı. Yalnız belirgin,
    // jenerik-olmayan para birimi adları/kodları kullanılır.
    private static final List<DictEntry> FX_DICT = List.of(
            fx("dolar", "USD", "Dolar"), fx("usd", "USD", "Dolar"),
            // "euro"/"avro" tek başına "avro bölgesi" gibi jenerik bağlamda geçiyor;
            // yalnız "euro/dolar", "euro bölgesi" değil net para bağlamı için kod+net ad:
            fx("euro/dolar", "EUR", "Euro"), fx("eur/usd", "EUR", "Euro"),
            fx("sterlin", "GBP", "Sterlin"),
            fx("isviçre frangı", "CHF", "İsviçre Frangı"),
            fx("japon yeni", "JPY", "Japon Yeni"),     // "yeni" tek başına ÇIKARILDI (çok yaygın kelime)
            fx("rus rublesi", "RUB", "Rus Rublesi"),
            fx("çin yuanı", "CNY", "Çin Yuanı"),
            fx("kanada doları", "CAD", "Kanada Doları"),
            fx("avustralya doları", "AUD", "Avustralya Doları"),
            fx("suudi riyali", "SAR", "Suudi Riyali")
    );

    // Hisse/kripto sembolü veya ADI olarak geçen ama günlük Türkçe'de ÇOK YAYGIN kelime
    // olan tokenlar — haber metninde para/varlık bağlamı olmadan geçer, yanlış pozitif
    // üretirler. SADECE gerçekten jenerik kelimeler (hisse SASA/DEVA gibi geçerli ama nadir
    // sembolleri DAHİL EDİLMEZ — onlar büyük harf kod olarak metinde nadiren yanlış eşleşir).
    private static final java.util.Set<String> COMMON_WORD_BLACKLIST = java.util.Set.of(
            "hedef", "şok", "bizim", "vakıf", "halk", "iş", "net", "good", "ideal",
            "info", "kent", "link", "mert", "oba", "us", "veri", "yapı",
            "yeni", "altın", "gold", "global", "say", "ar", "gen", "ege", "deva"
    );

    private static DictEntry fx(String key, String code, String name) {
        return new DictEntry(key.toLowerCase(TR), new RelatedAsset(code, name, "FX"), true);
    }

    /**
     * Varlık sözlüğü (hisse + kripto + döviz) — 24 saat in-memory cache'li. Her haber
     * detayında yeniden kurmak pahalı (tüm hisse listesi + kripto). Liste servisleri
     * zaten kendi cache'lerinden okur, bu yalnız sözlüğü hazırlar.
     */
    private List<DictEntry> getDictionary() {
        List<DictEntry> snapshot = cachedDict.get();
        if (snapshot != null && Duration.between(cachedAt, Instant.now()).compareTo(DICT_TTL) < 0) {
            return snapshot;
        }
        synchronized (this) {
            List<DictEntry> current = cachedDict.get();
            if (current != null && Duration.between(cachedAt, Instant.now()).compareTo(DICT_TTL) < 0) {
                return current;
            }
            List<DictEntry> built = buildDictionary();
            // Boş sözlük (kaynaklar geçici çöktü) cache'lenmesin — bir sonraki çağrı tekrar dener.
            if (!built.isEmpty()) {
                cachedDict.set(built);
                cachedAt = Instant.now();
            }
            return built;
        }
    }

    private List<DictEntry> buildDictionary() {
        // ÖNCELİK: spesifik (hisse/kripto) → jenerik (döviz). "dolar" neredeyse her finans
        // haberinde geçer; haber asıl bir hisse/coin ile ilgiliyse o öne çıksın diye FX SONA
        // eklenir (match() ilk-eşleşen-kazanır + MAX_ASSETS sınırı).
        List<DictEntry> dict = new ArrayList<>();

        // BIST hisse: sembol (tam token) + 4+ karakterli ad
        try {
            StockPageResponse first = stockQueryService.getPagedStockSummaries(0, STOCK_PAGE);
            addStocks(dict, first.getContent());
            for (int p = 1; p < first.getTotalPages(); p++) {
                addStocks(dict, stockQueryService.getPagedStockSummaries(p, STOCK_PAGE).getContent());
            }
        } catch (Exception e) {
            log.warn("[NewsAssetMatcher] hisse sözlüğü kurulamadı: {}", e.getMessage());
        }

        // Popüler kripto (tüm coinler — market cap sırası): sembol + ad.
        // DİKKAT: getCryptos(page,size) 0-indeksli → Bitcoin 0. sayfada; getAllCoins ile
        // tümünü alıp ilk N'i kullanmak Bitcoin/Ethereum'u atlamayı önler.
        try {
            List<CryptoMarketItem> all = cryptoMarketService.getAllCoins("try");
            List<CryptoMarketItem> coins = all.size() > 150 ? all.subList(0, 150) : all;
            for (CryptoMarketItem c : coins) {
                if (c.getId() == null) continue;
                RelatedAsset asset = new RelatedAsset(c.getId(),
                        c.getName() != null ? c.getName() : c.getId(), "CRYPTO");
                // Sembol (BTC/ETH) tam token; çok kısa (<3) + yaygın-kelime sembolleri atla
                if (c.getSymbol() != null && c.getSymbol().length() >= 3
                        && !COMMON_WORD_BLACKLIST.contains(c.getSymbol().toLowerCase(TR))) {
                    dict.add(new DictEntry(c.getSymbol().toLowerCase(TR), asset, true));
                }
                // İsim (Bitcoin/Ethereum) — 4+ karakter, yaygın kelime değilse
                if (c.getName() != null && c.getName().length() >= 4
                        && !COMMON_WORD_BLACKLIST.contains(c.getName().toLowerCase(TR))) {
                    dict.add(new DictEntry(c.getName().toLowerCase(TR), asset, false));
                }
            }
        } catch (Exception e) {
            log.warn("[NewsAssetMatcher] kripto sözlüğü kurulamadı: {}", e.getMessage());
        }

        // Döviz EN SONA (jenerik "dolar/euro" haber asıl bir hisse/coin ile ilgiliyse onun gerisinde kalsın)
        dict.addAll(FX_DICT);

        return dict;
    }

    private void addStocks(List<DictEntry> dict, List<StockSummary> content) {
        if (content == null) return;
        for (StockSummary s : content) {
            if (s.getSymbol() == null) continue;
            // Sembolden .IS uzantısını ayır (THYAO.IS → THYAO); route bütün sembolü ister
            String base = s.getSymbol();
            String token = base.contains(".") ? base.substring(0, base.indexOf('.')) : base;
            if (token.length() < 3) continue; // 2 harfli kod yaygın kelimelerle çakışır
            String tokenLc = token.toLowerCase(TR);
            RelatedAsset asset = new RelatedAsset(base, s.getName() != null ? s.getName() : token, "STOCK");
            // Sembol token'ı yaygın Türkçe kelime değilse ekle (ör. HEDEF/ŞOK/KOÇ atlanır)
            if (!COMMON_WORD_BLACKLIST.contains(tokenLc)) {
                dict.add(new DictEntry(tokenLc, asset, true));
            }
            if (s.getName() != null && s.getName().length() >= 5
                    && !COMMON_WORD_BLACKLIST.contains(s.getName().toLowerCase(TR))) {
                dict.add(new DictEntry(s.getName().toLowerCase(TR), asset, false));
            }
        }
    }

    /**
     * Haber metninde geçen varlıkları tespit eder. Aynı varlık birden çok anahtarla
     * eşleşse de bir kez döner (symbol+type bazında tekilleştirme). En fazla {@code MAX_ASSETS}.
     */
    public List<RelatedAsset> match(String title, String description, String content) {
        String text = ((title != null ? title : "") + " \n "
                + (description != null ? description : "") + " \n "
                + (content != null ? content : "")).toLowerCase(TR);
        if (text.isBlank()) return List.of();

        List<DictEntry> dict;
        try {
            dict = getDictionary();
        } catch (Exception e) {
            log.warn("[NewsAssetMatcher] sözlük alınamadı: {}", e.getMessage());
            return List.of();
        }

        // symbol|type → asset (ilk eşleşme kazanır; tekilleştirme)
        Map<String, RelatedAsset> found = new LinkedHashMap<>();
        for (DictEntry e : dict) {
            if (found.size() >= MAX_ASSETS) break;
            String dedupKey = e.asset().symbol() + "|" + e.asset().type();
            if (found.containsKey(dedupKey)) continue;
            if (containsKey(text, e.key(), e.wholeWord())) {
                found.put(dedupKey, e.asset());
            }
        }
        return new ArrayList<>(found.values());
    }

    /** Kelime-sınırı duyarlı içerik kontrolü (Türkçe karakterler kelime karakteri sayılır). */
    private static boolean containsKey(String text, String key, boolean wholeWord) {
        if (key == null || key.isBlank()) return false;
        if (!wholeWord) {
            return text.contains(key);
        }
        // \b ASCII odaklı; TR harfleri için elle sınır: anahtarın iki yanında harf/rakam olmamalı
        Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(key) + "(?![\\p{L}\\p{N}])");
        Matcher m = p.matcher(text);
        return m.find();
    }
}
