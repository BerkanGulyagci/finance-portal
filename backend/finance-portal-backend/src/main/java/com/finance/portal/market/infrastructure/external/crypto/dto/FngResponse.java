package com.finance.portal.market.infrastructure.external.crypto.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * alternative.me {@code /fng} kök yanıtı.
 *
 * <pre>{"name":"Fear and Greed Index","data":[{...}],"metadata":{"error":null}}</pre>
 *
 * Sadece {@code data} dizisi tüketilir; diğer alanlar (name/metadata) yok sayılır.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FngResponse {

    private List<FngEntry> data;
}
