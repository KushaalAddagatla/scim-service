# Local Dev — JWT Signing Key Setup

This document explains how to generate the HMAC-SHA256 signing key and load it into
LocalStack Secrets Manager for local development. These are one-time manual steps per
environment — they are not part of the build or test cycle.

---

## Prerequisites

- Docker running with `docker-compose up -d` (starts LocalStack on port 4566)
- AWS CLI installed (`brew install awscli`)
- AWS CLI configured (`aws configure`) — any dummy credentials work for LocalStack,
  but a real IAM user is recommended so the same CLI works against real AWS too

---

## Step 1 — Generate the signing key

Run this command to generate a cryptographically random 256-bit (32-byte) key,
Base64-encoded so it can be stored as a Secrets Manager string value:

```bash
openssl rand -base64 32
```

Example output (yours will differ):
```
kLdw1tuH1sR6m7Tr7dT56X8OL+5X481FC1kQsu56334=
```

Copy the output — you will use it in Step 2. Do **not** commit this value anywhere.

---

## Step 2 — Store the key in LocalStack

```bash
aws --endpoint-url=http://localhost:4566 secretsmanager create-secret \
  --name scim/jwt-signing-key \
  --secret-string "<paste your key here>" \
  --region us-east-1
```

Verify it was stored correctly:

```bash
aws --endpoint-url=http://localhost:4566 secretsmanager get-secret-value \
  --secret-id scim/jwt-signing-key \
  --region us-east-1 \
  --query SecretString \
  --output text
```

The output should match the key you generated in Step 1.

---

## Important — LocalStack is ephemeral

LocalStack does **not** persist state across restarts by default. There is no volume
mount for LocalStack in `docker-compose.yml`, so every `docker-compose down` wipes
all secrets, queues, and buckets.

**What this means in practice:**

| Situation | What to do |
|-----------|-----------|
| `docker-compose down` then `up` | Re-run Step 2 (`create-secret`) — the secret no longer exists |
| `docker-compose restart` (no `down`) | Nothing — state is preserved while the container is running |
| `docker-compose down -v` | Same as `down` — re-run Step 2 |

**Why `create-secret` and not `put-secret-value`:**
`put-secret-value` updates an existing secret. After a restart the secret doesn't exist
at all, so `create-secret` is always the right command. If you accidentally run it twice
without a restart you'll get a `ResourceExistsException` — that's fine, it means the
secret is already there.

---

## Activating the local profile

`AwsSecretsManagerConfig` only activates when `scim.jwt.secret-name` is set.
That property is defined in `application-local.yml`, which loads when the `local`
Spring profile is active.

To run the application locally with the signing key wired up:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Or set the environment variable before running:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

---

## Production

In production (ECS), the `prod` Spring profile is active. The secret name is supplied
via the `SCIM_JWT_SECRET_NAME` environment variable and the real AWS SDK endpoint
resolution is used (no `--endpoint-url` override). The ECS task role must have
`secretsmanager:GetSecretValue` permission scoped to `arn:aws:secretsmanager:*:*:secret:scim/*`.
