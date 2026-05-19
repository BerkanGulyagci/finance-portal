package com.finance.portal.common.infrastructure.external.proxy;

import com.finance.portal.common.application.proxy.port.ImageFetchPort;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class HttpImageFetchAdapter implements ImageFetchPort {

    private final HttpImageFetchClient httpImageFetchClient;

    public HttpImageFetchAdapter(HttpImageFetchClient httpImageFetchClient) {
        this.httpImageFetchClient = httpImageFetchClient;
    }

    @Override
    public byte[] fetchImage(URI uri) {
        return httpImageFetchClient.fetch(uri);
    }
}
