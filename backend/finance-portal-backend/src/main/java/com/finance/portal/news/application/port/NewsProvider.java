package com.finance.portal.news.application.port;

import com.finance.portal.news.application.model.NewsArticle;

import java.util.List;

/**
 * Tek bir haber kaynağı (RSS feed grubu veya bir API). Aggregator tüm etkin
 * sağlayıcılardan toplar. Hatalar yutulur (best-effort): başarısızlıkta boş liste döner.
 */
public interface NewsProvider {

    /** Teknik kimlik (log/metrik için), ör. "rss", "finnhub". */
    String id();

    /** Anahtar yoksa API sağlayıcıları devre dışıdır; RSS her zaman etkindir. */
    boolean isEnabled();

    /** Bu kaynaktan haberleri çeker (source + varsayılan kategori atanmış olarak). */
    List<NewsArticle> fetch();
}
