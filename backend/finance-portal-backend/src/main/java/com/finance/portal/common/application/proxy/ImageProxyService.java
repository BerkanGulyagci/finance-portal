package com.finance.portal.common.application.proxy;

import com.finance.portal.common.application.proxy.port.ImageFetchPort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Set;

@Service
public class ImageProxyService {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "geoim.bloomberght.com",
            "images.unsplash.com",
            "newsapi.org",
            "webcdn.getmidas.com"
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
            return new ImageProxyResult.Success(new ProxiedImage(bytes, MediaType.IMAGE_JPEG_VALUE));
        } catch (Exception e) {
            return ImageProxyResult.NotFound.INSTANCE;
        }
    }

    public record ProxiedImage(byte[] content, String contentType) {
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
