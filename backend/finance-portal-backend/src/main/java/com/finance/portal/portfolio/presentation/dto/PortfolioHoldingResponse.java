package com.finance.portal.portfolio.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.finance.portal.common.domain.AssetType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PortfolioHoldingResponse {

    private String symbol;
    private AssetType assetType;
    private BigDecimal totalQuantity;
    private BigDecimal averageCost;
    private BigDecimal totalCost;

    /**
     * Pozisyon kapalı mı (openQty=0)? Şu an yalnız BOND için satır olarak kalıyor:
     * vade itfası veya manuel tam-satıştan sonra holding listesinde realized K/Z görünür kalsın
     * diye {@code closed=true} bayrakla emit edilir. Diğer asset tipleri kapanınca listeden düşer.
     */
    private boolean closed;

    /**
     * Pozisyon kapalıysa (BOND için): hiç SELL yapılmadan önce yatırılan toplam maliyet (TL).
     * Toplam getiri yüzdesi {@code realizedGainLoss / initialCost × 100} ile hesaplanabilir.
     */
    private BigDecimal initialCost;

    /**
     * BOND için finansal kategori (TR sınıflandırma).
     * {@link com.finance.portal.market.application.bond.evds.model.BondCategory} enum adı.
     * Frontend bu alanı TÜFE-endeksli rozeti, strip uyarısı vb. için kullanır.
     */
    private String category;
    private BigDecimal currentPrice;
    private BigDecimal marketValue;
    private BigDecimal profitLoss;
    private String currency;
    private LocalDateTime asOf;

    // ── Enriched market fields (populated when live price is available) ─────────
    /** Instrument display name (e.g. "Türk Hava Yolları", "Bitcoin"). */
    private String name;
    /** Daily price change amount (current - previousClose). */
    private BigDecimal change;
    /** Daily price change percentage. */
    private BigDecimal changePercent;
    /** Daily trading volume. */
    private Long volume;
    /** Intraday high. */
    private BigDecimal dayHigh;
    /** Intraday low. */
    private BigDecimal dayLow;
    /** 52-week high (STOCK / FUTURE only). */
    private BigDecimal fiftyTwoWeekHigh;
    /** 52-week low (STOCK / FUTURE only). */
    private BigDecimal fiftyTwoWeekLow;
    /** Market capitalisation (CRYPTO only for now). */
    private BigDecimal marketCap;

    // ── Fund-specific return fields (FUND type — for trend computation) ─────────
    /** 1-day return % from Rasyonet (FUND). Used by computeTrend() fund-momentum branch. */
    private BigDecimal returnOneDay;
    /** 1-month return % from Rasyonet (FUND). */
    private BigDecimal returnOneMonth;
    /** 3-month return % from Rasyonet (FUND). */
    private BigDecimal returnThreeMonths;

    // ── Crypto-specific periodic change fields (CRYPTO — for trend computation) ─
    /** 7-day price change percentage (CRYPTO). Used by computeTrend() crypto-7d branch. */
    private BigDecimal priceChangePercentage7d;

    // ── Technical moving averages (STOCK / FUTURE / COMMODITY — computed from chart data) ──
    /** 20-period simple moving average of daily close prices. */
    private BigDecimal ma20;
    /** 50-period simple moving average of daily close prices. */
    private BigDecimal ma50;

    // ── VİOP (FUTURE) ek alanları — yalnız assetType=FUTURE için doldurulur ──────
    /** Bir kontratın temsil ettiği birim sayısı (100 hisse, 10 endeks puanı, 1000 USD, 1 gram, vb.). */
    private BigDecimal viopMultiplier;
    /** Başlangıç teminat oranı (decimal, örn. 0.146 = %14.6 — Takasbank PSR). */
    private BigDecimal viopMarginRate;
    /** Nominal pozisyon büyüklüğü (qty × güncel fiyat × multiplier) — risk göstergesi. */
    private BigDecimal viopNotional;
    /** Bağlanan başlangıç teminatı (qty × giriş fiyatı × multiplier × marginRate) — gerçek cüzdan etkisi. */
    private BigDecimal viopMarginPosted;
    /** Kaldıraç (notional / margin) — kaç katı pozisyon kontrol ediliyor. */
    private BigDecimal viopLeverage;
    /** Pozisyon yönü: "LONG" (yükselişe oynama) veya "SHORT" (düşüşe oynama). */
    private String viopDirection;

    // ── Transaction date metadata ─────────────────────────────────────────────
    /** Date of the first BUY transaction for this holding. */
    private LocalDateTime firstBuyDate;
    /** Date of the most recent transaction (BUY or SELL) for this holding. */
    private LocalDateTime lastTransactionDate;

    /** Cumulative realized P/L from SELLs (null if no SELL for this symbol while position open). */
    private BigDecimal realizedGainLoss;
    /**
     * Aggregate realized P/L as % of total cost basis of sold shares:
     * (sum of realized $) / (sum of sold cost basis) × 100. Null if no SELL or zero basis.
     */
    private BigDecimal realizedGainLossPercent;

    // ── Enflasyona göre düzeltilmiş (reel) getiri — KENDİ para birimi çerçevesi ──
    /**
     * Reel K/Z (kendi para birimi cinsinden): güncel değer − (maliyet × ilk alıştan bugüne enflasyon faktörü).
     * TL pozisyon → TÜFE; USD pozisyon → ABD CPI. Faktör/veri eksikse null.
     */
    private BigDecimal realProfitLoss;
    /** Reel getiri yüzdesi (kendi para birimi enflasyonundan arındırılmış). */
    private BigDecimal realProfitLossPercent;
    /** İlk alış tarihinden bugüne birikimli enflasyon (%) — kendi para biriminin enflasyonu. */
    private BigDecimal inflationSincePercent;
    /** Yukarıdaki "kendi para birimi" reel hesabında kullanılan enflasyon kaynağı: "TÜFE" veya "ABD TÜFE". */
    private String inflationSource;

    // ── Enflasyona göre düzeltilmiş (reel) getiri — TL ALIM GÜCÜ çerçevesi ──────
    /**
     * Reel K/Z (TL alım gücü cinsinden): tutarlar güncel kurla TL'ye çevrilip TÜFE ile arındırılır.
     * TL pozisyonlarda "kendi para birimi" reel ile aynıdır; USD pozisyonlarda Türk yatırımcının
     * TL alım gücü açısından gerçek getiriyi verir. Veri eksikse null.
     */
    private BigDecimal realProfitLossTry;
    /** TL alım gücü cinsinden reel getiri yüzdesi (TÜFE'den arındırılmış). */
    private BigDecimal realProfitLossPercentTry;
    /** İlk alıştan bugüne birikimli TÜFE enflasyonu (%) — TL çerçevesi. */
    private BigDecimal inflationSinceTryPercent;

    /**
     * Açık pozisyona katkı veren BUY lotları (varlığın kendi para biriminde maliyet katkıları).
     * Average-cost mantığıyla aynı: her SELL pre-sell openQty'ye göre tüm lotları orantısal
     * şekilde küçültür. Reel K/Z ve What-if hesabı her lotu kendi alış tarihinden bugüne ayrı
     * şekilde enflasyon/scenario faktörüyle çarpar (çoklu BUY'da firstBuyDate ile sapma giderilir).
     * Sadece backend hesabı içindir → JSON çıktısına yansımaz.
     */
    @JsonIgnore
    private List<CostLot> openCostLots;

    /**
     * {@link #openCostLots} elemanı: alış tarihi + maliyet katkısı (varlığın kendi para biriminde)
     * + kalan nominal/adet (SELL'de maliyetle aynı oranda küçülür). {@code qty}, What-if "Gerçek"
     * çizgisinin gerçek piyasa değerini (qty × fiyat) hesaplaması için gerekir; eski/test verisinde
     * bilinmiyorsa {@code null} (2-arg constructor) — çağıran eski ratio davranışına düşer.
     */
    public record CostLot(LocalDate buyDate, BigDecimal cost, BigDecimal qty) {
        public CostLot(LocalDate buyDate, BigDecimal cost) {
            this(buyDate, cost, null);
        }
    }
}
