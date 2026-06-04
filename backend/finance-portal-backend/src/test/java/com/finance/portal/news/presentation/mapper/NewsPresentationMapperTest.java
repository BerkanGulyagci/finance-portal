package com.finance.portal.news.presentation.mapper;

import com.finance.portal.news.application.model.GoldNewsResult;
import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.model.NewsDetail;
import com.finance.portal.news.application.model.NewsPage;
import com.finance.portal.news.application.model.NewsQueryResult;
import com.finance.portal.news.application.model.RelatedAsset;
import com.finance.portal.news.domain.NewsCategory;
import com.finance.portal.news.domain.NewsIdUtil;
import com.finance.portal.news.presentation.dto.NewsDetailResponse;
import com.finance.portal.news.presentation.dto.NewsItemDto;
import com.finance.portal.news.presentation.dto.NewsListResponse;
import com.finance.portal.news.presentation.dto.NewsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NewsPresentationMapper saf dönüşüm testleri: application/domain modeller → presentation DTO.
 */
class NewsPresentationMapperTest {

    private final NewsPresentationMapper mapper = new NewsPresentationMapper();

    private NewsArticle fullArticle() {
        return new NewsArticle(
                "Başlık",
                "Açıklama",
                "https://example.com/haber",
                "https://example.com/img.png",
                "2026-05-31T10:00:00Z",
                "Kaynak",
                "Yazar",
                "STOCKS",
                "tr",
                "tam içerik");
    }

    // ---- toNewsItemDto ----

    @Test
    @DisplayName("toNewsItemDto tüm alanları + türetilmiş id/categoryLabel'i map eder")
    void toNewsItemDto_mapsAllFields() {
        NewsArticle article = fullArticle();

        NewsItemDto dto = mapper.toNewsItemDto(article);

        assertThat(dto.getTitle()).isEqualTo("Başlık");
        assertThat(dto.getDescription()).isEqualTo("Açıklama");
        assertThat(dto.getUrl()).isEqualTo("https://example.com/haber");
        assertThat(dto.getImageUrl()).isEqualTo("https://example.com/img.png");
        assertThat(dto.getPublishedAt()).isEqualTo("2026-05-31T10:00:00Z");
        assertThat(dto.getSource()).isEqualTo("Kaynak");
        assertThat(dto.getAuthor()).isEqualTo("Yazar");
        assertThat(dto.getCategory()).isEqualTo("STOCKS");
        assertThat(dto.getCategoryLabel()).isEqualTo(NewsCategory.STOCKS.getLabel());
        assertThat(dto.getId()).isEqualTo(NewsIdUtil.idFor("https://example.com/haber"));
    }

    @Test
    @DisplayName("toNewsItemDto: bilinmeyen kategori için categoryLabel null")
    void toNewsItemDto_unknownCategory_nullLabel() {
        NewsArticle article = new NewsArticle(
                "t", "d", "https://x.test/a", "img", "p", "s", "a", "GARBAGE", "tr", null);

        NewsItemDto dto = mapper.toNewsItemDto(article);

        assertThat(dto.getCategory()).isEqualTo("GARBAGE");
        assertThat(dto.getCategoryLabel()).isNull();
    }

    @Test
    @DisplayName("toNewsItemDto: null url → id null, null kategori → category/label null")
    void toNewsItemDto_nullUrlAndCategory() {
        NewsArticle article = new NewsArticle(
                "t", "d", null, "img", "p", "s", "a", null, "tr", null);

        NewsItemDto dto = mapper.toNewsItemDto(article);

        assertThat(dto.getId()).isNull();
        assertThat(dto.getCategory()).isNull();
        assertThat(dto.getCategoryLabel()).isNull();
        assertThat(dto.getUrl()).isNull();
    }

    @Test
    @DisplayName("toNewsItemDto: tüm string alanları null olan article")
    void toNewsItemDto_allNullFields() {
        NewsArticle article = new NewsArticle();

        NewsItemDto dto = mapper.toNewsItemDto(article);

        assertThat(dto.getTitle()).isNull();
        assertThat(dto.getDescription()).isNull();
        assertThat(dto.getUrl()).isNull();
        assertThat(dto.getImageUrl()).isNull();
        assertThat(dto.getPublishedAt()).isNull();
        assertThat(dto.getSource()).isNull();
        assertThat(dto.getAuthor()).isNull();
        assertThat(dto.getId()).isNull();
        assertThat(dto.getCategory()).isNull();
        assertThat(dto.getCategoryLabel()).isNull();
    }

    // ---- toNewsItemDtoList ----

    @Test
    @DisplayName("toNewsItemDtoList her elemanı map eder, sırayı korur")
    void toNewsItemDtoList_mapsAllPreservingOrder() {
        NewsArticle a1 = new NewsArticle("A", "d", "https://x.test/1", null, null, null, null, "FX", "tr", null);
        NewsArticle a2 = new NewsArticle("B", "d", "https://x.test/2", null, null, null, null, "CRYPTO", "tr", null);

        List<NewsItemDto> dtos = mapper.toNewsItemDtoList(List.of(a1, a2));

        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getTitle()).isEqualTo("A");
        assertThat(dtos.get(0).getCategoryLabel()).isEqualTo(NewsCategory.FX.getLabel());
        assertThat(dtos.get(1).getTitle()).isEqualTo("B");
        assertThat(dtos.get(1).getCategoryLabel()).isEqualTo(NewsCategory.CRYPTO.getLabel());
    }

    @Test
    @DisplayName("toNewsItemDtoList boş liste → boş liste")
    void toNewsItemDtoList_empty() {
        assertThat(mapper.toNewsItemDtoList(List.of())).isEmpty();
    }

    // ---- toNewsResponse ----

    @Test
    @DisplayName("toNewsResponse sayfalama alanlarını ve item'ları map eder")
    void toNewsResponse_mapsPagingAndItems() {
        NewsPage page = new NewsPage(List.of(fullArticle()), 2, 25, 130, 6);

        NewsResponse response = mapper.toNewsResponse(page);

        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getPageSize()).isEqualTo(25);
        assertThat(response.getTotalElements()).isEqualTo(130);
        assertThat(response.getTotalPages()).isEqualTo(6);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getTitle()).isEqualTo("Başlık");
    }

    @Test
    @DisplayName("toNewsResponse boş sayfa (default constructor) → boş item listesi + default değerler")
    void toNewsResponse_emptyPage() {
        NewsResponse response = mapper.toNewsResponse(new NewsPage());

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
    }

    // ---- toGoldNewsBody ----

    @Test
    @DisplayName("toGoldNewsBody items/isFiltered/label anahtarlarını map eder")
    void toGoldNewsBody_mapsKeys() {
        GoldNewsResult result = new GoldNewsResult(List.of(fullArticle()), true, "Altın Haberleri");

        Map<String, Object> body = mapper.toGoldNewsBody(result);

        assertThat(body).containsKeys("items", "isFiltered", "label");
        assertThat(body.get("isFiltered")).isEqualTo(true);
        assertThat(body.get("label")).isEqualTo("Altın Haberleri");
        @SuppressWarnings("unchecked")
        List<NewsItemDto> items = (List<NewsItemDto>) body.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getTitle()).isEqualTo("Başlık");
    }

    @Test
    @DisplayName("toGoldNewsBody boş sonuç (default) → boş items, isFiltered false")
    void toGoldNewsBody_default() {
        Map<String, Object> body = mapper.toGoldNewsBody(new GoldNewsResult());

        assertThat(body.get("isFiltered")).isEqualTo(false);
        assertThat(body.get("label")).isEqualTo("Son Haberler");
        @SuppressWarnings("unchecked")
        List<NewsItemDto> items = (List<NewsItemDto>) body.get("items");
        assertThat(items).isEmpty();
    }

    // ---- toListResponse ----

    @Test
    @DisplayName("toListResponse: items, sayfalama, kaynaklar ve yalnızca count>0 facet'leri enum sırasıyla map eder")
    void toListResponse_mapsFacetsInEnumOrder() {
        // STOCKS=3, ECONOMY=5, WORLD=0 (atlanır), GARBAGE=anahtar enum'da yok (atlanır)
        Map<String, Long> counts = Map.of(
                "STOCKS", 3L,
                "ECONOMY", 5L,
                "WORLD", 0L,
                "GARBAGE", 9L);
        NewsQueryResult result = new NewsQueryResult(
                List.of(fullArticle()),
                1, 20, 42L, 3,
                List.of("Kaynak A", "Kaynak B"),
                counts);

        NewsListResponse response = mapper.toListResponse(result);

        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isEqualTo(42L);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.getSources()).containsExactly("Kaynak A", "Kaynak B");
        assertThat(response.getItems()).hasSize(1);

        // Enum sırası: ECONOMY enum'da STOCKS'tan önce gelir; WORLD/GARBAGE elenir
        assertThat(response.getCategories()).hasSize(2);
        NewsListResponse.CategoryFacet first = response.getCategories().get(0);
        NewsListResponse.CategoryFacet second = response.getCategories().get(1);
        assertThat(first.getKey()).isEqualTo("ECONOMY");
        assertThat(first.getLabel()).isEqualTo(NewsCategory.ECONOMY.getLabel());
        assertThat(first.getCount()).isEqualTo(5L);
        assertThat(second.getKey()).isEqualTo("STOCKS");
        assertThat(second.getLabel()).isEqualTo(NewsCategory.STOCKS.getLabel());
        assertThat(second.getCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("toListResponse: boş count map → facet listesi boş")
    void toListResponse_noFacets() {
        NewsQueryResult result = new NewsQueryResult(
                List.of(), 1, 10, 0L, 0, List.of(), Map.of());

        NewsListResponse response = mapper.toListResponse(result);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getCategories()).isEmpty();
        assertThat(response.getSources()).isEmpty();
    }

    // ---- toDetailResponse ----

    @Test
    @DisplayName("toDetailResponse article + related + content'i map eder")
    void toDetailResponse_mapsAll() {
        NewsArticle main = fullArticle();
        NewsArticle related = new NewsArticle(
                "İlgili", "d", "https://x.test/rel", null, null, null, null, "FX", "tr", null);
        NewsDetail detail = new NewsDetail(main, List.of(related), "<p>tam metin</p>",
                List.of(new RelatedAsset("THYAO.IS", "Türk Hava Yolları", "STOCK")));

        NewsDetailResponse response = mapper.toDetailResponse(detail);

        assertThat(response.getArticle().getTitle()).isEqualTo("Başlık");
        assertThat(response.getArticle().getId())
                .isEqualTo(NewsIdUtil.idFor("https://example.com/haber"));
        assertThat(response.getRelated()).hasSize(1);
        assertThat(response.getRelated().get(0).getTitle()).isEqualTo("İlgili");
        assertThat(response.getContent()).isEqualTo("<p>tam metin</p>");
        assertThat(response.getRelatedAssets()).hasSize(1);
        assertThat(response.getRelatedAssets().get(0).getSymbol()).isEqualTo("THYAO.IS");
        assertThat(response.getRelatedAssets().get(0).getType()).isEqualTo("STOCK");
    }

    @Test
    @DisplayName("toDetailResponse: boş related + null content")
    void toDetailResponse_emptyRelatedNullContent() {
        NewsDetail detail = new NewsDetail(fullArticle(), List.of(), null, List.of());

        NewsDetailResponse response = mapper.toDetailResponse(detail);

        assertThat(response.getArticle()).isNotNull();
        assertThat(response.getRelated()).isEmpty();
        assertThat(response.getContent()).isNull();
    }
}
