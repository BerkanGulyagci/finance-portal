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
 * Adet/Sözleşme formatı (ondalık yok)
 */
export function formatQuantity(value) {
  return formatTrNumber(value, 0);
}
