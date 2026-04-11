package com.finance.portal.common.presentation.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Set;

@RestController
@RequestMapping("/api/proxy")
public class ImageProxyController {

    private static final Logger log = LoggerFactory.getLogger(ImageProxyController.class);

    // Only allow images from trusted domains
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "geoim.bloomberght.com",
            "images.unsplash.com",
            "newsapi.org"
    );

    private final RestTemplate restTemplate;

    public ImageProxyController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();

            if (host == null || ALLOWED_HOSTS.stream().noneMatch(host::endsWith)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            byte[] imageBytes = restTemplate.getForObject(uri, byte[].class);
            if (imageBytes == null) return ResponseEntity.notFound().build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.setCacheControl("public, max-age=3600");

            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.debug("Image proxy failed for url {}: {}", url, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
