package com.finance.portal.common.application.logging.port;

import com.finance.portal.common.application.logging.model.BusinessLogEvent;

public interface BusinessLogPublisherPort {

    void publish(BusinessLogEvent event);
}
