# ── JWT signing key ───────────────────────────────────────────────────────────
# The app's AwsSecretsManagerConfig fetches this secret at startup and decodes
# it from Base64 into a 256-bit HmacSHA256 key. Terraform generates 32 random
# bytes and stores them base64-encoded — exactly the format the app expects.
#
# The random_bytes resource produces a new value on first apply and never
# changes it again unless you taint the resource. This is intentional: rotating
# the key invalidates all issued JWTs, which would log out every active IdP
# connection and force re-auth.

resource "random_bytes" "jwt_signing_key" {
  length = 32   # 256-bit HMAC key
}

resource "aws_secretsmanager_secret" "jwt_signing_key" {
  name                    = "scim/jwt-signing-key"
  description             = "HMAC-SHA256 key for JWT signing (base64-encoded 32 bytes)"
  recovery_window_in_days = 0   # force-delete on destroy; avoids "scheduled for deletion" block on re-apply
}

resource "aws_secretsmanager_secret_version" "jwt_signing_key" {
  secret_id     = aws_secretsmanager_secret.jwt_signing_key.id
  secret_string = random_bytes.jwt_signing_key.base64
}

# ── Database password ─────────────────────────────────────────────────────────
# ECS injects this at task startup via the `secrets` block in the task
# definition — the ECS agent fetches the value from Secrets Manager using the
# task execution role and sets it as SPRING_DATASOURCE_PASSWORD in the
# container environment. The app never calls Secrets Manager for this; ECS does.

resource "random_password" "db_password" {
  length  = 32
  special = false   # RDS allows special chars but they can cause shell-escaping issues
                    # in some client tools; alphanumeric keeps it safe everywhere
}

resource "aws_secretsmanager_secret" "db_password" {
  name                    = "scim/db-password"
  description             = "RDS PostgreSQL master password"
  recovery_window_in_days = 0   # force-delete on destroy; avoids "scheduled for deletion" block on re-apply
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db_password.result
}
