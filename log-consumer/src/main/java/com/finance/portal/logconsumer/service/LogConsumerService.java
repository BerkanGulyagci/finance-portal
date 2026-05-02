package com.finance.portal.logconsumer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class LogConsumerService {

    private static final Logger log = LoggerFactory.getLogger(LogConsumerService.class);
    private static final DateTimeFormatter INDEX_DATE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final String INDEX_PREFIX = "finance-portal-logs-";

    private final OpenSearchClient openSearchClient;
    private final ObjectMapper objectMapper;

    @Value("${opensearch.host:opensearch}")
    private String opensearchHost;

    @Value("${opensearch.port:9200}")
    private int opensearchPort;

    public LogConsumerService(OpenSearchClient openSearchClient, ObjectMapper objectMapper) {
        this.openSearchClient = openSearchClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("=== LogConsumerService STARTED === topic=finance-portal-logs groupId=log-consumer-group-v4 opensearch={}:{}", opensearchHost, opensearchPort);
    }

    @KafkaListener(topics = "finance-portal-logs", groupId = "log-consumer-group-v4")
    public void consume(String message) {
        log.info("Received Kafka log message. length={}, payload={}", message.length(), message);
        try {
            Map<String, Object> doc = objectMapper.readValue(
                    message, new TypeReference<Map<String, Object>>() {});

            convertToNumericIfPossible(doc, "status");
            convertToNumericIfPossible(doc, "durationMs");

            String indexDate = extractDateFromTimestamp(doc);
            String indexName = INDEX_PREFIX + indexDate;

            IndexRequest<Map<String, Object>> request = IndexRequest.of(r -> r
                    .index(indexName)
                    .document(doc)
            );

            var response = openSearchClient.index(request);
            log.info("Indexed to OpenSearch: index={} id={} result={}", indexName, response.id(), response.result());

        } catch (Exception e) {
            log.error("Failed to process Kafka message. class={} message={}", e.getClass().getName(), e.getMessage(), e);
        }
    }

    private void convertToNumericIfPossible(Map<String, Object> doc, String key) {
        Object val = doc.get(key);
        if (val instanceof String strVal && !strVal.isBlank()) {
            try {
                doc.put(key, Long.parseLong(strVal.trim()));
            } catch (NumberFormatException ignored) {}
        }
    }

    private String extractDateFromTimestamp(Map<String, Object> doc) {
        try {
            Object ts = doc.get("timestamp");
            if (ts instanceof String tsStr && tsStr.length() >= 10) {
                LocalDate date = LocalDate.parse(tsStr.substring(0, 10));
                return date.format(INDEX_DATE_FMT);
            }
        } catch (Exception e) {
            log.debug("Could not parse timestamp, using today: {}", e.getMessage());
        }
        return LocalDate.now().format(INDEX_DATE_FMT);
    }
}