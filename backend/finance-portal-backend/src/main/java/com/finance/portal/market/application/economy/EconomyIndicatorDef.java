package com.finance.portal.market.application.economy;

/**
 * Türkiye ekonomisi özet panelinde gösterilen göstergelerin kataloğu.
 *
 * <p>Her gösterge bir TCMB EVDS seri koduna bağlanır. Kodlar EVDS'e karşı
 * canlı doğrulanmıştır (bkz. {@code serieList} keşfi). Yeni gösterge eklemek
 * için buraya bir enum sabiti eklemek yeterlidir — servis/DTO katmanı otomatik işler.
 */
public enum EconomyIndicatorDef {

    // ── Enflasyon ────────────────────────────────────────────────────────────
    // TÜFE genel endeks (2025=100) — güncel seri. Eski TP.FG.J0 (2003=100) Oca 2026'da donduruldu.
    TUFE("tufe", "TÜFE — Tüketici Enflasyonu", "TP.TUKFIY2025.GENEL",
            Category.INFLATION, "endeks", Frequency.MONTHLY, true),
    UFE("ufe", "Yİ-ÜFE — Üretici Enflasyonu", "TP.TUFE1YI.T1",
            Category.INFLATION, "endeks", Frequency.MONTHLY, true),
    // Çekirdek C endeksi (2025=100) — güncel. Eski TP.FE.OKTG02 (2003=100) Ara 2025'te donduruldu.
    CEKIRDEK("cekirdek", "Çekirdek Enflasyon (C)", "TP.FE25.OKTG04",
            Category.INFLATION, "endeks", Frequency.MONTHLY, true),

    // ── Faizler ──────────────────────────────────────────────────────────────
    POLITIKA_FAIZI("politikaFaizi", "Politika Faizi (1 Hafta Repo)", "TP.APIFON4",
            Category.RATES, "%", Frequency.DAILY, false),
    MEVDUAT_FAIZI("mevduatFaizi", "TL Mevduat Faizi", "TP.TRY.MT02",
            Category.RATES, "%", Frequency.WEEKLY, false),
    KREDI_FAIZI("ihtiyacKredisiFaizi", "İhtiyaç Kredisi Faizi", "TP.KTF10",
            Category.RATES, "%", Frequency.WEEKLY, false),

    // ── Büyüme & Gelir ───────────────────────────────────────────────────────
    GSYIH("gsyihBuyume", "GSYİH (Reel, Zincirlenmiş Hacim)", "TP.GSYIH26.HY.ZH",
            Category.GROWTH, "bin TL", Frequency.QUARTERLY, true),
    KISI_BASI_GELIR("kisiBasiGelir", "Kişi Başı Milli Gelir", "TP.IMFGDPUSDPC.TUR",
            Category.GROWTH, "USD", Frequency.YEARLY, false),

    // ── Dış Denge & Rezerv ───────────────────────────────────────────────────
    CARI_DENGE("cariDenge", "Cari İşlemler Dengesi", "TP.ODANA6.Q01",
            Category.EXTERNAL, "milyon USD", Frequency.MONTHLY, false),
    REZERVLER("rezervler", "TCMB Toplam Rezervler", "TP.AB.B6",
            Category.EXTERNAL, "milyon USD", Frequency.MONTHLY, false),
    REER("reelEfektifKur", "Reel Efektif Döviz Kuru", "TP.RK.T1.Y",
            Category.EXTERNAL, "endeks", Frequency.MONTHLY, false),

    // ── Döviz Kurları ────────────────────────────────────────────────────────
    USD_TRY("usdTry", "Dolar / TL", "TP.DK.USD.A.YTL",
            Category.FX, "TL", Frequency.DAILY, false),
    EUR_TRY("eurTry", "Euro / TL", "TP.DK.EUR.A.YTL",
            Category.FX, "TL", Frequency.DAILY, false),

    // ── İşgücü ───────────────────────────────────────────────────────────────
    ISSIZLIK("issizlik", "İşsizlik Oranı", "TP.YISGUCU2.G8",
            Category.LABOR, "%", Frequency.MONTHLY, false),

    // ── Bütçe ────────────────────────────────────────────────────────────────
    BUTCE_DENGESI("butceDengesi", "Genel Bütçe Dengesi", "TP.KB.GEN35",
            Category.FISCAL, "bin TL", Frequency.MONTHLY, false),

    // ── Aktivite & Güven ─────────────────────────────────────────────────────
    KAPASITE("kapasiteKullanim", "Kapasite Kullanım Oranı", "TP.KKO.MA",
            Category.ACTIVITY, "%", Frequency.MONTHLY, false),
    TUKETICI_GUVEN("tuketiciGuven", "Tüketici Güven Endeksi", "TP.TG2.Y01",
            Category.ACTIVITY, "endeks", Frequency.MONTHLY, false),

    // ── Borsa & Altın ──────────────────────────────────────────────────────────
    // BIST 100 bileşik endeksi (günlük kapanış) — ham seviye grafiği gösterilir.
    BIST100("bist100", "BIST 100 Endeksi", "TP.MK.F.BILESIK",
            Category.MARKETS, "endeks", Frequency.DAILY, false),
    // Külçe altın (Kapalıçarşı, TL/gram) — EVDS'te aylık yayınlanır.
    GRAM_ALTIN("gramAltin", "Gram Altın (TL)", "TP.MK.KUL.YTL",
            Category.MARKETS, "TL/gram", Frequency.MONTHLY, false),

    // ── Küresel ──────────────────────────────────────────────────────────────
    // ABD TÜFE (CPI, mevsimsellikten arındırılmamış) — FRED'ten. YoY = ABD enflasyonu.
    ABD_CPI("abdCpi", "ABD Enflasyonu (CPI)", "CPIAUCNS",
            Category.GLOBAL, "endeks", Frequency.MONTHLY, true, Source.FRED);

    private final String key;
    private final String label;
    private final String seriesCode;
    private final Category category;
    private final String unit;
    private final Frequency frequency;
    /** Yıllık % değişim (YoY) anlamlı mı? Endeks/GSYİH için true, oran/kur için false. */
    private final boolean yoyMeaningful;
    /** Verinin hangi sağlayıcıdan çekileceği (EVDS varsayılan; ABD CPI gibi küresel seriler FRED). */
    private final Source source;

    EconomyIndicatorDef(String key, String label, String seriesCode,
                        Category category, String unit, Frequency frequency, boolean yoyMeaningful) {
        this(key, label, seriesCode, category, unit, frequency, yoyMeaningful, Source.EVDS);
    }

    EconomyIndicatorDef(String key, String label, String seriesCode,
                        Category category, String unit, Frequency frequency, boolean yoyMeaningful, Source source) {
        this.key = key;
        this.label = label;
        this.seriesCode = seriesCode;
        this.category = category;
        this.unit = unit;
        this.frequency = frequency;
        this.yoyMeaningful = yoyMeaningful;
        this.source = source;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getSeriesCode() { return seriesCode; }
    public Category getCategory() { return category; }
    public String getUnit() { return unit; }
    public Frequency getFrequency() { return frequency; }
    public boolean isYoyMeaningful() { return yoyMeaningful; }
    public Source getSource() { return source; }

    /** Veri sağlayıcı — seriyi hangi dış API'den çekeceğimizi belirler. */
    public enum Source { EVDS, FRED }

    /**
     * Negatif olabilen "akım/denge" verisi mi? (cari denge, bütçe dengesi)
     * Bu serilerde % değişim yanıltıcıdır (açık büyürken +% görünür);
     * frontend mutlak değişimi göstersin diye ipucu olarak kullanılır.
     */
    public boolean isPreferAbsolute() {
        return this == CARI_DENGE || this == BUTCE_DENGESI;
    }

    /** Gösterge kategorisi — panelde gruplama için. */
    public enum Category {
        INFLATION("Enflasyon"),
        RATES("Faizler"),
        GROWTH("Büyüme & Gelir"),
        EXTERNAL("Dış Denge & Rezerv"),
        FX("Döviz Kurları"),
        LABOR("İşgücü"),
        FISCAL("Bütçe"),
        ACTIVITY("Aktivite & Güven"),
        MARKETS("Borsa & Altın"),
        GLOBAL("Küresel");

        private final String label;
        Category(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /**
     * Seri frekansı ve veri çekiminde kullanılacak geriye dönük pencere (ay).
     * Pencere, yıllık karşılaştırma (YoY) için en az 1 yıllık veri içerecek şekilde seçilir.
     */
    public enum Frequency {
        DAILY(3),
        WEEKLY(3),
        MONTHLY(24),
        QUARTERLY(30),
        YEARLY(48);

        private final int lookbackMonths;
        Frequency(int lookbackMonths) { this.lookbackMonths = lookbackMonths; }
        public int getLookbackMonths() { return lookbackMonths; }
    }
}
