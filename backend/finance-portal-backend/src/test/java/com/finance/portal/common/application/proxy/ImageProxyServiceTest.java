package com.finance.portal.common.application.proxy;

import com.finance.portal.common.application.proxy.port.ImageFetchPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageProxyServiceTest {

    @Mock
    private ImageFetchPort imageFetchPort;

    @InjectMocks
    private ImageProxyService service;

    // ── Null / blank input → NotFound (no fetch) ───────────────────────────────

    @Test
    void nullUrl_returnsNotFound_withoutFetching() {
        ImageProxyService.ImageProxyResult result = service.fetchAllowedImage(null);

        assertThat(result).isEqualTo(ImageProxyService.ImageProxyResult.NotFound.INSTANCE);
        verify(imageFetchPort, never()).fetchImage(any());
    }

    @Test
    void blankUrl_returnsNotFound_withoutFetching() {
        ImageProxyService.ImageProxyResult result = service.fetchAllowedImage("   ");

        assertThat(result).isEqualTo(ImageProxyService.ImageProxyResult.NotFound.INSTANCE);
        verify(imageFetchPort, never()).fetchImage(any());
    }

    // ── Allowlist branches ─────────────────────────────────────────────────────

    @Test
    void disallowedHost_returnsForbidden_withoutFetching() {
        ImageProxyService.ImageProxyResult result =
                service.fetchAllowedImage("https://evil.example.com/a.png");

        assertThat(result).isEqualTo(ImageProxyService.ImageProxyResult.Forbidden.INSTANCE);
        verify(imageFetchPort, never()).fetchImage(any());
    }

    @Test
    void noHost_returnsForbidden() {
        // Relative URI -> getHost() == null
        ImageProxyService.ImageProxyResult result = service.fetchAllowedImage("/just/a/path.png");

        assertThat(result).isEqualTo(ImageProxyService.ImageProxyResult.Forbidden.INSTANCE);
        verify(imageFetchPort, never()).fetchImage(any());
    }

    @Test
    void allowedHost_subdomainSuffixMatch_isAccepted() {
        // endsWith match: "cdn.images.unsplash.com" ends with allowed "images.unsplash.com"
        byte[] payload = "binarydata".getBytes(StandardCharsets.UTF_8);
        when(imageFetchPort.fetchImage(any(URI.class))).thenReturn(payload);

        ImageProxyService.ImageProxyResult result =
                service.fetchAllowedImage("https://cdn.images.unsplash.com/photo.jpg");

        assertThat(result).isInstanceOf(ImageProxyService.ImageProxyResult.Success.class);
    }

    // ── Success + content-type resolution ──────────────────────────────────────

    @Test
    void allowedHostPng_returnsSuccessWithPngContentType() {
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        when(imageFetchPort.fetchImage(any(URI.class))).thenReturn(payload);

        ImageProxyService.ImageProxyResult result =
                service.fetchAllowedImage("https://s3-symbol-logo.tradingview.com/logo.png");

        assertThat(result).isInstanceOf(ImageProxyService.ImageProxyResult.Success.class);
        ImageProxyService.ProxiedImage img =
                ((ImageProxyService.ImageProxyResult.Success) result).image();
        assertThat(img.contentType()).isEqualTo("image/png");
        assertThat(img.content()).isEqualTo(payload);
    }

    @Test
    void svgExtension_yieldsSvgContentType() {
        byte[] payload = "<svg></svg>".getBytes(StandardCharsets.UTF_8);
        when(imageFetchPort.fetchImage(any(URI.class))).thenReturn(payload);

        ImageProxyService.ImageProxyResult result =
                service.fetchAllowedImage("https://webcdn.getmidas.com/icon.svg");

        ImageProxyService.ProxiedImage img =
                ((ImageProxyService.ImageProxyResult.Success) result).image();
        assertThat(img.contentType()).isEqualTo("image/svg+xml");
    }

    @Test
    void noExtensionButSvgContent_isSniffedAsSvg() {
        byte[] payload = "<svg xmlns=\"x\"></svg>".getBytes(StandardCharsets.UTF_8);
        when(imageFetchPort.fetchImage(any(URI.class))).thenReturn(payload);

        ImageProxyService.ImageProxyResult result =
                service.fetchAllowedImage("https://newsapi.org/image");

        ImageProxyService.ProxiedImage img =
                ((ImageProxyService.ImageProxyResult.Success) result).image();
        assertThat(img.contentType()).isEqualTo("image/svg+xml");
    }

    @Test
    void unknownExtensionNonSvg_defaultsToJpeg() {
        byte[] payload = new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
        when(imageFetchPort.fetchImage(any(URI.class))).thenReturn(payload);

        ImageProxyService.ImageProxyResult result =
                service.fetchAllowedImage("https://geoim.bloomberght.com/pic");

        ImageProxyService.ProxiedImage img =
                ((ImageProxyService.ImageProxyResult.Success) result).image();
        assertThat(img.contentType()).isEqualTo("image/jpeg");
    }

    // ── Empty / null bytes → NotFound ──────────────────────────────────────────

    @Test
    void allowedHostButEmptyBytes_returnsNotFound() {
        when(imageFetchPort.fetchImage(any(URI.class))).thenReturn(new byte[0]);

        ImageProxyService.ImageProxyResult result =
                service.fetchAllowedImage("https://images.unsplash.com/photo.jpg");

        assertThat(result).isEqualTo(ImageProxyService.ImageProxyResult.NotFound.INSTANCE);
    }

    @Test
    void allowedHostButNullBytes_returnsNotFound() {
        when(imageFetchPort.fetchImage(any(URI.class))).thenReturn(null);

        ImageProxyService.ImageProxyResult result =
                service.fetchAllowedImage("https://images.unsplash.com/photo.jpg");

        assertThat(result).isEqualTo(ImageProxyService.ImageProxyResult.NotFound.INSTANCE);
    }

    // ── Exception fallback → NotFound ──────────────────────────────────────────

    @Test
    void fetchThrows_returnsNotFound() {
        when(imageFetchPort.fetchImage(any(URI.class)))
                .thenThrow(new RuntimeException("network down"));

        ImageProxyService.ImageProxyResult result =
                service.fetchAllowedImage("https://images.unsplash.com/photo.jpg");

        assertThat(result).isEqualTo(ImageProxyService.ImageProxyResult.NotFound.INSTANCE);
    }

    @Test
    void malformedUrl_returnsNotFound() {
        // URI.create throws IllegalArgumentException -> caught -> NotFound
        ImageProxyService.ImageProxyResult result =
                service.fetchAllowedImage("ht tp://bad url with spaces");

        assertThat(result).isEqualTo(ImageProxyService.ImageProxyResult.NotFound.INSTANCE);
        verify(imageFetchPort, never()).fetchImage(any());
    }
}
