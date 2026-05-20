package com.finance.portal.common.application.logging.port;

import com.finance.portal.common.application.logging.model.IntegrationLogEvent;

public interface IntegrationLogPublisherPort {

    void publish(IntegrationLogEvent event);
}
