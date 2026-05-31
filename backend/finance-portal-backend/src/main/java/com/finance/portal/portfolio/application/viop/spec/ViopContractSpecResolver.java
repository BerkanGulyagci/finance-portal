package com.finance.portal.portfolio.application.viop.spec;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VİOP kontrat sembolünden dayanak varlık kodunu (YAML key'i) çıkarır.
 *
 * <p>BIST notasyonu: {@code F_<UNDERLIER><MMYY>[suffix]}. Birkaç özel durum:
 * <ul>
 *   <li>Düz hisse: {@code F_AKBNK0626} → AKBNK</li>
 *   <li>Bedelli/bedelsiz N1 variant: {@code F_BIMAS0626N1} → BIMAS (multiplier 200, ayrı entry değil)</li>
 *   <li>Mini gold: {@code F_XAUTRYM0626} → XAUTRY (M suffix dayanağa dahil değil)</li>
 *   <li>Elektrik: {@code F_ELCBASQ326} → ELCBASQ (Q+digit), {@code F_ELCBAS0626} → ELCBASM (default monthly)</li>
 *   <li>TLREF: {@code F_TLREF1M0526} → TLREF_VADE_ICI (default; vade-dışı için ayrı çağrı)</li>
 *   <li>DİBS: {@code F_TRT020926T17_KESN_T1_0626} → TRT020926T17</li>
 * </ul>
 *
 * <p>Sembol formatına uymayanlarda {@code null} döner; üst katman {@link ViopContractSpec#fallback}
 * kullanabilir.
 */
public final class ViopContractSpecResolver {

    private static final Pattern STANDARD = Pattern.compile(
            "^F_([A-Z0-9]+?)([0-9]{4})(?:N\\d+)?$"
    );

    /** {@code F_XAUTRYM0626} gibi — M suffix dayanak kod'a değil, mini-konvansiyona ait. */
    private static final Pattern XAUTRY_MINI = Pattern.compile("^F_XAUTRYM[0-9]{4}(?:N\\d+)?$");

    /** {@code F_ELCBASQ326} = Q + 3 (quarter) + 26 (year); {@code F_ELCBASY26}; {@code F_ELCBAS0626}. */
    private static final Pattern ELCBAS_QUARTERLY = Pattern.compile("^F_ELCBASQ\\d{3}$");
    private static final Pattern ELCBAS_YEARLY    = Pattern.compile("^F_ELCBASY\\d{2}$");
    private static final Pattern ELCBAS_MONTHLY   = Pattern.compile("^F_ELCBAS[0-9]{4}$");

    /** {@code F_TLREF1M0526}. */
    private static final Pattern TLREF = Pattern.compile("^F_TLREF\\d+[A-Z]?[0-9]{4}$");

    /** DİBS: {@code F_TRT020926T17_KESN_T1_0626} formatı. */
    private static final Pattern DIBS = Pattern.compile("^F_(TRT[0-9A-Z]+)(_[A-Z0-9_]+)?$");

    private ViopContractSpecResolver() { }

    /**
     * Sembolden dayanak varlık YAML key'ini çıkarır.
     *
     * @return null = sembol parse edilemedi
     */
    public static String resolveUnderlier(String contractSymbol) {
        if (contractSymbol == null || contractSymbol.isBlank()) {
            return null;
        }
        String s = contractSymbol.trim().toUpperCase();
        if (!s.startsWith("F_")) {
            return null;
        }

        if (XAUTRY_MINI.matcher(s).matches()) return "XAUTRY";
        if (ELCBAS_QUARTERLY.matcher(s).matches()) return "ELCBASQ";
        if (ELCBAS_YEARLY.matcher(s).matches()) return "ELCBASY";
        if (ELCBAS_MONTHLY.matcher(s).matches()) return "ELCBASM";
        if (TLREF.matcher(s).matches()) return "TLREF_VADE_ICI";

        Matcher dibs = DIBS.matcher(s);
        if (dibs.matches() && s.startsWith("F_TRT")) {
            return dibs.group(1);
        }

        Matcher std = STANDARD.matcher(s);
        if (std.matches()) {
            return std.group(1);
        }

        return null;
    }
}
