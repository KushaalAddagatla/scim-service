#!/usr/bin/env bash
# =============================================================================
# dev-setup.sh — seed LocalStack Secrets Manager with the JWT signing key
#
# Run this once after every `docker-compose up -d`. LocalStack does not persist
# state across restarts, so the secret must be re-seeded each time.
#
# Usage:
#   ./scripts/dev-setup.sh              # generate a fresh key and store it
#   ./scripts/dev-setup.sh <base64key>  # store a specific key (e.g., to reuse
#                                       # a key from a previous session)
#
# Prerequisites:
#   - Docker running with `docker-compose up -d`
#   - AWS CLI installed (`brew install awscli`)
#   - Any AWS credentials configured (LocalStack accepts dummy values;
#     set AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test if unconfigured)
# =============================================================================
set -euo pipefail

LOCALSTACK_URL="http://localhost:4566"
SECRET_NAME="scim/jwt-signing-key"
REGION="us-east-1"

# ── Colour helpers ────────────────────────────────────────────────────────────
green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }
red() { printf '\033[0;31m%s\033[0m\n' "$*"; }

# ── Check prerequisites ───────────────────────────────────────────────────────
if ! command -v aws &>/dev/null; then
  red "ERROR: AWS CLI not found. Install with: brew install awscli"
  exit 1
fi

# Verify LocalStack is reachable before doing anything else
if ! curl -sf "${LOCALSTACK_URL}/health" &>/dev/null; then
  red "ERROR: LocalStack is not reachable at ${LOCALSTACK_URL}."
  echo "       Run 'docker-compose up -d' and wait a few seconds, then retry."
  exit 1
fi

# ── Resolve or generate the signing key ──────────────────────────────────────
if [[ $# -ge 1 && -n "$1" ]]; then
  SIGNING_KEY="$1"
  yellow "Using provided signing key."
else
  SIGNING_KEY=$(openssl rand -base64 32)
  yellow "Generated new 256-bit signing key."
fi

# ── Store in LocalStack Secrets Manager ──────────────────────────────────────
# Try create-secret first. If the secret already exists (ResourceExistsException),
# fall back to put-secret-value. This handles the common case where dev-setup.sh
# is run twice without a docker-compose restart.
if aws --endpoint-url="${LOCALSTACK_URL}" \
       --region="${REGION}" \
       secretsmanager create-secret \
       --name "${SECRET_NAME}" \
       --secret-string "${SIGNING_KEY}" \
       --output text &>/dev/null 2>&1; then
  green "Secret '${SECRET_NAME}' created in LocalStack."
else
  aws --endpoint-url="${LOCALSTACK_URL}" \
      --region="${REGION}" \
      secretsmanager put-secret-value \
      --secret-id "${SECRET_NAME}" \
      --secret-string "${SIGNING_KEY}" \
      --output text &>/dev/null
  green "Secret '${SECRET_NAME}' updated in LocalStack (already existed)."
fi

# ── Verify round-trip ─────────────────────────────────────────────────────────
STORED_KEY=$(aws --endpoint-url="${LOCALSTACK_URL}" \
                 --region="${REGION}" \
                 secretsmanager get-secret-value \
                 --secret-id "${SECRET_NAME}" \
                 --query SecretString \
                 --output text)

if [[ "${STORED_KEY}" != "${SIGNING_KEY}" ]]; then
  red "ERROR: Round-trip verification failed — stored key does not match."
  exit 1
fi

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
green "=== LocalStack setup complete ==="
echo ""
echo "  Secret name : ${SECRET_NAME}"
echo "  Region      : ${REGION}"
echo "  Endpoint    : ${LOCALSTACK_URL}"
echo ""
echo "Next step: mint a Bearer token for curl testing:"
echo ""
echo "  ./scripts/mint-local-token.sh"
echo ""
echo "Then start the server:"
echo ""
echo "  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local"
echo ""
