package com.finance.portal.common.infrastructure.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.model.RequestLogEvent;
import com.finance.portal.common.application.logging.port.RequestLogPublisherPort;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class RequestLogKafkaPublisher implements RequestLogPublisherPort {

    private static final Logger log = LogManager.getLogger(RequestLogKafkaPublisher.class);

    @Autowired(required = false)
    @Qualifier("kafkaTemplate")
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.logging.kafka.topic:finance-portal-logs}")
    private String topic;

    @Override
    public void publish(RequestLogEvent event) {
        if (kafkaTemplate == null) {
            log.warn("[RequestLogKafkaPublisher] KafkaTemplate is null — Kafka not configured, skipping log publish");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("[RequestLogKafkaPublisher] Failed to send log to Kafka topic={}: {}",
                                    topic, ex.getMessage());
                        } else {
                            log.debug("[RequestLogKafkaPublisher] Published request log to Kafka topic={} partition={} offset={}",
                                    topic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.warn("[RequestLogKafkaPublisher] Error publishing log event to topic={}: {}", topic, e.getMessage());
        }
    }
}
