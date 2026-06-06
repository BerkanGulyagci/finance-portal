#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# CD KURULUMU — Workload Identity Federation (anahtarsız GitHub→GCP auth)
#
# NASIL ÇALIŞTIRILIR:
#   1. https://console.cloud.google.com aç (projen seçili olsun)
#   2. Sağ üstte terminal ikonu (>_) → "Activate Cloud Shell"
#   3. Bu dosyanın TAMAMINI kopyala, Cloud Shell'e yapıştır, Enter
#   4. Script sonunda yazılan 2 değeri GitHub repo Secrets'a ekle (talimat en altta)
#
# Tek seferlik. Tekrar çalıştırılırsa "already exists" uyarıları verir, zararsız.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Senin projene özel sabitler ──────────────────────────────────────────────
PROJECT_ID="project-f3ab8cbf-895a-47c2-91d"
GITHUB_REPO="BerkanGulyagci/finance-portal"   # owner/repo
POOL_ID="github-pool"
PROVIDER_ID="github-provider"
SA_NAME="github-actions-cd"
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

echo "▶ Proje ayarlanıyor: ${PROJECT_ID}"
gcloud config set project "${PROJECT_ID}"
PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"
echo "  Proje numarası: ${PROJECT_NUMBER}"

# ── Gerekli API'leri aç ───────────────────────────────────────────────────────
echo "▶ API'ler etkinleştiriliyor (iamcredentials, sts, artifactregistry, container)..."
gcloud services enable \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  artifactregistry.googleapis.com \
  container.googleapis.com

# ── 1) Service Account ────────────────────────────────────────────────────────
echo "▶ Service account oluşturuluyor: ${SA_EMAIL}"
gcloud iam service-accounts create "${SA_NAME}" \
  --display-name="GitHub Actions CD" 2>/dev/null || echo "  (zaten var, atlanıyor)"

# ── 2) Rolleri ver (image push + GKE deploy) ─────────────────────────────────
echo "▶ Roller veriliyor (Artifact Registry Writer + GKE Developer)..."
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/artifactregistry.writer" --condition=None --quiet
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/container.developer" --condition=None --quiet

# ── 3) Workload Identity Pool ─────────────────────────────────────────────────
echo "▶ Workload Identity Pool: ${POOL_ID}"
gcloud iam workload-identity-pools create "${POOL_ID}" \
  --location="global" \
  --display-name="GitHub Actions Pool" 2>/dev/null || echo "  (zaten var, atlanıyor)"

# ── 4) OIDC Provider (GitHub) — sadece SENİN repo'na izin ────────────────────
echo "▶ OIDC Provider: ${PROVIDER_ID} (repo kısıtlı: ${GITHUB_REPO})"
gcloud iam workload-identity-pools providers create-oidc "${PROVIDER_ID}" \
  --location="global" \
  --workload-identity-pool="${POOL_ID}" \
  --display-name="GitHub OIDC" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='${GITHUB_REPO}'" \
  2>/dev/null || echo "  (zaten var, atlanıyor)"

# ── 5) GitHub repo → service account taklit (impersonation) izni ─────────────
echo "▶ GitHub repo'ya service account kullanma izni veriliyor..."
gcloud iam service-accounts add-iam-policy-binding "${SA_EMAIL}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/attribute.repository/${GITHUB_REPO}" \
  --quiet

# ── Çıktı: GitHub'a koyacağın 2 secret ───────────────────────────────────────
WIF_PROVIDER="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/providers/${PROVIDER_ID}"

echo ""
echo "═══════════════════════════════════════════════════════════════════════"
echo "✅ KURULUM TAMAM. Şu 2 değeri GitHub'a ekle:"
echo "   GitHub repo → Settings → Secrets and variables → Actions → New secret"
echo "═══════════════════════════════════════════════════════════════════════"
echo ""
echo "  Secret adı:  GCP_WIF_PROVIDER"
echo "  Değeri:      ${WIF_PROVIDER}"
echo ""
echo "  Secret adı:  GCP_SERVICE_ACCOUNT"
echo "  Değeri:      ${SA_EMAIL}"
echo ""
echo "═══════════════════════════════════════════════════════════════════════"
echo "Sonra: GitHub repo → Settings → Environments → New environment → 'production'"
echo "       → 'Required reviewers' işaretle → kendini ekle → Save"
echo "       (Bu, deploy'dan önce manuel onay kapısını açar.)"
echo "═══════════════════════════════════════════════════════════════════════"
