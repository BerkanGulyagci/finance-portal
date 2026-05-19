package com.finance.portal.common.presentation.controller;

import com.finance.portal.common.application.proxy.ImageProxyService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/proxy")
public class ImageProxyController {

    private final ImageProxyService imageProxyService;

    public ImageProxyController(ImageProxyService imageProxyService) {
        this.imageProxyService = imageProxyService;
    }

    @GetMapping("/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam String url) {
        ImageProxyService.ImageProxyResult result = imageProxyService.fetchAllowedImage(url);
        if (result instanceof ImageProxyService.ImageProxyResult.Forbidden) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (result instanceof ImageProxyService.ImageProxyResult.NotFound) {
            return ResponseEntity.notFound().build();
        }
        ImageProxyService.ProxiedImage image =
                ((ImageProxyService.ImageProxyResult.Success) result).image();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(image.contentType()));
        headers.setCacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).getHeaderValue());
        return new ResponseEntity<>(image.content(), headers, HttpStatus.OK);
    }
}
