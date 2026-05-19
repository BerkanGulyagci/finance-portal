package com.finance.portal.common.application.logging.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequestLogEvent {

    private String timestamp;
    private String serviceName;
    private String level;
    private String logger;
    private String message;
    private String requestId;
    private String method;
    private String path;
    private String status;
    private String durationMs;
    private String userId;
    private String traceId;
    private String spanId;

    public RequestLogEvent() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final RequestLogEvent event = new RequestLogEvent();

        public Builder timestamp(String v)   { event.timestamp   = v; return this; }
        public Builder serviceName(String v) { event.serviceName = v; return this; }
        public Builder level(String v)       { event.level       = v; return this; }
        public Builder logger(String v)      { event.logger      = v; return this; }
        public Builder message(String v)     { event.message     = v; return this; }
        public Builder requestId(String v)   { event.requestId   = v; return this; }
        public Builder method(String v)      { event.method      = v; return this; }
        public Builder path(String v)        { event.path        = v; return this; }
        public Builder status(String v)      { event.status      = v; return this; }
        public Builder durationMs(String v)  { event.durationMs  = v; return this; }
        public Builder userId(String v)      { event.userId      = v; return this; }
        public Builder traceId(String v)     { event.traceId     = v; return this; }
        public Builder spanId(String v)      { event.spanId      = v; return this; }

        public RequestLogEvent build() {
            return event;
        }
    }

    public String getTimestamp()   { return timestamp; }
    public String getServiceName() { return serviceName; }
    public String getLevel()       { return level; }
    public String getLogger()      { return logger; }
    public String getMessage()     { return message; }
    public String getRequestId()   { return requestId; }
    public String getMethod()      { return method; }
    public String getPath()        { return path; }
    public String getStatus()      { return status; }
    public String getDurationMs()  { return durationMs; }
    public String getUserId()      { return userId; }
    public String getTraceId()     { return traceId; }
    public String getSpanId()      { return spanId; }
}
