package com.finance.portal.common.application.logging.port;

import com.finance.portal.common.application.logging.model.ErrorLogEvent;

public interface ErrorLogPublisherPort {

    void publish(ErrorLogEvent event);
}
