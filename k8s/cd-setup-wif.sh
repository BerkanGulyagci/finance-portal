#!/usr/bin/env bash
# CD kurulumu — Workload Identity Federation (anahtarsız GitHub→GCP auth).
# Cloud Shell'de bir kez çalıştır; sonunda yazılan 2 değeri GitHub Secrets'a ekle.
# Tekrar çalıştırmak güvenli (var olanları atlar).
set -euo pipefail

PROJECT_ID="project-f3ab8cbf-895a-47c2-91d"
GITHUB_REPO="BerkanGulyagci/finance-portal"   # owner/repo
POOL_ID="github-pool"
PROVIDER_ID="github-provider"
SA_NAME="github-actions-cd"
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud config set project "${PROJECT_ID}"
PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"

# Gerekli API'ler
gcloud services enable \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  artifactregistry.googleapis.com \
  container.googleapis.com

# Service account + roller (image push + GKE deploy)
gcloud iam service-accounts create "${SA_NAME}" \
  --display-name="GitHub Actions CD" 2>/dev/null || echo "  (SA zaten var)"
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/artifactregistry.writer" --condition=None --quiet
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/container.developer" --condition=None --quiet

# Workload Identity Pool + OIDC provider (sadece bu repo'ya izinli)
gcloud iam workload-identity-pools create "${POOL_ID}" \
  --location="global" \
  --display-name="GitHub Actions Pool" 2>/dev/null || echo "  (pool zaten var)"
gcloud iam workload-identity-pools providers create-oidc "${PROVIDER_ID}" \
  --location="global" \
  --workload-identity-pool="${POOL_ID}" \
  --display-name="GitHub OIDC" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='${GITHUB_REPO}'" \
  2>/dev/null || echo "  (provider zaten var)"

# Repo → service account impersonation izni
gcloud iam service-accounts add-iam-policy-binding "${SA_EMAIL}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/attribute.repository/${GITHUB_REPO}" \
  --quiet

WIF_PROVIDER="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/providers/${PROVIDER_ID}"

echo ""
echo "✅ Kurulum tamam. Şu 2 değeri GitHub repo Secrets'a ekle:"
echo "   (Settings → Secrets and variables → Actions → New repository secret)"
echo ""
echo "  GCP_WIF_PROVIDER     = ${WIF_PROVIDER}"
echo "  GCP_SERVICE_ACCOUNT  = ${SA_EMAIL}"
