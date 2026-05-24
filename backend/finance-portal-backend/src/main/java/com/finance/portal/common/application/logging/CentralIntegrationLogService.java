package com.finance.portal.common.application.logging;

import com.finance.portal.common.application.logging.model.IntegrationLogEvent;
import com.finance.portal.common.application.logging.port.IntegrationLogPublisherPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CentralIntegrationLogService {

    private static final Logger log = LogManager.getLogger(CentralIntegrationLogService.class);
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final IntegrationLogPublisherPort integrationLogPublisher;
    private final MeterRegistry meterRegistry;

    public CentralIntegrationLogService(@Autowired(required = false) IntegrationLogPublisherPort integrationLogPublisher,
                                        @Autowired(required = false) MeterRegistry meterRegistry) {
        this.integrationLogPublisher = integrationLogPublisher;
        this.meterRegistry = meterRegistry;
    }

    private static String nv(String s) {
        return s == null || s.isBlank() ? "unknown" : s;
    }

    public void publish(String eventType,
                        String level,
                        String message,
                        String provider,
                        String operation,
                        String httpStatus,
                        Long durationMs,
                        Boolean fallbackUsed,
                        Boolean degraded,
                        Map<String, Object> metadata,
                        String loggerName) {
        logToConsole(level, message);
        if (meterRegistry != null) {
            meterRegistry.counter("fp.integration.events", "provider", nv(provider), "event", nv(eventType)).increment();
            if (durationMs != null) {
                meterRegistry.timer("fp.integration.duration", "provider", nv(provider), "event", nv(eventType))
                        .record(durationMs, TimeUnit.MILLISECONDS);
            }
        }
        if (integrationLogPublisher == null) {
            return;
        }
        try {
            ErrorLogContext ctx = ErrorLogContext.fromCurrentRequest();
            IntegrationLogEvent event = IntegrationLogEvent.builder()
                    .timestamp(ISO_FMT.format(Instant.now()))
                    .level(level)
                    .serviceName(IntegrationLogSupport.SERVICE_NAME)
                    .category(IntegrationLogSupport.CATEGORY)
                    .eventType(eventType)
                    .message(message)
                    .traceId(ctx.traceId())
                    .spanId(ctx.spanId())
                    .requestId(ctx.requestId())
                    .userId(ctx.userId())
                    .clientIp(ctx.clientIp())
                    .method(ctx.method())
                    .path(ctx.path())
                    .provider(provider)
                    .operation(operation)
                    .httpStatus(httpStatus)
                    .durationMs(durationMs)
                    .fallbackUsed(fallbackUsed)
                    .degraded(degraded)
                    .metadata(IntegrationLogMetadataSanitizer.sanitize(metadata))
                    .logger(loggerName)
                    .build();
            integrationLogPublisher.publish(event);
        } catch (Exception e) {
            log.warn("[CentralIntegrationLogService] Failed to publish integration log: {}", e.getMessage());
        }
    }

    public static String inferProviderFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return IntegrationLogSupport.PROVIDER_EXTERNAL;
        }
        String lower = message.toLowerCase();
        if (lower.contains("yahoo")) {
            return IntegrationLogSupport.PROVIDER_YAHOO;
        }
        if (lower.contains("coingecko") || lower.contains("crypto market")) {
            return IntegrationLogSupport.PROVIDER_COINGECKO;
        }
        if (lower.contains("news")) {
            return IntegrationLogSupport.PROVIDER_NEWSAPI;
        }
        if (lower.contains("rasyonet")) {
            return IntegrationLogSupport.PROVIDER_RASYONET;
        }
        return IntegrationLogSupport.PROVIDER_EXTERNAL;
    }

    private void logToConsole(String level, String message) {
        switch (level) {
            case "WARN" -> log.warn(message);
            case "ERROR" -> log.error(message);
            default -> log.debug(message);
        }
    }
}
