package com.finance.portal.news.application.port;

/**
 * Metin çevirisi (makine çevirisi). Best-effort: çevrilemezse / kaynak=hedef ise orijinali döner.
 */
public interface TranslationPort {

    /**
     * {@code text}'i {@code targetLang}'a çevirir. {@code sourceLang} ipucu olarak kullanılır
     * (boş/null ise otomatik algılanır). Hata veya aynı dilde ise orijinal metni döner.
     */
    String translate(String text, String sourceLang, String targetLang);
}
