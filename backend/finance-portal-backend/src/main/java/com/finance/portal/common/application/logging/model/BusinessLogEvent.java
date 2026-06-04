package com.finance.portal.common.application.logging.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusinessLogEvent {

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
    private String entityType;
    private String entityId;
    private String action;
    private String result;
    private Map<String, Object> metadata;
    private String logger;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final BusinessLogEvent event = new BusinessLogEvent();

        public Builder timestamp(String v)    { event.timestamp    = v; return this; }
        public Builder level(String v)        { event.level        = v; return this; }
        public Builder serviceName(String v)  { event.serviceName  = v; return this; }
        public Builder category(String v)     { event.category     = v; return this; }
        public Builder eventType(String v)    { event.eventType    = v; return this; }
        public Builder message(String v)      { event.message      = v; return this; }
        public Builder traceId(String v)      { event.traceId      = v; return this; }
        public Builder spanId(String v)       { event.spanId       = v; return this; }
        public Builder requestId(String v)    { event.requestId    = v; return this; }
        public Builder userId(String v)       { event.userId       = v; return this; }
        public Builder clientIp(String v)     { event.clientIp     = v; return this; }
        public Builder method(String v)       { event.method       = v; return this; }
        public Builder path(String v)         { event.path         = v; return this; }
        public Builder entityType(String v)   { event.entityType   = v; return this; }
        public Builder entityId(String v)     { event.entityId     = v; return this; }
        public Builder action(String v)       { event.action       = v; return this; }
        public Builder result(String v)       { event.result       = v; return this; }
        public Builder metadata(Map<String, Object> v) { event.metadata = v; return this; }
        public Builder logger(String v)       { event.logger       = v; return this; }

        public BusinessLogEvent build() {
            return event;
        }
    }
}
