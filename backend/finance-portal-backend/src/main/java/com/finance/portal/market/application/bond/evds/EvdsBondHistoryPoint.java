package com.finance.portal.market.application.bond.evds;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tarihsel grafik için tek bir günlük EVDS gösterge değeri noktası.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvdsBondHistoryPoint {

    /** Tarih — ISO format (yyyy-MM-dd) */
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** Tarih — görüntüleme formatı (dd-MM-yyyy) */
    private String dateText;

    /** Kıymet kodu */
    private String instrumentCode;

    /** TCMB EVDS Gösterge Değeri */
    private BigDecimal indicatorValue;
}
