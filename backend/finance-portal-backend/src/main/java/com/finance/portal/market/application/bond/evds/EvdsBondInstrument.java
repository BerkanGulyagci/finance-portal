package com.finance.portal.market.application.bond.evds;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.bond.evds.model.BondCurrency;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * EVDS'den normalize edilmiş DİBS kıymet modeli.
 * Liste ve detay sayfası için ortak domain nesnesi.
 */
@Getter
@Setter
@NoArgsConstructor
public class EvdsBondInstrument {

    /** Kıymet kodu — örn. TRD070727K10 */
    private String instrumentCode;

    /** Tür — "Hazine Bonosu", "Devlet Tahvili", "DİBS" (legacy human-readable etiket). */
    private String type;

    /**
     * Finansal kategori — CBRT kodu + ISIN suffix'inden
     * {@link com.finance.portal.market.application.bond.evds.model.BondClassifier} ile belirlenir.
     * Hesap motorunda bu alan kullanılır.
     */
    private BondCategory category;

    /** Nominal cinsi para birimi (TRY/USD/EUR/GOLD). */
    private BondCurrency currency;

    /**
     * TCMB CBRT kodu — EVDS SERIE_NAME'nin son parantezinde
     * (örn. {@code 121T2K19240227}, {@code 61T4DK13010328}).
     * Sınıflandırma kaynağı; UI'da debug/tooltip için de gösterilebilir.
     */
    private String cbrtCode;

    /** İhraç tarihi (EVDS START_DATE) */
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate issueDate;

    /** Vade tarihi (EVDS END_DATE) */
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate maturityDate;

    /** Vadeye kalan gün (maturityDate - today) */
    private int remainingDays;

    /** TCMB EVDS Gösterge Değeri — en güncel değer */
    private BigDecimal indicatorValue;

    /** Bir önceki iş günü değeri */
    private BigDecimal previousValue;

    /** Günlük değişim = indicatorValue - previousValue */
    private BigDecimal dailyChange;

    /** Günlük değişim yüzdesi = dailyChange / previousValue * 100 */
    private BigDecimal dailyChangePercent;

    /** Kupon faiz oranı — .ORAN serisinden, yoksa null */
    private BigDecimal couponRate;

    /** Veri kaynağı — sabit "TCMB EVDS" */
    private String source;

    /** Son güncelleme tarihi */
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate lastUpdated;
}
