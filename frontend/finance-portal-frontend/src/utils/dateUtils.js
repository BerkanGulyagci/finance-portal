// Backend tarafından (LocalDateTime; Jackson default) "2026-06-01T00:49:52.123"
// gibi TZ-belirteçsiz ISO döndüğünde, JS new Date() bunu YEREL saat olarak
// yorumlar. Backend JVM TZ'si UTC iken bu kullanıcıya 3 saatlik fark olarak
// görünür (Europe/Istanbul). Bu yardımcı, TZ ek-belirteci yoksa 'Z' (UTC)
// ekleyerek string'i UTC olarak parse etmeyi sağlar; toLocaleString sonra
// kullanıcının yereline çevirir.
//
// Not: Backend bir gün Instant/OffsetDateTime'a geçer veya TZ env eklenirse,
// bu fonksiyon zaten TZ-belirteçli girdileri olduğu gibi parse eder; yani
// geri uyumluluk korunur.
export function parseBackendDate(s) {
  if (!s) return null;
  // Sadece string için işle; Date veya number geldiyse new Date'e bırak.
  if (typeof s !== 'string') {
    const d = new Date(s);
    return isNaN(d.getTime()) ? null : d;
  }
  const hasTz = /(Z|[+-]\d{2}:?\d{2})$/.test(s);
  const d = new Date(hasTz ? s : s + 'Z');
  return isNaN(d.getTime()) ? null : d;
}
