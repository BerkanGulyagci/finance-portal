package com.finance.portal.common.application.logging.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorLogEvent {

    private String timestamp;
    private String level;
    private String serviceName;
    private String category;
    private String eventType;
    private String message;
    private String exception;
    private String traceId;
    private String spanId;
    private String requestId;
    private String userId;
    private String clientIp;
    private String method;
    private String path;
    private String status;
    private String durationMs;
    private String logger;

    public ErrorLogEvent() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ErrorLogEvent event = new ErrorLogEvent();

        public Builder timestamp(String v)   { event.timestamp   = v; return this; }
        public Builder level(String v)       { event.level       = v; return this; }
        public Builder serviceName(String v) { event.serviceName = v; return this; }
        public Builder category(String v)    { event.category    = v; return this; }
        public Builder eventType(String v)   { event.eventType   = v; return this; }
        public Builder message(String v)     { event.message     = v; return this; }
        public Builder exception(String v)   { event.exception   = v; return this; }
        public Builder traceId(String v)     { event.traceId     = v; return this; }
        public Builder spanId(String v)      { event.spanId      = v; return this; }
        public Builder requestId(String v)   { event.requestId   = v; return this; }
        public Builder userId(String v)      { event.userId      = v; return this; }
        public Builder clientIp(String v)    { event.clientIp    = v; return this; }
        public Builder method(String v)      { event.method      = v; return this; }
        public Builder path(String v)        { event.path        = v; return this; }
        public Builder status(String v)      { event.status      = v; return this; }
        public Builder durationMs(String v)  { event.durationMs  = v; return this; }
        public Builder logger(String v)      { event.logger      = v; return this; }

        public ErrorLogEvent build() {
            return event;
        }
    }

    public String getTimestamp()   { return timestamp; }
    public String getLevel()       { return level; }
    public String getServiceName() { return serviceName; }
    public String getCategory()    { return category; }
    public String getEventType()   { return eventType; }
    public String getMessage()     { return message; }
    public String getException()   { return exception; }
    public String getTraceId()     { return traceId; }
    public String getSpanId()      { return spanId; }
    public String getRequestId()   { return requestId; }
    public String getUserId()      { return userId; }
    public String getClientIp()    { return clientIp; }
    public String getMethod()      { return method; }
    public String getPath()        { return path; }
    public String getStatus()      { return status; }
    public String getDurationMs()  { return durationMs; }
    public String getLogger()      { return logger; }
}
