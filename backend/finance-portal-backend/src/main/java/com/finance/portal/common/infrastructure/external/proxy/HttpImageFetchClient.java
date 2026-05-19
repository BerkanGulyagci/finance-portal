package com.finance.portal.common.infrastructure.external.proxy;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Component
public class HttpImageFetchClient {

    private final RestTemplate restTemplate;

    public HttpImageFetchClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public byte[] fetch(URI uri) {
        return restTemplate.getForObject(uri, byte[].class);
    }
}
