# =============================================================================
# Custom Keycloak image — özel "finance-portal" login temasını gömer.
#
# Neden: GKE'de ConfigMap ile tema mount etmek (binary png + alt klasörler)
# zahmetli. En temiz yol: temayı image'a COPY etmek. loginTheme=finance-portal
# realm ayarı zaten doğru; tema dosyaları image'da olunca Keycloak bulur.
#
# Build (proje kökünden):
#   docker build -f docker/keycloak/Dockerfile -t <registry>/keycloak:v1 .
# =============================================================================
FROM quay.io/keycloak/keycloak:26.0

# Özel login temasını Keycloak tema dizinine kopyala.
COPY docker/keycloak/themes/finance-portal /opt/keycloak/themes/finance-portal
