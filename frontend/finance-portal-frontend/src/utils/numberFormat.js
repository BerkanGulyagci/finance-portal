/**
 * Türkçe sayı formatını parse eder
 * Örnekler:
 * "5.734,00" -> 5734
 * "77.813" -> 77813
 * "95,45" -> 95.45
 * "-0,88" -> -0.88
 * "146.129,00" -> 146129
 */
export function parseTrNumber(value) {
  if (value === null || value === undefined || value === "") return null;

  // API'den gelen JSON sayıları (örn. BigDecimal → 55.91) doğrudan kabul et.
  // Aksi halde "55.91" string'i Türk binlik sanılıp "5591"e dönüşürdü.
  if (typeof value === "number" && Number.isFinite(value)) return value;

  // String'e çevir ve trim
  const str = String(value).trim();
  
  // Boş string kontrolü
  if (str === "" || str === "-") return null;
  
  // Binlik ayracı olan noktaları kaldır, ondalık virgülü noktaya çevir
  const normalized = str.replace(/\./g, "").replace(",", ".");
  
  const num = Number(normalized);
  return Number.isNaN(num) ? null : num;
}

/**
 * Sayıyı Türkçe formatta gösterir
 * @param {number} value - Gösterilecek sayı
 * @param {number} maximumFractionDigits - Maksimum ondalık basamak sayısı
 */
export function formatTrNumber(value, maximumFractionDigits = 2) {
  if (value === null || value === undefined || Number.isNaN(value)) return "-";
  
  return new Intl.NumberFormat("tr-TR", {
    minimumFractionDigits: 0,
    maximumFractionDigits
  }).format(value);
}

/**
 * Fiyat formatı (2 ondalık basamak)
 */
export function formatPrice(value) {
  return formatTrNumber(value, 2);
}

/**
 * klinecharts y-ekseni / OHLC tooltip için ondalık hassasiyeti.
 * Varsayılan 2 ondalık, ~0.00001 TRY gibi mikro fiyatları 0.00 gösterir.
 */
export function computeKlinePricePrecision(values) {
  const nums = (Array.isArray(values) ? values : [])
    .map((v) => Number(v))
    .filter((v) => Number.isFinite(v) && v > 0);
  if (!nums.length) return 2;
  const min = Math.min(...nums);
  if (min >= 100) return 2;
  if (min >= 1) return 4;
  if (min >= 0.01) return 4;
  const exp = Math.floor(Math.log10(min));
  return Math.min(12, Math.max(4, -exp + 3));
}

export function computeKlineVolumePrecision(values) {
  const nums = (Array.isArray(values) ? values : [])
    .map((v) => Number(v))
    .filter((v) => Number.isFinite(v) && v > 0);
  if (!nums.length) return 2;
  const max = Math.max(...nums);
  if (max >= 1_000_000) return 0;
  if (max >= 1) return 2;
  const exp = Math.floor(Math.log10(max));
  return Math.min(8, Math.max(2, -exp + 2));
}

/**
 * Adet/Sözleşme formatı (ondalık yok)
 */
export function formatQuantity(value) {
  return formatTrNumber(value, 0);
}
