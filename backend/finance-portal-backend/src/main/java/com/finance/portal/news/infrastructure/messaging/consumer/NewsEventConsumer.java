package com.finance.portal.news.infrastructure.messaging.consumer;

import com.finance.portal.news.infrastructure.messaging.event.NewsCacheUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NewsEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NewsEventConsumer.class);

    @KafkaListener(topics = "news-events", groupId = "finance-portal-group")
    public void consumeNewsCacheUpdatedEvent(NewsCacheUpdatedEvent event) {
        logger.info("📨 Kafka Event Received - Type: {}, Timestamp: {}, Item Count: {}, Source: {}",
                event.getEventType(),
                event.getTimestamp(),
                event.getItemCount(),
                event.getSource());
    }
}
