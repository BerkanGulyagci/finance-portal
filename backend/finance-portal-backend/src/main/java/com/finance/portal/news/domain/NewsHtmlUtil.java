package com.finance.portal.news.domain;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Haber metni için HTML temizleme yardımcıları: etiket sıyırma + varlık çözme.
 * decodeEntities çift-encode'lu kaynakları (ör. {@code &amp;ccedil;}) da çözer.
 */
public final class NewsHtmlUtil {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern NUM_ENTITY = Pattern.compile("&#(x?[0-9a-fA-F]+);");
    private static final Pattern NAMED_ENTITY = Pattern.compile("&([a-zA-Z]+);");
    private static final Pattern WS = Pattern.compile("\\s+");
    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("nbsp", " "), Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"),
            Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("rsquo", "'"), Map.entry("lsquo", "'"),
            Map.entry("ldquo", "\""), Map.entry("rdquo", "\""), Map.entry("ndash", "–"), Map.entry("mdash", "—"),
            Map.entry("hellip", "…"), Map.entry("shy", ""), Map.entry("uuml", "ü"), Map.entry("Uuml", "Ü"),
            Map.entry("ouml", "ö"), Map.entry("Ouml", "Ö"), Map.entry("ccedil", "ç"), Map.entry("Ccedil", "Ç"),
            Map.entry("szlig", "ß"), Map.entry("eacute", "é"), Map.entry("aacute", "á"), Map.entry("agrave", "à"),
            Map.entry("acirc", "â"));

    private NewsHtmlUtil() {
    }

    /** HTML'i düz metne çevirir: etiketleri sıyır, varlıkları çöz, boşlukları sıkıştır, maxLen'e kırp (≤0 = kırpma). */
    public static String stripToText(String html, int maxLen) {
        if (html == null) {
            return null;
        }
        String text = decodeEntities(HTML_TAG.matcher(html).replaceAll(" ")).replace("­", "");
        text = WS.matcher(text).replaceAll(" ").trim();
        if (text.isEmpty()) {
            return null;
        }
        return (maxLen > 0 && text.length() > maxLen) ? text.substring(0, maxLen).trim() + "…" : text;
    }

    /** HTML varlıklarını çözer; çift-encode için stabil olana dek (≤3 geçiş) tekrarlar. */
    public static String decodeEntities(String s) {
        if (s == null) {
            return "";
        }
        if (s.indexOf('&') < 0) {
            return s;
        }
        String out = s;
        for (int pass = 0; pass < 3 && out.indexOf('&') >= 0; pass++) {
            String before = out;
            out = decodeOnce(out);
            if (out.equals(before)) {
                break;
            }
        }
        return out;
    }

    private static String decodeOnce(String s) {
        String out = NUM_ENTITY.matcher(s).replaceAll(mr -> {
            String g = mr.group(1);
            try {
                int cp = (g.charAt(0) == 'x' || g.charAt(0) == 'X')
                        ? Integer.parseInt(g.substring(1), 16) : Integer.parseInt(g);
                return Matcher.quoteReplacement(new String(Character.toChars(cp)));
            } catch (Exception e) {
                return Matcher.quoteReplacement(mr.group(0));
            }
        });
        out = NAMED_ENTITY.matcher(out).replaceAll(mr -> {
            String val = NAMED.get(mr.group(1));
            return Matcher.quoteReplacement(val != null ? val : mr.group(0));
        });
        return out;
    }
}
