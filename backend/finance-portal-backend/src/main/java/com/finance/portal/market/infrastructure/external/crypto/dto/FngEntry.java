package com.finance.portal.market.infrastructure.external.crypto.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * alternative.me {@code /fng} data dizisinin tek elemanı. Tüm alanlar String döner.
 *
 * <pre>{"value":"12","value_classification":"Extreme Fear","timestamp":"1780704000"}</pre>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FngEntry {

    /** Endeks değeri 0-100 (String → int'e çevrilir). */
    private String value;

    /** Sınıf metni: Extreme Fear / Fear / Neutral / Greed / Extreme Greed. */
    @JsonProperty("value_classification")
    private String valueClassification;

    /** Ölçüm zamanı, unix SANİYE (String). */
    private String timestamp;
}
