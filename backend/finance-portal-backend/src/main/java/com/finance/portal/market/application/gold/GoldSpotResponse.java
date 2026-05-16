package com.finance.portal.market.application.gold;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * GET /api/gold/spot response.
 *
 * Ana kaynak : Borsa İstanbul Kıymetli Madenler (BIST).
 * Fallback   : Yahoo Finance GC=F (ONS/USD).
 *
 * Teorik fiyatlar BIST resmi has altın gram referansından hesaplanır.
 * Alış/satış makası, işçilik, basım primi dahil değildir.
 */
@Data
@NoArgsConstructor
public class GoldSpotResponse implements Serializable {

    // ── Kaynak metadata ───────────────────────────────────────────────────────

    /** "Borsa İstanbul" veya "Yahoo Finance Fallback" */
    private String source;

    /** Resmi BIST verisi mi? */
    private boolean official;

    /** BIST erişilemedi, fallback kullanıldı mı? */
    private boolean fallback;

    /** Veri güncel değil mi (hafta sonu / tatil)? */
    private boolean stale;

    private String lastUpdated;

    /**
     * Kullanıcıya gösterilecek uyarı.
     * "Bu fiyatlar Borsa İstanbul resmi metal fiyatından hesaplanan teorik referans
     *  değerlerdir. Serbest piyasa alış/satış, işçilik, basım primi ve makas dahil değildir."
     */
    private String disclaimer;

    // ── BIST has altın gram referansı ─────────────────────────────────────────

    /**
     * Resmi saf altın gram TL referansı = BIST weightedAverageTryKg / 1000.
     * Tüm türev hesapların tabanı.
     */
    private BigDecimal officialPureGoldGramTry;

    /** BIST kapanış gram TL (kpo / 1000) */
    private BigDecimal gramCloseTry;

    /** BIST günlük en düşük gram TL */
    private BigDecimal gramLowTry;

    /** BIST günlük en yüksek gram TL */
    private BigDecimal gramHighTry;

    /** BIST işlem hacmi TL */
    private BigDecimal volumeTry;

    /** BIST işlem miktarı Kg */
    private BigDecimal quantityKg;

    /** BIST işlem sayısı */
    private Integer transactionCount;

    /** BIST son işlem tarihi */
    private String bistDate;

    // ── Teorik türev fiyatlar ─────────────────────────────────────────────────

    /** Gram altın teorik TL = officialPureGoldGramTry */
    private BigDecimal gramGoldTry;

    /** Çeyrek altın teorik TL = officialPureGoldGramTry * 1.754 * 0.9166 */
    private BigDecimal quarterGoldTry;

    /** Yarım altın teorik TL = officialPureGoldGramTry * 3.508 * 0.9166 */
    private BigDecimal halfGoldTry;

    /** Ziynet tam altın teorik TL = officialPureGoldGramTry * 7.016 * 0.9166 */
    private BigDecimal ziynetGoldTry;

    /** Cumhuriyet / Ata altını teorik TL = officialPureGoldGramTry * 7.216 * 0.9166 */
    private BigDecimal republicGoldTry;

    /** 14 Ayar bilezik teorik TL/gram = officialPureGoldGramTry * 0.5850 */
    private BigDecimal fourteenKBraceletTry;

    /** 22 Ayar bilezik teorik TL/gram = officialPureGoldGramTry * 0.9166 */
    private BigDecimal twentyTwoKBraceletTry;

    // ── ONS (Yahoo) ───────────────────────────────────────────────────────────

    private BigDecimal onsUsd;
    /** Ons altın TL (onsUsd × TCMB USD/TRY), portföy ve modal için */
    private BigDecimal onsTry;
    private BigDecimal onsChangePercent;
    private BigDecimal onsChange;
    private BigDecimal onsHigh;
    private BigDecimal onsLow;
    private BigDecimal onsPreviousClose;

    /** USD/TRY kuru */
    private BigDecimal usdTry;

    // ── Geriye dönük uyumluluk — mevcut GoldPage.jsx bu alanları okur ─────────
    // Yeni frontend refactor'dan sonra kaldırılabilir.

    private BigDecimal gramTl;
    private BigDecimal ceyrekTl;
    private BigDecimal yarimTl;
    private BigDecimal tamTl;
    private BigDecimal cumhuriyetTl;
    private BigDecimal ayar14Tl;
    private BigDecimal ayar22Tl;

    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal previousClose;
    private BigDecimal bid;
    private BigDecimal ask;
    private BigDecimal priceTl;

    private String symbol;
    private String name;
    private String currency;
    private String updatedAt;
}
