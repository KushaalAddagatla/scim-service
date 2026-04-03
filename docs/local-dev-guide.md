# Local Development Guide

## Why this guide exists

Running the SCIM service locally requires two things the application needs before it can boot: a PostgreSQL database and a JWT signing key in Secrets Manager. Both are provided by Docker Compose — PostgreSQL directly, and LocalStack as a local stand-in for AWS Secrets Manager.

The catch is that **LocalStack is ephemeral**. Every `docker-compose down` wipes all state — secrets, queues, buckets — completely. The JWT signing key stored in Secrets Manager disappears with it. If you restart Docker Compose and try to start the server without re-seeding the key, `AwsSecretsManagerConfig` will throw an exception at startup before the application finishes booting.

---

## The startup sequence

Every local dev session follows the same four steps in order:

```bash
# 1. Start dependencies
docker-compose up -d

# 2. Seed the JWT signing key into LocalStack
./scripts/dev-setup.sh

# 3. Mint a Bearer token for curl / Postman testing
./scripts/mint-local-token.sh

# 4. Start the server
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Steps 2 and 3 are only needed after `docker-compose up`. If you restart the Spring Boot process without touching Docker, skip them.

---

## What dev-setup.sh does

`dev-setup.sh` automates the manual setup that would otherwise require remembering three separate commands and their flags.

**Without the script**, after every `docker-compose up -d` you would have to:

1. Generate a signing key:
   ```bash
   openssl rand -base64 32
   ```
2. Store it in LocalStack under the exact secret name the app expects:
   ```bash
   aws --endpoint-url=http://localhost:4566 secretsmanager create-secret \
     --name scim/jwt-signing-key \
     --secret-string "<paste key here>" \
     --region us-east-1
   ```
3. Hope you remembered the right secret name, endpoint URL, and region every time.

**With the script**, it is just:
```bash
./scripts/dev-setup.sh
```

The script also handles the common mistake of running it twice without restarting Docker Compose. `create-secret` returns a `ResourceExistsException` if the secret already exists — rather than crashing, the script detects this and automatically falls back to `put-secret-value` to update it instead.

After storing the key it does a round-trip read to verify the stored value matches what was written, and exits with an error if it does not.

If you want to reuse a key from a previous session (e.g., to keep a long-lived Okta token valid), pass it as an argument:
```bash
./scripts/dev-setup.sh "kLdw1tuH1sR6m7Tr7dT56X8OL+5X481FC1kQsu56334="
```

---

## What mint-local-token.sh does

`mint-local-token.sh` fetches the signing key that `dev-setup.sh` just stored in LocalStack and builds a signed JWT you can use with curl or Postman.

The token is signed with HMAC-SHA256 using the same key the server loads on startup, so it passes all three validation checks: correct signature, correct issuer (`scim-service`), and the required `scim:provision` scope. It is valid for 24 hours.

The script uses Python standard library only — no `pip install` required.

Output includes an `export TOKEN=...` line and ready-to-paste curl commands for all key endpoints:

```bash
./scripts/mint-local-token.sh

# Copy the export line, then:
export TOKEN='eyJ...'

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/scim/v2/Users | jq .
```

---

## When the server fails to start

If the app throws an error like `software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException` on startup, the secret is missing. Run `./scripts/dev-setup.sh` and try again.

If it throws a credentials error, LocalStack requires any valid AWS credentials to be configured. Either run `aws configure` (dummy values work: key ID `test`, secret `test`, region `us-east-1`), or prefix the start command:

```bash
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

---

## Docker Compose restart cheatsheet

| Situation | Key still in LocalStack? | Action needed |
|-----------|--------------------------|---------------|
| `docker-compose restart` (no `down`) | Yes | Nothing — state preserved |
| `docker-compose down` then `up` | **No** | Re-run `dev-setup.sh` |
| `docker-compose down -v` then `up` | **No** | Re-run `dev-setup.sh` |
| Spring Boot restart only | Yes | Nothing |
