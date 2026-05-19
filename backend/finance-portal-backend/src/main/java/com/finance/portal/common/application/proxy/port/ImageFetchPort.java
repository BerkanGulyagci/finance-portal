package com.finance.portal.common.application.proxy.port;

import java.net.URI;

public interface ImageFetchPort {

    byte[] fetchImage(URI uri);
}
