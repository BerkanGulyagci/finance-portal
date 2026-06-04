package com.finance.portal.common.infrastructure.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.model.BusinessLogEvent;
import com.finance.portal.common.application.logging.model.ErrorLogEvent;
import com.finance.portal.common.application.logging.model.IntegrationLogEvent;
import com.finance.portal.common.application.logging.model.RequestLogEvent;
import com.finance.portal.common.application.logging.port.BusinessLogPublisherPort;
import com.finance.portal.common.application.logging.port.ErrorLogPublisherPort;
import com.finance.portal.common.application.logging.port.IntegrationLogPublisherPort;
import com.finance.portal.common.application.logging.port.RequestLogPublisherPort;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class RequestLogKafkaPublisher implements RequestLogPublisherPort, ErrorLogPublisherPort, BusinessLogPublisherPort, IntegrationLogPublisherPort {

    private static final Logger log = LogManager.getLogger(RequestLogKafkaPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    // Constructor injection (S6813). kafkaTemplate Kafka yapılandırılmamışsa null olabilir
    // (@Nullable — eski @Autowired(required = false) davranışını birebir korur).
    public RequestLogKafkaPublisher(
            @Nullable @Qualifier("kafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.logging.kafka.topic:finance-portal-logs}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(RequestLogEvent event) {
        send(event, "request");
    }

    @Override
    public void publish(ErrorLogEvent event) {
        send(event, "error");
    }

    @Override
    public void publish(BusinessLogEvent event) {
        send(event, "business");
    }

    @Override
    public void publish(IntegrationLogEvent event) {
        send(event, "integration");
    }

    private void send(Object event, String kind) {
        if (kafkaTemplate == null) {
            log.warn("[RequestLogKafkaPublisher] KafkaTemplate is null — Kafka not configured, skipping {} log publish", kind);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("[RequestLogKafkaPublisher] Failed to send {} log to Kafka topic={}: {}",
                                    kind, topic, ex.getMessage());
                        } else {
                            log.debug("[RequestLogKafkaPublisher] Published {} log to Kafka topic={} partition={} offset={}",
                                    kind,
                                    topic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.warn("[RequestLogKafkaPublisher] Error publishing {} log event to topic={}: {}", kind, topic, e.getMessage());
        }
    }
}
