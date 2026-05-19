package com.finance.portal.market.application.bond.evds.model;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EVDS serieList endpoint'inden gelen tek bir seri tanımını temsil eder.
 * bie_pydibs data group'undaki her DİBS serisi için bir EvdsSeriesInfo üretilir.
 *
 * Örnek EVDS response alanları:
 * SERIE_CODE       → "TP.TRD070727K10"
 * DATAGROUP_CODE   → "bie_pydibs"
 * SERIE_NAME       → "TRD070727K10 ( 07.01.2026 07.07.2027 )  Değer (24D2K3050128)"
 * FREQUENCY_STR    → "GÜNLÜK"
 * START_DATE       → "07-01-2026"
 * END_DATE         → "04-05-2026"
 */
public class EvdsSeriesInfo {

    private final String seriesCode;
    private final String datagroupCode;
    private final String seriesName;
    private final String seriesNameEng;
    private final String frequency;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public EvdsSeriesInfo(
            String seriesCode,
            String datagroupCode,
            String seriesName,
            String seriesNameEng,
            String frequency,
            LocalDate startDate,
            LocalDate endDate) {
        this.seriesCode = seriesCode;
        this.datagroupCode = datagroupCode;
        this.seriesName = seriesName;
        this.seriesNameEng = seriesNameEng;
        this.frequency = frequency;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getSeriesCode() { return seriesCode; }
    public String getDatagroupCode() { return datagroupCode; }
    public String getSeriesName() { return seriesName; }
    public String getSeriesNameEng() { return seriesNameEng; }
    public String getFrequency() { return frequency; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }

    /**
     * Seri kodundan instrument kodunu çıkarır.
     * "TP.TRD070727K10"      → "TRD070727K10"
     * "TP.TRD070727K10.ORAN" → "TRD070727K10"
     */
    public String extractInstrumentCode() {
        if (seriesCode == null) return null;
        // TP. prefix'ini kaldır
        String withoutPrefix = seriesCode.startsWith("TP.") ? seriesCode.substring(3) : seriesCode;
        // .ORAN suffix'ini kaldır
        int dotIdx = withoutPrefix.indexOf('.');
        return dotIdx >= 0 ? withoutPrefix.substring(0, dotIdx) : withoutPrefix;
    }

    /**
     * Bu serinin kupon faiz oranı serisi olup olmadığını döner.
     * "TP.TRD070727K10.ORAN" → true
     * "TP.TRD070727K10"      → false
     */
    public boolean isCouponRateSeries() {
        return seriesCode != null && seriesCode.endsWith(".ORAN");
    }

    /**
     * Bu serinin değer (gösterge) serisi olup olmadığını döner.
     */
    public boolean isValueSeries() {
        return !isCouponRateSeries();
    }

    /**
     * SERIE_NAME alanından ihraç tarihini parse eder.
     * Format: "TRD070727K10 ( 07.01.2026 07.07.2027 )  Değer ..."
     *                          ^^^^^^^^^^
     * @return ihraç tarihi, parse edilemezse null
     */
    public LocalDate parseIssueDateFromName() {
        return parseDateFromName(0);
    }

    /**
     * SERIE_NAME alanından vade tarihini parse eder.
     * Format: "TRD070727K10 ( 07.01.2026 07.07.2027 )  Değer ..."
     *                                    ^^^^^^^^^^
     * @return vade tarihi, parse edilemezse null
     */
    public LocalDate parseMaturityDateFromName() {
        return parseDateFromName(1);
    }

    /**
     * SERIE_NAME içindeki parantez bloğundan n. tarihi çıkarır.
     * "( 07.01.2026 07.07.2027 )" → index 0: 07.01.2026, index 1: 07.07.2027
     */
    private LocalDate parseDateFromName(int index) {
        if (seriesName == null) return null;
        // Parantez içindeki iki tarihi bul: ( dd.MM.yyyy dd.MM.yyyy )
        Pattern p = Pattern.compile("\\(\\s*(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(\\d{2}\\.\\d{2}\\.\\d{4})\\s*\\)");
        Matcher m = p.matcher(seriesName);
        if (!m.find()) return null;
        String dateStr = m.group(index + 1); // group 1 = issueDate, group 2 = maturityDate
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "EvdsSeriesInfo{code='" + seriesCode + "', name='" + seriesName + "', start=" + startDate + ", end=" + endDate + "}";
    }
}
