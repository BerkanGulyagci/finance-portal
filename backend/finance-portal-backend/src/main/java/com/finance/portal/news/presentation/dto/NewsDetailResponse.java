package com.finance.portal.news.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Haber detay yanıtı: makale + ilgili haberler + haberde tespit edilen varlıklar.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NewsDetailResponse {

    private NewsItemDto article;
    private List<NewsItemDto> related;
    /** Makale tam metni (best-effort); çıkarılamazsa null → frontend özete düşer. */
    private String content;
    /** Haberde geçen ilgili varlıklar (haber→grafik geçişi); yoksa boş liste. */
    private List<RelatedAssetDto> relatedAssets;
}
