package com.finance.portal.news.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NewsHtmlUtil saf yardımcı sınıf testleri: etiket sıyırma, varlık çözme,
 * çift-encode, boşluk sıkıştırma ve maxLen kırpma davranışları.
 */
class NewsHtmlUtilTest {

    // =========================================================================
    // stripToText
    // =========================================================================

    @Test
    @DisplayName("stripToText: null girdi → null")
    void stripToText_null_returnsNull() {
        assertThat(NewsHtmlUtil.stripToText(null, 0)).isNull();
    }

    @Test
    @DisplayName("stripToText: boş string → null")
    void stripToText_empty_returnsNull() {
        assertThat(NewsHtmlUtil.stripToText("", 0)).isNull();
    }

    @Test
    @DisplayName("stripToText: yalnız etiket/boşluk → temizlenince boş → null")
    void stripToText_onlyTagsAndWhitespace_returnsNull() {
        assertThat(NewsHtmlUtil.stripToText("<p>   </p>", 0)).isNull();
        assertThat(NewsHtmlUtil.stripToText("   ", 0)).isNull();
    }

    @Test
    @DisplayName("stripToText: etiketleri sıyırır ve boşlukları sıkıştırır")
    void stripToText_stripsTagsAndCollapsesWhitespace() {
        String html = "<p>Merhaba   <b>dünya</b>\n\t veri</p>";
        assertThat(NewsHtmlUtil.stripToText(html, 0)).isEqualTo("Merhaba dünya veri");
    }

    @Test
    @DisplayName("stripToText: varlıkları çözer")
    void stripToText_decodesEntities() {
        assertThat(NewsHtmlUtil.stripToText("Ali &amp; Veli", 0)).isEqualTo("Ali & Veli");
    }

    @Test
    @DisplayName("stripToText: maxLen ≤ 0 → kırpma yok")
    void stripToText_noTrimWhenMaxLenNonPositive() {
        String text = "abcdefghij";
        assertThat(NewsHtmlUtil.stripToText(text, 0)).isEqualTo(text);
        assertThat(NewsHtmlUtil.stripToText(text, -5)).isEqualTo(text);
    }

    @Test
    @DisplayName("stripToText: metin maxLen'den uzunsa kırpılır + ellipsis eklenir")
    void stripToText_trimsAndAddsEllipsis() {
        String text = "abcdefghij";
        // 5 karakter + ellipsis
        assertThat(NewsHtmlUtil.stripToText(text, 5)).isEqualTo("abcde…");
    }

    @Test
    @DisplayName("stripToText: metin maxLen'e eşit/kısa ise kırpılmaz")
    void stripToText_noTrimWhenWithinMaxLen() {
        assertThat(NewsHtmlUtil.stripToText("abcde", 5)).isEqualTo("abcde");
        assertThat(NewsHtmlUtil.stripToText("abc", 5)).isEqualTo("abc");
    }

    @Test
    @DisplayName("stripToText: soft-hyphen (U+00AD) karakterini kaldırır")
    void stripToText_removesSoftHyphen() {
        // İçinde soft-hyphen olan "a­b" → "ab"
        String html = "<span>a­b</span>";
        assertThat(NewsHtmlUtil.stripToText(html, 0)).isEqualTo("ab");
    }

    @Test
    @DisplayName("stripToText: kırpmadan önce trim uygulanır (sondaki boşluk ellipsis öncesi silinir)")
    void stripToText_trimsBeforeEllipsis() {
        // "ab cd ef" → maxLen 3 → substring "ab " → trim → "ab" + …
        assertThat(NewsHtmlUtil.stripToText("ab cd ef", 3)).isEqualTo("ab…");
    }

    // =========================================================================
    // decodeEntities
    // =========================================================================

    @Test
    @DisplayName("decodeEntities: null → boş string")
    void decodeEntities_null_returnsEmpty() {
        assertThat(NewsHtmlUtil.decodeEntities(null)).isEmpty();
    }

    @Test
    @DisplayName("decodeEntities: '&' yoksa girdiyi olduğu gibi döner")
    void decodeEntities_noAmpersand_returnsSame() {
        assertThat(NewsHtmlUtil.decodeEntities("plain text")).isEqualTo("plain text");
        assertThat(NewsHtmlUtil.decodeEntities("")).isEmpty();
    }

    @Test
    @DisplayName("decodeEntities: isimli varlıkları çözer")
    void decodeEntities_namedEntities() {
        assertThat(NewsHtmlUtil.decodeEntities("a&amp;b")).isEqualTo("a&b");
        assertThat(NewsHtmlUtil.decodeEntities("&lt;tag&gt;")).isEqualTo("<tag>");
        assertThat(NewsHtmlUtil.decodeEntities("&quot;x&quot;")).isEqualTo("\"x\"");
        assertThat(NewsHtmlUtil.decodeEntities("&ccedil;&uuml;&ouml;")).isEqualTo("çüö");
        assertThat(NewsHtmlUtil.decodeEntities("a&nbsp;b")).isEqualTo("a b");
    }

    @Test
    @DisplayName("decodeEntities: shy varlığı boşa çözülür")
    void decodeEntities_shyToEmpty() {
        assertThat(NewsHtmlUtil.decodeEntities("a&shy;b")).isEqualTo("ab");
    }

    @Test
    @DisplayName("decodeEntities: bilinmeyen isimli varlık değişmeden kalır")
    void decodeEntities_unknownNamedEntity_unchanged() {
        assertThat(NewsHtmlUtil.decodeEntities("a&foobar;b")).isEqualTo("a&foobar;b");
    }

    @Test
    @DisplayName("decodeEntities: ondalık sayısal varlık")
    void decodeEntities_decimalNumeric() {
        // &#65; = 'A'
        assertThat(NewsHtmlUtil.decodeEntities("&#65;&#66;&#67;")).isEqualTo("ABC");
    }

    @Test
    @DisplayName("decodeEntities: onaltılık sayısal varlık (küçük x)")
    void decodeEntities_hexNumeric() {
        // &#x41; = 'A' — NUM_ENTITY deseni yalnız küçük 'x' kabul eder
        assertThat(NewsHtmlUtil.decodeEntities("&#x41;")).isEqualTo("A");
        assertThat(NewsHtmlUtil.decodeEntities("&#x41;&#x42;")).isEqualTo("AB");
    }

    @Test
    @DisplayName("decodeEntities: büyük 'X' onaltılık önek desende eşleşmez → ham kalır")
    void decodeEntities_uppercaseXHex_notMatched() {
        // Regex 'x?' yalnız küçük x; 'X42' hex haneleri değil → eşleşme yok, değişmeden kalır
        assertThat(NewsHtmlUtil.decodeEntities("&#X42;")).isEqualTo("&#X42;");
    }

    @Test
    @DisplayName("decodeEntities: BMP dışı kod noktası (emoji, surrogate pair)")
    void decodeEntities_supplementaryCodePoint() {
        // &#128512; = 😀
        assertThat(NewsHtmlUtil.decodeEntities("&#128512;")).isEqualTo("😀");
    }

    @Test
    @DisplayName("decodeEntities: geçersiz kod noktası → orijinal eşleşme korunur")
    void decodeEntities_invalidCodePoint_keepsOriginal() {
        // Character.toChars geçersiz cp için IllegalArgumentException → catch → group(0)
        String tooBig = "&#x110000;"; // > 0x10FFFF
        assertThat(NewsHtmlUtil.decodeEntities(tooBig)).isEqualTo(tooBig);
    }

    @Test
    @DisplayName("decodeEntities: çift-encode'lu kaynak (≤3 geçişte çözülür)")
    void decodeEntities_doubleEncoded() {
        // &amp;ccedil; → ilk geçiş: &ccedil; → ikinci geçiş: ç
        assertThat(NewsHtmlUtil.decodeEntities("&amp;ccedil;")).isEqualTo("ç");
    }

    @Test
    @DisplayName("decodeEntities: üçlü-encode bile stabil olana kadar çözülür")
    void decodeEntities_tripleEncoded() {
        // &amp;amp;amp; → & (3 geçiş)
        assertThat(NewsHtmlUtil.decodeEntities("&amp;amp;amp;")).isEqualTo("&");
    }

    @Test
    @DisplayName("decodeEntities: çözüm sonucu kalan '&' (4+ kat) ham bırakılır")
    void decodeEntities_overEncoded_stopsAfterThreePasses() {
        // &amp;amp;amp;amp; → 3 geçiş sonra hâlâ "&amp;" kalır (4 kat encode)
        assertThat(NewsHtmlUtil.decodeEntities("&amp;amp;amp;amp;")).isEqualTo("&amp;");
    }

    @Test
    @DisplayName("decodeEntities: karışık metin + sayısal + isimli varlıklar")
    void decodeEntities_mixedContent() {
        assertThat(NewsHtmlUtil.decodeEntities("Tom &amp; Jerry &#33; &mdash; son"))
                .isEqualTo("Tom & Jerry ! — son");
    }

    @Test
    @DisplayName("decodeEntities: geçersiz ondalık (parse edilemez) → orijinal korunur")
    void decodeEntities_unparsableDecimal_keepsOriginal() {
        // &#; deseni NUM_ENTITY ile eşleşmez (en az bir rakam gerekli) → değişmeden kalır
        assertThat(NewsHtmlUtil.decodeEntities("&#;")).isEqualTo("&#;");
    }
}
