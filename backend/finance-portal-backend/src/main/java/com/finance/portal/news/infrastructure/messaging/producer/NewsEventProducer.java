package com.finance.portal.news.infrastructure.messaging.producer;

import com.finance.portal.news.infrastructure.messaging.event.NewsCacheUpdatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class NewsEventProducer {

    private static final String TOPIC_NEWS_EVENTS = "news-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NewsEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendNewsCacheUpdatedEvent(NewsCacheUpdatedEvent event) {
        kafkaTemplate.send(TOPIC_NEWS_EVENTS, event);
        kafkaTemplate.flush();
    }
}
