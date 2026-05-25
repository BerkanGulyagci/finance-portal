package com.finance.portal.news.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Haber başlığı/özetinden kategori çıkarımı (TR + EN anahtar kelimeler).
 * Öncelik sırası: en spesifik kategoriler önce kontrol edilir; eşleşme yoksa feed varsayılanı kullanılır.
 *
 * <p>İki tür eşleşme var:
 * <ul>
 *   <li><b>loose</b> (Türkçe/uzun kelimeler): yalnız KELİME BAŞI sınırlı, son ek serbest →
 *       "faiz" → "faizi/faizler", "borsa" → "borsada" eşleşir; "saltın" içindeki "altın" eşleşmez.</li>
 *   <li><b>exact</b> (kısa/İngilizce/kısaltma): İKİ TARAFLI sınırlı → "gold" yalnız bağımsız kelime;
 *       "Goldman"/"ipotek"(ipo)/"coincidence"(coin)/"bistro"(bist) tetiklemez.</li>
 * </ul>
 * Türkçe harfler (ı/ş/ğ/ç/ö/ü) kelime karakteri sayılır (UNICODE_CHARACTER_CLASS).
 */
public final class NewsClassifier {

    private static final String LEAD = "(?<![\\p{L}\\p{N}])";   // kelime başı: öncesinde harf/rakam yok
    private static final String TRAIL = "(?![\\p{L}\\p{N}])";   // kelime sonu: sonrasında harf/rakam yok

    // Sıra önemlidir: kripto/emtia/döviz/tahvil/hisse genel ekonomiden önce denenir.
    private static final List<Map.Entry<NewsCategory, Pattern>> RULES = List.of(
            Map.entry(NewsCategory.CRYPTO, pattern(
                    List.of("bitcoin", "ethereum", "kripto", "blockchain", "altcoin", "binance",
                            "ripple", "solana", "dogecoin", "stablecoin", "cryptocurrency", "memecoin",
                            "satoshi", "tether"),
                    List.of("btc", "eth", "coin", "xrp", "nft", "defi", "usdt"))),
            Map.entry(NewsCategory.COMMODITIES, pattern(
                    List.of("altın", "gümüş", "petrol", "emtia", "bakır", "doğalgaz", "platin",
                            "paladyum", "değerli metal"),
                    List.of("gold", "silver", "brent", "copper", "commodity", "crude oil", "natural gas"))),
            Map.entry(NewsCategory.FX, pattern(
                    List.of("dolar", "euro", "sterlin", "döviz", "parite", "forex", "serbest piyasa"),
                    List.of("kur", "usd/try", "eur/try", "usdtry", "eurtry", "exchange rate",
                            "currency", "dolar/tl", "euro/tl"))),
            Map.entry(NewsCategory.BONDS, pattern(
                    List.of("tahvil", "bono", "faiz", "getiri eğrisi"),
                    List.of("bond", "yield", "dibs", "eurobond", "interest rate", "ppk"))),
            Map.entry(NewsCategory.STOCKS, pattern(
                    List.of("hisse", "borsa", "endeks", "halka arz", "temettü", "bilanço", "şirket karı"),
                    List.of("bist", "ipo", "stock", "share", "equity", "nasdaq", "dow jones",
                            "s&p 500", "wall street")))
    );

    private NewsClassifier() {
    }

    /**
     * Başlık+özetten kategori çıkarır; spesifik bir eşleşme yoksa {@code fallback} döner.
     */
    public static NewsCategory classify(String title, String description, NewsCategory fallback) {
        String text = ((title != null ? title : "") + " " + (description != null ? description : ""))
                .toLowerCase(java.util.Locale.forLanguageTag("tr"));
        for (Map.Entry<NewsCategory, Pattern> rule : RULES) {
            if (rule.getValue().matcher(text).find()) {
                return rule.getKey();
            }
        }
        return fallback != null ? fallback : NewsCategory.ECONOMY;
    }

    /** Kategori için loose (kelime-başı, ek serbest) + exact (iki taraflı) keyword'lerini tek regex'e derler. */
    private static Pattern pattern(List<String> loose, List<String> exact) {
        List<String> parts = new ArrayList<>();
        if (!loose.isEmpty()) {
            parts.add(LEAD + "(?:" + alternation(loose) + ")");
        }
        if (!exact.isEmpty()) {
            parts.add(LEAD + "(?:" + alternation(exact) + ")" + TRAIL);
        }
        return Pattern.compile(String.join("|", parts), Pattern.UNICODE_CHARACTER_CLASS);
    }

    private static String alternation(List<String> keywords) {
        return keywords.stream().map(Pattern::quote).collect(Collectors.joining("|"));
    }
}
