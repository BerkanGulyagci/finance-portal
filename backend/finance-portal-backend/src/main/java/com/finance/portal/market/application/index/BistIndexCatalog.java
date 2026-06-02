package com.finance.portal.market.application.index;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BIST endeks kataloğu — kod, görünen ad (TR), kategori ve (sektör endeksleri için) bileşen hisseler.
 *
 * <p>Veri kaynağı: her endeks Yahoo Finance'te {@code {KOD}.IS} sembolüyle yayımlanır (instrumentType=INDEX,
 * TRY). Fiyat/değişim/grafik mevcut hisse uçlarından ({@code /api/market/stocks/{symbol}}) gelir — endeks
 * sembolü Yahoo açısından sıradan bir semboldür. Bu katalog yalnız hangi endekslerin listeleneceğini,
 * Türkçe adlarını ve ilgili hisselerini tanımlar.</p>
 *
 * <p><b>İlgili hisseler:</b> Büyüklük endeksleri (XU030/XU050/XU100/XUTUM) için bileşenler CANLI olarak
 * {@code StockSymbolProvider}'dan (Midas + yedek) gelir. Sektör/tema endeksleri için Midas'ta bileşen
 * sayfası YOK (404) ve Yahoo bileşen vermez; bu yüzden aşağıdaki {@link #CONSTITUENTS} küratörlü, öne
 * çıkan üyelerle doldurulmuştur (yaklaşık, kaynak: BIST sektör endeksleri, gözden geçirme 2026-06).
 * Bileşeni olmayan endeksler için detay sayfasında "ilgili hisse" bölümü gösterilmez.</p>
 */
public final class BistIndexCatalog {

    private BistIndexCatalog() {}

    public record IndexInfo(String code, String name, String category) {
        public String yahooSymbol() { return code + ".IS"; }
    }

    public static final String CAT_SIZE = "Ana";
    public static final String CAT_SECTOR = "Sektör";
    public static final String CAT_PARTICIPATION = "Katılım";
    public static final String CAT_THEME = "Tema";

    /** Sıralı katalog (görünüm sırası: büyüklük → süper-sektör → sektör → katılım → tema). */
    private static final List<IndexInfo> ALL = List.of(
            // ── Büyüklük / ana endeksler ────────────────────────────────────────────
            new IndexInfo("XU100", "BIST 100", CAT_SIZE),
            new IndexInfo("XU030", "BIST 30", CAT_SIZE),
            new IndexInfo("XU050", "BIST 50", CAT_SIZE),
            new IndexInfo("XU500", "BIST 500", CAT_SIZE),
            new IndexInfo("XUTUM", "BIST Tüm", CAT_SIZE),
            new IndexInfo("XBANA", "BIST Ana", CAT_SIZE),
            new IndexInfo("X10XB", "BIST Banka Dışı Likit 10", CAT_SIZE),
            // ── Süper-sektör ────────────────────────────────────────────────────────
            new IndexInfo("XUSIN", "BIST Sınai", CAT_SECTOR),
            new IndexInfo("XUHIZ", "BIST Hizmetler", CAT_SECTOR),
            new IndexInfo("XUMAL", "BIST Mali", CAT_SECTOR),
            new IndexInfo("XUTEK", "BIST Teknoloji", CAT_SECTOR),
            // ── Sektör ──────────────────────────────────────────────────────────────
            new IndexInfo("XBANK", "BIST Banka", CAT_SECTOR),
            new IndexInfo("XHOLD", "BIST Holding ve Yatırım", CAT_SECTOR),
            new IndexInfo("XGIDA", "BIST Gıda, İçecek", CAT_SECTOR),
            new IndexInfo("XKMYA", "BIST Kimya, Petrol, Plastik", CAT_SECTOR),
            new IndexInfo("XMESY", "BIST Metal Eşya, Makina", CAT_SECTOR),
            new IndexInfo("XMANA", "BIST Metal Ana", CAT_SECTOR),
            new IndexInfo("XTAST", "BIST Taş, Toprak", CAT_SECTOR),
            new IndexInfo("XELKT", "BIST Elektrik", CAT_SECTOR),
            new IndexInfo("XILTM", "BIST İletişim", CAT_SECTOR),
            new IndexInfo("XBLSM", "BIST Bilişim", CAT_SECTOR),
            new IndexInfo("XTCRT", "BIST Ticaret", CAT_SECTOR),
            new IndexInfo("XULAS", "BIST Ulaştırma", CAT_SECTOR),
            new IndexInfo("XSGRT", "BIST Sigorta", CAT_SECTOR),
            new IndexInfo("XFINK", "BIST Finansal Kiralama, Faktoring", CAT_SECTOR),
            new IndexInfo("XGMYO", "BIST Gayrimenkul Yatırım Ortaklığı", CAT_SECTOR),
            new IndexInfo("XMADN", "BIST Madencilik", CAT_SECTOR),
            new IndexInfo("XTEKS", "BIST Tekstil, Deri", CAT_SECTOR),
            new IndexInfo("XKAGT", "BIST Orman, Kağıt, Basım", CAT_SECTOR),
            new IndexInfo("XINSA", "BIST İnşaat", CAT_SECTOR),
            new IndexInfo("XTRZM", "BIST Turizm", CAT_SECTOR),
            new IndexInfo("XSPOR", "BIST Spor", CAT_SECTOR),
            // ── Katılım ─────────────────────────────────────────────────────────────
            new IndexInfo("XK100", "BIST Katılım 100", CAT_PARTICIPATION),
            new IndexInfo("XK050", "BIST Katılım 50", CAT_PARTICIPATION),
            new IndexInfo("XK030", "BIST Katılım 30", CAT_PARTICIPATION),
            new IndexInfo("XKTUM", "BIST Katılım Tüm", CAT_PARTICIPATION),
            // ── Tema ────────────────────────────────────────────────────────────────
            new IndexInfo("XTMTU", "BIST Temettü", CAT_THEME),
            new IndexInfo("XTM25", "BIST Temettü 25", CAT_THEME),
            new IndexInfo("XKURY", "BIST Kurumsal Yönetim", CAT_THEME),
            new IndexInfo("XHARZ", "BIST Halka Arz", CAT_THEME),
            new IndexInfo("XYORT", "BIST Menkul Kıymet Yatırım Ortaklığı", CAT_THEME)
    );

    private static final Map<String, IndexInfo> BY_CODE = new LinkedHashMap<>();
    static {
        for (IndexInfo i : ALL) BY_CODE.put(i.code(), i);
    }

    public static List<IndexInfo> all() { return ALL; }

    public static IndexInfo byCode(String code) {
        return code == null ? null : BY_CODE.get(code.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * Sektör/tema endekslerinin küratörlü bileşenleri (öne çıkan üyeler; sembol .IS'siz).
     * Büyüklük endeksleri (XU030/050/100/UTUM) burada YOK — onlar StockSymbolProvider'dan canlı gelir.
     * Liste kapsamlı değil, tanınan büyük/orta üyelerle sınırlıdır (gözden geçirme 2026-06).
     */
    private static final Map<String, List<String>> CONSTITUENTS = Map.ofEntries(
            Map.entry("XBANK", List.of(
                    "GARAN", "AKBNK", "ISCTR", "YKBNK", "VAKBN", "HALKB", "TSKB", "SKBNK", "ALBRK", "ICBCT", "QNBTR")),
            Map.entry("XHOLD", List.of(
                    "KCHOL", "SAHOL", "AGHOL", "DOHOL", "GLYHO", "ALARK", "NTHOL", "TKFEN", "ECILC", "IEYHO", "ISGSY")),
            Map.entry("XGIDA", List.of(
                    "AEFES", "CCOLA", "ULKER", "TATGD", "BANVT", "PNSUT", "TUKAS", "KERVT", "PETUN", "KNFRT", "SELGD")),
            Map.entry("XKMYA", List.of(
                    "TUPRS", "PETKM", "SASA", "GUBRF", "HEKTS", "BAGFS", "ALKIM", "EGGUB", "DYOBY", "SODSN", "GOODY")),
            Map.entry("XMESY", List.of(
                    "FROTO", "TOASO", "ARCLK", "VESTL", "VESBE", "TTRAK", "OTKAR", "KARSN", "ASUZU", "EGEEN", "KLMSN", "KATMR", "JANTS", "PARSN")),
            Map.entry("XMANA", List.of(
                    "EREGL", "KRDMD", "KRDMA", "KRDMB", "ISDMR", "CEMTS", "DMSAS", "CUSAN")),
            Map.entry("XTAST", List.of(
                    "CIMSA", "AKCNS", "OYAKC", "BTCIM", "BUCIM", "NUHCM", "GOLTS", "KONYA", "BSOKE", "CMBTN", "AFYON")),
            Map.entry("XELKT", List.of(
                    "ENJSA", "AKSEN", "ZOREN", "ODAS", "AKENR", "BIOEN", "NATEN", "ENERY", "ARASE", "GWIND", "AYEN")),
            Map.entry("XILTM", List.of(
                    "TCELL", "TTKOM")),
            Map.entry("XBLSM", List.of(
                    "LOGO", "NETAS", "KAREL", "INDES", "ARENA", "LINK", "ESCOM", "DESPC", "ARDYZ", "FONET", "MTRKS", "PKART", "KFEIN", "OBASE", "MIATK")),
            Map.entry("XUTEK", List.of(
                    "ASELS", "LOGO", "NETAS", "KAREL", "INDES", "ARENA", "LINK", "ARDYZ", "MIATK", "ALCTL", "KFEIN", "PKART", "FONET")),
            Map.entry("XTCRT", List.of(
                    "BIMAS", "MGROS", "SOKM", "MAVI", "BIZIM", "CRFSA", "TKNSA", "VAKKO", "SELEC", "DOAS", "INTEM")),
            Map.entry("XULAS", List.of(
                    "THYAO", "PGSUS", "TAVHL", "CLEBI", "RYSAS", "BEYAZ", "GSDDE", "TLMAN")),
            Map.entry("XSGRT", List.of(
                    "TURSG", "ANSGR", "AKGRT", "AGESA", "RAYSG", "ANHYT")),
            Map.entry("XFINK", List.of(
                    "ISFIN", "GARFA", "LIDFA", "CRDFA", "ULUFA", "VAKFN")),
            Map.entry("XGMYO", List.of(
                    "EKGYO", "ISGYO", "TRGYO", "OZKGY", "AKSGY", "KLGYO", "RYGYO", "SRVGY", "NUGYO", "DZGYO", "VKGYO")),
            Map.entry("XMADN", List.of(
                    "KOZAL", "KOZAA", "IPEKE", "PRKME")),
            Map.entry("XTEKS", List.of(
                    "KORDS", "YUNSA", "MNDRS", "BLCYT", "DAGI", "DESA", "ARSAN", "DERIM", "ATEKS", "RODRG")),
            Map.entry("XKAGT", List.of(
                    "KARTN", "BAKAB", "DURDO", "VKING", "OZRDN", "KAPLM")),
            Map.entry("XINSA", List.of(
                    "ENKAI", "TKFEN", "YYAPI", "ORGE", "ANELE", "KUYAS")),
            Map.entry("XTRZM", List.of(
                    "MAALT", "AYCES", "TEKTU", "MARTI", "UTPYA", "METUR", "PKENT")),
            Map.entry("XSPOR", List.of(
                    "GSRAY", "FENER", "BJKAS", "TSPOR")),
            // BIST Banka Dışı Likit 10 — en likit 10 banka-dışı (BIST 30'dan, banka hariç).
            Map.entry("X10XB", List.of(
                    "ASELS", "THYAO", "KCHOL", "SAHOL", "FROTO", "EREGL", "BIMAS", "TUPRS", "TCELL", "SISE"))
    );

    /**
     * Süper-sektör → alt sektör kodları. Bu endekslerin bileşenleri çocuk sektörlerin küratörlü
     * listelerinin BİRLEŞİMİdir (XUMAL=mali sektörler, XUSIN=sınai, XUHIZ=hizmet).
     */
    private static final Map<String, List<String>> SUPER_SECTORS = Map.of(
            "XUMAL", List.of("XBANK", "XSGRT", "XHOLD", "XGMYO", "XFINK", "XYORT"),
            "XUSIN", List.of("XGIDA", "XKMYA", "XMANA", "XMESY", "XTAST", "XTEKS", "XKAGT"),
            "XUHIZ", List.of("XTCRT", "XULAS", "XTRZM", "XILTM", "XELKT", "XSPOR", "XINSA", "XBLSM")
    );

    /** Sektör/tema endeksi için küratörlü bileşen kodları (.IS'siz). Yoksa boş liste. */
    public static List<String> curatedConstituents(String code) {
        if (code == null) return List.of();
        return CONSTITUENTS.getOrDefault(code.trim().toUpperCase(java.util.Locale.ROOT), List.of());
    }

    /** Süper-sektör endeksinin alt sektör kodları (bileşen = bu sektörlerin birleşimi). Yoksa boş. */
    public static List<String> superSectorChildren(String code) {
        if (code == null) return List.of();
        return SUPER_SECTORS.getOrDefault(code.trim().toUpperCase(java.util.Locale.ROOT), List.of());
    }
}
