package com.finance.portal.common.application.logging;

import com.finance.portal.common.application.logging.model.BusinessLogEvent;
import com.finance.portal.common.application.logging.port.BusinessLogPublisherPort;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class CentralBusinessLogService {

    private static final Logger log = LogManager.getLogger(CentralBusinessLogService.class);
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final BusinessLogPublisherPort businessLogPublisher;

    public CentralBusinessLogService(@Autowired(required = false) BusinessLogPublisherPort businessLogPublisher) {
        this.businessLogPublisher = businessLogPublisher;
    }

    public void publish(String category,
                        String eventType,
                        String level,
                        String message,
                        String entityType,
                        String entityId,
                        String action,
                        String result,
                        Map<String, Object> metadata,
                        String actorUserId,
                        String loggerName) {
        logToConsole(level, message);
        if (businessLogPublisher == null) {
            return;
        }
        try {
            ErrorLogContext ctx = ErrorLogContext.fromCurrentRequest();
            String userId = actorUserId != null && !actorUserId.isBlank() ? actorUserId : ctx.userId();

            BusinessLogEvent event = BusinessLogEvent.builder()
                    .timestamp(ISO_FMT.format(Instant.now()))
                    .level(level)
                    .serviceName(BusinessLogSupport.SERVICE_NAME)
                    .category(category)
                    .eventType(eventType)
                    .message(message)
                    .traceId(ctx.traceId())
                    .spanId(ctx.spanId())
                    .requestId(ctx.requestId())
                    .userId(userId)
                    .clientIp(ctx.clientIp())
                    .method(ctx.method())
                    .path(ctx.path())
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .result(result)
                    .metadata(BusinessLogMetadataSanitizer.sanitize(metadata))
                    .logger(loggerName)
                    .build();
            businessLogPublisher.publish(event);
        } catch (Exception e) {
            log.warn("[CentralBusinessLogService] Failed to publish business log: {}", e.getMessage());
        }
    }

    private void logToConsole(String level, String message) {
        switch (level) {
            case "WARN" -> log.warn(message);
            case "ERROR" -> log.error(message);
            default -> log.info(message);
        }
    }
}
