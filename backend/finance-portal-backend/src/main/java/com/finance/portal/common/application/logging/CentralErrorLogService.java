package com.finance.portal.common.application.logging;

import com.finance.portal.common.application.logging.model.ErrorLogEvent;
import com.finance.portal.common.application.logging.port.ErrorLogPublisherPort;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class CentralErrorLogService {

    private static final Logger log = LogManager.getLogger(CentralErrorLogService.class);
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final ErrorLogPublisherPort errorLogPublisher;

    public CentralErrorLogService(@Autowired(required = false) ErrorLogPublisherPort errorLogPublisher) {
        this.errorLogPublisher = errorLogPublisher;
    }

    public void logHandledError(HttpStatus status, String eventType, String message, Throwable ex) {
        logHandledError(status, eventType, message, ex, CentralErrorLogService.class.getName());
    }

    public void logHandledError(HttpStatus status, String eventType, String message, Throwable ex, String loggerName) {
        String level = ErrorLogSupport.resolveLevel(status);
        logToConsole(level, message, ex);
        publishEvent(status, eventType, level, message, RequestLogSupport.formatExceptionSummary(ex), loggerName);
    }

    public void logHandledError(HttpStatus status, String eventType, String message,
                                String exceptionSummary, String loggerName) {
        String level = ErrorLogSupport.resolveLevel(status);
        logToConsole(level, message, null);
        publishEvent(status, eventType, level, message, exceptionSummary, loggerName);
    }

    private void logToConsole(String level, String message, Throwable ex) {
        switch (level) {
            case "ERROR" -> {
                if (ex != null) {
                    log.error(message, ex);
                } else {
                    log.error(message);
                }
            }
            case "WARN" -> {
                if (ex != null) {
                    log.warn("{}: {}", message, ex.getMessage());
                } else {
                    log.warn(message);
                }
            }
            default -> log.info(message);
        }
    }

    private void publishEvent(HttpStatus status, String eventType, String level,
                              String message, String exceptionSummary, String loggerName) {
        if (errorLogPublisher == null) {
            return;
        }
        try {
            ErrorLogContext ctx = ErrorLogContext.fromCurrentRequest();
            ErrorLogEvent event = ErrorLogEvent.builder()
                    .timestamp(ISO_FMT.format(Instant.now()))
                    .level(level)
                    .serviceName(ErrorLogSupport.SERVICE_NAME)
                    .category(ErrorLogSupport.CATEGORY)
                    .eventType(eventType)
                    .message(message)
                    .exception(exceptionSummary)
                    .requestId(ctx.requestId())
                    .traceId(ctx.traceId())
                    .spanId(ctx.spanId())
                    .userId(ctx.userId())
                    .clientIp(ctx.clientIp())
                    .method(ctx.method())
                    .path(ctx.path())
                    .status(String.valueOf(status.value()))
                    .logger(loggerName)
                    .build();
            errorLogPublisher.publish(event);
        } catch (Exception e) {
            log.warn("[CentralErrorLogService] Failed to publish error log: {}", e.getMessage());
        }
    }
}
