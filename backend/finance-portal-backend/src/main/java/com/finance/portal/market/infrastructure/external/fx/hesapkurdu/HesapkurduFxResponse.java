package com.finance.portal.market.infrastructure.external.fx.hesapkurdu;

import com.finance.portal.market.application.currency.model.HesapkurduFxItem;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Hesapkurdu getExchangeRates endpoint'inin tam response wrapper'ı.
 *
 * <p><b>Eski yapı:</b>
 * <pre>{@code { "isSuccess": true, "response": { "data": [ ... ] } }}</pre>
 *
 * <p><b>Güncel yapı (2025+):</b>
 * <pre>{@code { "data": { "data": [ ... ] }, "isSuccess": true, "statusCode": 200 }}</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HesapkurduFxResponse {

    @JsonProperty("isSuccess")
    private boolean isSuccess;
    /** Eski API: kök {@code response.data} */
    private HesapkurduResponseBody response;
    /** Yeni API: kök {@code data.data} */
    private HesapkurduDataEnvelope data;

    public HesapkurduFxResponse() {}

    public boolean isSuccess()                             { return isSuccess; }

    @JsonProperty("isSuccess")
    public void setSuccess(boolean success)                { this.isSuccess = success; }

    public HesapkurduResponseBody getResponse()            { return response; }
    public void setResponse(HesapkurduResponseBody r)      { this.response = r; }

    public HesapkurduDataEnvelope getData()                { return data; }
    public void setData(HesapkurduDataEnvelope data)       { this.data = data; }

    /**
     * Önce yeni ({@code data.data}), yoksa eski ({@code response.data}) döner.
     */
    public List<HesapkurduFxItem> resolveDataList() {
        if (data != null && data.getData() != null) {
            return data.getData();
        }
        if (response != null && response.getData() != null) {
            return response.getData();
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HesapkurduDataEnvelope {
        private List<HesapkurduFxItem> data;

        public List<HesapkurduFxItem> getData()            { return data; }
        public void setData(List<HesapkurduFxItem> data)   { this.data = data; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HesapkurduResponseBody {
        private List<HesapkurduFxItem> data;

        public List<HesapkurduFxItem> getData()            { return data; }
        public void setData(List<HesapkurduFxItem> data)   { this.data = data; }
    }
}
