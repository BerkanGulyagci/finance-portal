package com.finance.portal.market.infrastructure.external.fx.hesapkurdu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Hesapkurdu getExchangeRates endpoint'inin tam response wrapper'ı.
 *
 * Yapı:
 * {
 *   "isSuccess": true,
 *   "messages": [],
 *   "response": {
 *     "data": [ ... ]
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HesapkurduFxResponse {

    @JsonProperty("isSuccess")
    private boolean isSuccess;
    private HesapkurduResponseBody response;

    public HesapkurduFxResponse() {}

    public boolean isSuccess()                             { return isSuccess; }

    @JsonProperty("isSuccess")
    public void setSuccess(boolean success)                { this.isSuccess = success; }

    public HesapkurduResponseBody getResponse()            { return response; }
    public void setResponse(HesapkurduResponseBody r)      { this.response = r; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HesapkurduResponseBody {
        private List<HesapkurduFxItem> data;

        public List<HesapkurduFxItem> getData()            { return data; }
        public void setData(List<HesapkurduFxItem> data)   { this.data = data; }
    }
}
