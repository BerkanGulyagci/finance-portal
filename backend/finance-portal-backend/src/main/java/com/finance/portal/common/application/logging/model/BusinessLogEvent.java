package com.finance.portal.common.application.logging.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

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

    public BusinessLogEvent() {}

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

    public String getTimestamp()    { return timestamp; }
    public String getLevel()        { return level; }
    public String getServiceName()  { return serviceName; }
    public String getCategory()     { return category; }
    public String getEventType()    { return eventType; }
    public String getMessage()      { return message; }
    public String getTraceId()      { return traceId; }
    public String getSpanId()       { return spanId; }
    public String getRequestId()    { return requestId; }
    public String getUserId()       { return userId; }
    public String getClientIp()     { return clientIp; }
    public String getMethod()       { return method; }
    public String getPath()         { return path; }
    public String getEntityType()   { return entityType; }
    public String getEntityId()     { return entityId; }
    public String getAction()       { return action; }
    public String getResult()       { return result; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getLogger()       { return logger; }
}
