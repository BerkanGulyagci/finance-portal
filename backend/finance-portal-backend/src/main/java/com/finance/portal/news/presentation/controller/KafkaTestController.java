package com.finance.portal.news.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.news.infrastructure.messaging.event.NewsCacheUpdatedEvent;
import com.finance.portal.news.infrastructure.messaging.producer.NewsEventProducer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/kafka")
public class KafkaTestController {

    private final NewsEventProducer newsEventProducer;

    public KafkaTestController(NewsEventProducer newsEventProducer) {
        this.newsEventProducer = newsEventProducer;
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<String>> testKafka() {
        NewsCacheUpdatedEvent testEvent = new NewsCacheUpdatedEvent(
                LocalDateTime.now(),
                42
        );
        
        newsEventProducer.sendNewsCacheUpdatedEvent(testEvent);
        
        return ResponseEntity.ok(
                ApiResponse.success("Kafka test event sent successfully. Check logs for consumer output.")
        );
    }
}
