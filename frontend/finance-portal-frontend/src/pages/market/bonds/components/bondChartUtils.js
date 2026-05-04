/**
 * EVDS Tahvil/Bono grafik hesaplama yardımcıları.
 * Tüm hesaplamalar frontend'de yapılır; EVDS formül endpoint'leri çağrılmaz.
 */

/**
 * History noktalarını KLineCharts formatına çevirir.
 */
export function toKlineData(points) {
  return points
    .map(p => {
      const v = parseFloat(p.indicatorValue);
      if (isNaN(v)) return null;
      return {
        timestamp: new Date(p.date).getTime(),
        open: v, high: v, low: v, close: v,
        volume: 0, turnover: 0,
        _date: p.date, _dateText: p.dateText ?? p.date, _value: v,
      };
    })
    .filter(Boolean)
    .sort((a, b) => a.timestamp - b.timestamp);
}

/**
 * Performans grafiği için normalize eder.
 * baseDate verilirse o tarihteki değer = 100 baz alınır.
 * Verilmezse serinin ilk geçerli değeri baz alınır.
 */
export function normalizeSeries(points, baseDate = null) {
  let firstValue = null;
  if (baseDate) {
    const basePt = points.find(p => p.date === baseDate);
    if (basePt) firstValue = parseFloat(basePt.indicatorValue);
  }
  if (firstValue == null || isNaN(firstValue) || firstValue === 0) {
    const values = points.map(p => parseFloat(p.indicatorValue)).filter(v => !isNaN(v) && v > 0);
    firstValue = values[0] ?? null;
  }
  if (!firstValue) return points;

  return points.map(p => {
    const v = parseFloat(p.indicatorValue);
    if (isNaN(v)) return { ...p, indicatorValue: null };
    return { ...p, _rawValue: v, indicatorValue: (v / firstValue) * 100 };
  });
}

/**
 * İki history serisinin ortak ilk tarihini bulur.
 * Her iki seride de değer olan en erken tarih döner.
 */
export function findCommonStartDate(mainPoints, comparePoints) {
  const mainDates    = new Set(mainPoints.map(p => p.date));
  const compareDates = new Set(comparePoints.map(p => p.date));
  const common = [...mainDates].filter(d => compareDates.has(d)).sort();
  return common.length > 0 ? common[0] : null;
}

/**
 * Seçili periyot değişim yüzdesi.
 */
export function calculatePeriodChangePercent(points) {
  const values = points.map(p => parseFloat(p.indicatorValue)).filter(v => !isNaN(v) && v > 0);
  if (values.length < 2) return null;
  return ((values[values.length - 1] - values[0]) / values[0]) * 100;
}

/**
 * Kayan hareketli ortalama dizisi üretir.
 */
export function buildMALine(values, n) {
  return values.map((_, i) => {
    if (i < n - 1) return null;
    const slice = values.slice(i - n + 1, i + 1);
    return slice.reduce((a, b) => a + b, 0) / n;
  });
}

/**
 * İki history serisini tarih bazında birleştirir.
 * Eksik tarihler için compareValue = null döner.
 */
export function mergeByDate(mainPoints, comparePoints) {
  const compareMap = new Map(
    comparePoints
      .filter(p => p.indicatorValue != null)
      .map(p => [p.date, parseFloat(p.indicatorValue)])
  );
  return mainPoints
    .filter(p => p.indicatorValue != null)
    .map(p => ({
      date:         p.date,
      dateText:     p.dateText ?? p.date,
      mainValue:    parseFloat(p.indicatorValue),
      compareValue: compareMap.has(p.date) ? compareMap.get(p.date) : null,
    }));
}

/**
 * Türkçe sayı formatı.
 */
export function fmtNum(v, decimals = 2) {
  if (v == null || isNaN(v)) return '-';
  return parseFloat(v).toLocaleString('tr-TR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

/**
 * Yüzde değişim formatı — tutarlı format:
 * Pozitif: +%3,22  |  Negatif: -%8,57  |  Sıfır: %0,00
 */
export function fmtPct(v, decimals = 2) {
  if (v == null || isNaN(v)) return '-';
  const n = parseFloat(v);
  if (n > 0) return `+%${fmtNum(n, decimals)}`;
  if (n < 0) return `-%${fmtNum(Math.abs(n), decimals)}`;
  return `%${fmtNum(0, decimals)}`;
}
