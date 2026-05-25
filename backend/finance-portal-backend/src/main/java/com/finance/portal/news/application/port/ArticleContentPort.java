package com.finance.portal.news.application.port;

/**
 * Haber sayfasından tam metin ve og:image çıkarımı (best-effort; başarısızlıkta null).
 */
public interface ArticleContentPort {

    /** Makalenin tam metni ya da çıkarılamazsa null. */
    String fetchContent(String url);

    /** Sayfanın og:image URL'si ya da null. */
    String fetchOgImage(String url);

    /**
     * RSS {@code <content:encoded>} ham HTML'ini paragraflara (\n\n ile ayrık) çevirir.
     * Sayfayı indirmeden çalışır; içerik çıkarılamazsa null döner.
     */
    String extractFromHtml(String html);
}
