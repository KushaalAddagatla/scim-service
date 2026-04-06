data "aws_iam_policy_document" "ecs_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# ── Task execution role ───────────────────────────────────────────────────────
# Used by the ECS AGENT (not your app) to:
#   - Pull the container image from ECR
#   - Push container logs to CloudWatch
#   - Fetch secrets from Secrets Manager and inject them as env vars
#
# This is NOT the role your application code uses at runtime. Keep the two
# roles separate — the execution role is an ECS infrastructure concern; the
# task role is an application concern.

resource "aws_iam_role" "ecs_execution" {
  name               = "${local.name_prefix}-ecs-execution-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

# AWS-managed policy covers ECR image pull + CloudWatch Logs
resource "aws_iam_role_policy_attachment" "ecs_execution_managed" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Custom policy for secrets — the managed policy above does NOT include
# Secrets Manager access; it must be added explicitly.
resource "aws_iam_role_policy" "ecs_execution_secrets" {
  name = "read-task-secrets"
  role = aws_iam_role.ecs_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = [aws_secretsmanager_secret.db_password.arn]
    }]
  })
}

# ── Task role ─────────────────────────────────────────────────────────────────
# Used by YOUR APPLICATION CODE at runtime. The SDK calls inside Spring Boot
# automatically pick up credentials from this role via the ECS task metadata
# endpoint (no explicit credential config needed in code).
#
# Principle of least privilege: each permission maps to a real code path:
#   - secretsmanager:GetSecretValue → AwsSecretsManagerConfig (JWT key fetch)
#   - ses:Send*                     → CertificationService (approval emails)
#   - cloudwatch:PutMetricData      → CertificationScheduler (metrics)

resource "aws_iam_role" "ecs_task" {
  name               = "${local.name_prefix}-ecs-task-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

resource "aws_iam_role_policy" "ecs_task_permissions" {
  name = "scim-service-runtime"
  role = aws_iam_role.ecs_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadJwtSigningKey"
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = [aws_secretsmanager_secret.jwt_signing_key.arn]
      },
      {
        Sid    = "SendCertificationEmails"
        Effect = "Allow"
        Action = [
          "ses:SendEmail",
          "ses:SendRawEmail"
        ]
        # Restrict to the configured set so a compromised task can't send
        # from arbitrary SES identities in your account.
        Resource = ["*"]
        Condition = {
          StringEquals = {
            "ses:FromAddress" = var.ses_from_address
          }
        }
      },
      {
        Sid      = "PutCertificationMetrics"
        Effect   = "Allow"
        Action   = ["cloudwatch:PutMetricData"]
        Resource = ["*"]
        Condition = {
          StringEquals = {
            "cloudwatch:namespace" = "ScimService/Certifications"
          }
        }
      }
    ]
  })
}
