package com.finance.portal.market.application.bond.eurobond.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Business Insider tahvil detayı (Bond Data tablosu + başlıktaki canlı fiyat).
 * Statik künye alanlarının çoğu HMB listesinde de var; BI bunlara denomination/ihraç fiyatı,
 * ödeme sıklığı, kupon tarihleri ve canlı fiyat/getiri ekler. {@code instrumentId}+{@code tkData}
 * grafiği çekmek için kullanılır.
 */
@Getter
@Setter
public class EurobondDetail {
    private String isin;
    private String name;
    private String issuer;
    private String country;
    private String currency;
    private String couponRate;          // "5.200%"
    private String issueVolume;         // "1,500,000,000"
    private String issuePrice;          // "99.99"
    private String issueDate;           // "7/17/2025"
    private String maturityDate;        // "8/17/2031"
    private String denomination;        // "1000"
    private String paymentType;         // "regular interest"
    private String couponPaymentDate;
    private String paymentsPerYear;     // "1,0"
    private String couponStartDate;
    private String finalCouponDate;

    private BigDecimal lastPrice;       // başlık fiyatı (kote — döviz cinsinden, currency alanı)
    private BigDecimal change;
    private BigDecimal changePercent;
    private String asOf;

    private BigDecimal lastPriceTry;    // canlı TCMB kuru ile TL karşılığı (lastPrice × fxRate)
    private BigDecimal fxRate;          // kullanılan birim TCMB satış kuru (USD/EUR/JPY → TRY)

    private long instrumentId;
    private String tkData;              // Chart_GetChartData için ("1,627799832,1330,333")
    private String detailUrl;           // BI detay sayfası tam URL

    // ── Birikmiş faiz / kirli fiyat (tahmini — bilgi amaçlı; portföy değerlemesini ETKİLEMEZ) ──
    // Kupon künyesinden (couponRate + paymentsPerYear + kupon tarihleri) bugünkü tarih için
    // hesaplanır (AccruedInterestCalculator, para-birimine göre day-count). Veri kaynağı kupon
    // konvansiyonunu vermediği için tahminidir. Tümü 100 nominal başına (kote ile aynı ölçek).
    private BigDecimal accruedInterest; // birikmiş faiz, 100 nominal başına (ör. 3.9583)
    private BigDecimal dirtyPrice;      // temiz (lastPrice) + birikmiş faiz, döviz cinsinden
    private BigDecimal dirtyPriceTry;   // kirli fiyatın canlı TCMB kuru ile TL karşılığı
    private String dayCountConvention;  // kullanılan konvansiyon adı ("THIRTY_360" / "ACT_ACT" / "ACT_365")
}
