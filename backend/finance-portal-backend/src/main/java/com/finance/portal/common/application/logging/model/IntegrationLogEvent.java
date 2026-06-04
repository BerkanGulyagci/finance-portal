package com.finance.portal.common.application.logging.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntegrationLogEvent {

    private String timestamp;
    private String level;
    private String serviceName;
    private String category;
    private String eventType;
    private String message;
    private String traceId;
    private String spanId;
    private String requestId;
    private String userId;
    private String clientIp;
    private String method;
    private String path;
    private String provider;
    private String operation;
    private String httpStatus;
    private Long durationMs;
    private Boolean fallbackUsed;
    private Boolean degraded;
    private Map<String, Object> metadata;
    private String logger;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final IntegrationLogEvent event = new IntegrationLogEvent();

        public Builder timestamp(String v)       { event.timestamp = v; return this; }
        public Builder level(String v)           { event.level = v; return this; }
        public Builder serviceName(String v)     { event.serviceName = v; return this; }
        public Builder category(String v)        { event.category = v; return this; }
        public Builder eventType(String v)       { event.eventType = v; return this; }
        public Builder message(String v)         { event.message = v; return this; }
        public Builder traceId(String v)         { event.traceId = v; return this; }
        public Builder spanId(String v)          { event.spanId = v; return this; }
        public Builder requestId(String v)       { event.requestId = v; return this; }
        public Builder userId(String v)          { event.userId = v; return this; }
        public Builder clientIp(String v)        { event.clientIp = v; return this; }
        public Builder method(String v)          { event.method = v; return this; }
        public Builder path(String v)            { event.path = v; return this; }
        public Builder provider(String v)        { event.provider = v; return this; }
        public Builder operation(String v)       { event.operation = v; return this; }
        public Builder httpStatus(String v)      { event.httpStatus = v; return this; }
        public Builder durationMs(Long v)        { event.durationMs = v; return this; }
        public Builder fallbackUsed(Boolean v)   { event.fallbackUsed = v; return this; }
        public Builder degraded(Boolean v)       { event.degraded = v; return this; }
        public Builder metadata(Map<String, Object> v) { event.metadata = v; return this; }
        public Builder logger(String v)          { event.logger = v; return this; }

        public IntegrationLogEvent build() {
            return event;
        }
    }
}
