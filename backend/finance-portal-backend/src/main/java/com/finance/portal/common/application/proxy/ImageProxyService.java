package com.finance.portal.common.application.proxy;

import com.finance.portal.common.application.proxy.port.ImageFetchPort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Service
public class ImageProxyService {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "geoim.bloomberght.com",
            "images.unsplash.com",
            "newsapi.org",
            "webcdn.getmidas.com",
            "s3-symbol-logo.tradingview.com"
    );

    private final ImageFetchPort imageFetchPort;

    public ImageProxyService(ImageFetchPort imageFetchPort) {
        this.imageFetchPort = imageFetchPort;
    }

    public ImageProxyResult fetchAllowedImage(String url) {
        if (url == null || url.isBlank()) {
            return ImageProxyResult.NotFound.INSTANCE;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null || ALLOWED_HOSTS.stream().noneMatch(host::endsWith)) {
                return ImageProxyResult.Forbidden.INSTANCE;
            }
            byte[] bytes = imageFetchPort.fetchImage(uri);
            if (bytes == null || bytes.length == 0) {
                return ImageProxyResult.NotFound.INSTANCE;
            }
            return new ImageProxyResult.Success(new ProxiedImage(bytes, contentTypeFor(uri, bytes)));
        } catch (Exception e) {
            return ImageProxyResult.NotFound.INSTANCE;
        }
    }

    /** İçerik türü: önce URL uzantısına bak (SVG/PNG/WEBP/GIF/JPEG), olmazsa SVG metnini sez, yoksa JPEG. */
    private static String contentTypeFor(URI uri, byte[] bytes) {
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".gif")) return MediaType.IMAGE_GIF_VALUE;
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_VALUE;
        if (bytes.length > 4) {
            String head = new String(bytes, 0, Math.min(bytes.length, 64), StandardCharsets.UTF_8).trim().toLowerCase();
            if (head.startsWith("<?xml") || head.startsWith("<svg")) return "image/svg+xml";
        }
        return MediaType.IMAGE_JPEG_VALUE;
    }

    // Record'un default equals/hashCode'u array referansı karşılaştırır — Sonar S6218.
    // Override ile array içeriğini hesaba kat: aynı byte'lar + aynı contentType ise eşit.
    public record ProxiedImage(byte[] content, String contentType) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            // Java 21 record pattern — Sonar S6878
            if (!(o instanceof ProxiedImage(byte[] otherContent, String otherContentType))) {
                return false;
            }
            return java.util.Arrays.equals(content, otherContent)
                    && java.util.Objects.equals(contentType, otherContentType);
        }

        @Override
        public int hashCode() {
            return 31 * java.util.Arrays.hashCode(content)
                    + java.util.Objects.hashCode(contentType);
        }

        @Override
        public String toString() {
            return "ProxiedImage[contentType=" + contentType
                    + ", content.length=" + (content != null ? content.length : 0) + "]";
        }
    }

    public sealed interface ImageProxyResult permits ImageProxyResult.Success, ImageProxyResult.Forbidden, ImageProxyResult.NotFound {

        record Success(ProxiedImage image) implements ImageProxyResult {
        }

        enum Forbidden implements ImageProxyResult {
            INSTANCE
        }

        enum NotFound implements ImageProxyResult {
            INSTANCE
        }
    }
}
