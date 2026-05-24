package com.finance.portal.common.presentation.exception;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.logging.CentralErrorLogService;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.common.application.logging.port.IntegrationLogPublisherPort;
import com.finance.portal.common.application.logging.model.IntegrationLogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerIntegrationLogTest {

    @Mock
    private CentralErrorLogService centralErrorLogService;

    @Mock
    private IntegrationLogPublisherPort integrationLogPublisher;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(
                centralErrorLogService,
                new CentralIntegrationLogService(integrationLogPublisher, null));
    }

    @Test
    void handleExternalApiException_publishesIntegrationFailedNotErrorCategory() {
        ExternalApiException ex = new ExternalApiException("Yahoo Finance API returned empty chart result");

        ResponseEntity<?> response = handler.handleExternalApiException(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());

        ArgumentCaptor<IntegrationLogEvent> captor = ArgumentCaptor.forClass(IntegrationLogEvent.class);
        verify(integrationLogPublisher).publish(captor.capture());
        IntegrationLogEvent event = captor.getValue();

        assertEquals(IntegrationLogSupport.CATEGORY, event.getCategory());
        assertEquals(IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED, event.getEventType());
        assertEquals("ERROR", event.getLevel());
        assertEquals(IntegrationLogSupport.PROVIDER_YAHOO, event.getProvider());
        assertEquals("503", event.getHttpStatus());
        verifyNoInteractions(centralErrorLogService);
    }
}
