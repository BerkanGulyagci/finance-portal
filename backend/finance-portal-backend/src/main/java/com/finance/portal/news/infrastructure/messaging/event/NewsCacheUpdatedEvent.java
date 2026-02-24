package com.finance.portal.news.infrastructure.messaging.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class NewsCacheUpdatedEvent {

    public static final String EVENT_TYPE = "NEWS_CACHE_UPDATED";
    public static final String SOURCE = "external-news-api";

    private String eventType = EVENT_TYPE;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    private int itemCount;

    private String source = SOURCE;

    public NewsCacheUpdatedEvent() {
    }

    public NewsCacheUpdatedEvent(LocalDateTime timestamp, int itemCount) {
        this.timestamp = timestamp;
        this.itemCount = itemCount;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
