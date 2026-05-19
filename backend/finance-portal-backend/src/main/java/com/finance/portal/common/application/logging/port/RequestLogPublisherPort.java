package com.finance.portal.common.application.logging.port;

import com.finance.portal.common.application.logging.model.RequestLogEvent;

public interface RequestLogPublisherPort {

    void publish(RequestLogEvent event);
}
