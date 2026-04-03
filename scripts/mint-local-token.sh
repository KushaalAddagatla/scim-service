#!/usr/bin/env bash
# =============================================================================
# mint-local-token.sh — mint a 24-hour Bearer JWT for local curl testing
#
# Fetches the HMAC-SHA256 signing key from LocalStack Secrets Manager and
# builds a signed JWT that the local SCIM server will accept. No PyJWT or
# any external Python packages are required — uses Python stdlib only.
#
# Usage:
#   ./scripts/mint-local-token.sh
#
# Output:
#   - The raw JWT (copy-paste into Authorization header)
#   - Ready-to-run curl examples for all key endpoints
#
# Prerequisites:
#   - docker-compose up -d (LocalStack running on port 4566)
#   - ./scripts/dev-setup.sh already run (secret seeded)
#   - AWS CLI installed
#   - Python 3 installed (python3 must be on PATH)
# =============================================================================
set -euo pipefail

LOCALSTACK_URL="http://localhost:4566"
SECRET_NAME="scim/jwt-signing-key"
REGION="us-east-1"
ISSUER="scim-service"
SERVER="http://localhost:8080"

green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
red() { printf '\033[0;31m%s\033[0m\n' "$*"; }

# ── Prerequisites ─────────────────────────────────────────────────────────────
if ! command -v aws &>/dev/null; then
  red "ERROR: AWS CLI not found. Install with: brew install awscli"
  exit 1
fi

if ! command -v python3 &>/dev/null; then
  red "ERROR: python3 not found."
  exit 1
fi

if ! curl -sf "${LOCALSTACK_URL}/health" &>/dev/null; then
  red "ERROR: LocalStack not reachable. Run 'docker-compose up -d' first."
  exit 1
fi

# ── Fetch signing key from LocalStack ─────────────────────────────────────────
KEY_B64=$(aws --endpoint-url="${LOCALSTACK_URL}" \
              --region="${REGION}" \
              secretsmanager get-secret-value \
              --secret-id "${SECRET_NAME}" \
              --query SecretString \
              --output text 2>/dev/null) || {
  red "ERROR: Could not fetch secret '${SECRET_NAME}' from LocalStack."
  echo "       Run './scripts/dev-setup.sh' first to seed the signing key."
  exit 1
}

# ── Mint JWT using Python stdlib (no pip install needed) ──────────────────────
# Python builds the three JWT parts, computes HMAC-SHA256 over "header.payload",
# and assembles the final token. This produces the same format that NimbusJwtDecoder
# (used in SecurityConfig) expects for HS256 tokens.
TOKEN=$(python3 - "${KEY_B64}" "${ISSUER}" <<'PYEOF'
import sys, hmac, hashlib, base64, json, time

key_b64, issuer = sys.argv[1], sys.argv[2]
key = base64.b64decode(key_b64)

def b64url(data):
    if isinstance(data, str):
        data = data.encode()
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode()

now = int(time.time())
header  = b64url(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(',', ':')))
payload = b64url(json.dumps({
    "iss":   issuer,
    "sub":   "local-dev",
    "scope": "scim:provision",
    "iat":   now,
    "exp":   now + 86400   # 24 hours
}, separators=(',', ':')))

signing_input = f"{header}.{payload}".encode()
sig = b64url(hmac.new(key, signing_input, hashlib.sha256).digest())
print(f"{header}.{payload}.{sig}")
PYEOF
)

# ── Print token and curl examples ─────────────────────────────────────────────
echo ""
green "=== Bearer token (valid for 24 hours) ==="
echo ""
echo "${TOKEN}"
echo ""
green "=== export for use in this shell session ==="
echo ""
echo "  export TOKEN='${TOKEN}'"
echo ""
green "=== Smoke-test curl commands (paste after export TOKEN=...) ==="
echo ""
cat <<CURL
# Discovery — no token needed (RFC 7644 §4)
curl -s ${SERVER}/scim/v2/ServiceProviderConfig | jq .

# List users
curl -s -H "Authorization: Bearer \$TOKEN" ${SERVER}/scim/v2/Users | jq .

# Provision a user
curl -s -X POST ${SERVER}/scim/v2/Users \\
  -H "Authorization: Bearer \$TOKEN" \\
  -H "Content-Type: application/scim+json" \\
  -d '{
    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
    "userName": "jdoe@example.com",
    "name": { "givenName": "Jane", "familyName": "Doe" },
    "emails": [{ "value": "jdoe@example.com", "type": "work", "primary": true }],
    "active": true
  }' | jq .

# Filter by userName (Okta pre-check)
curl -s -H "Authorization: Bearer \$TOKEN" \\
  "${SERVER}/scim/v2/Users?filter=userName+eq+%22jdoe%40example.com%22" | jq .

# List groups
curl -s -H "Authorization: Bearer \$TOKEN" ${SERVER}/scim/v2/Groups | jq .
CURL
echo ""
