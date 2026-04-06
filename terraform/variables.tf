variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Used as a prefix on every resource name"
  type        = string
  default     = "scim-service"
}

variable "environment" {
  description = "Deployment environment (prod, staging)"
  type        = string
  default     = "prod"
}

# ── Database ──────────────────────────────────────────────────────────────────

variable "db_name" {
  description = "PostgreSQL database name — must match Flyway baseline"
  type        = string
  default     = "scimdb"
}

variable "db_username" {
  description = "PostgreSQL master username"
  type        = string
  default     = "scimuser"
}

variable "db_instance_class" {
  description = "RDS instance class — db.t3.micro is free-tier eligible"
  type        = string
  default     = "db.t3.micro"
}

# ── Application ───────────────────────────────────────────────────────────────

variable "scim_jwt_issuer" {
  description = "Value checked in the JWT 'iss' claim — must match token minting config"
  type        = string
  default     = "scim-service"
}

variable "ses_from_address" {
  description = "Verified SES sender address for certification emails (must be verified in SES)"
  type        = string
}

variable "scim_base_url" {
  description = "Public base URL of the service — used in SES email links. Defaults to ALB DNS if empty."
  type        = string
  default     = ""
}

# ── ECS task sizing ───────────────────────────────────────────────────────────
# 512 CPU / 1024 MB is the smallest Fargate config that comfortably runs a
# Spring Boot app. Fargate bills per vCPU-second and GB-second, so right-sizing
# here directly controls your compute bill.

variable "container_cpu" {
  description = "ECS task CPU units (1024 = 1 vCPU)"
  type        = number
  default     = 512
}

variable "container_memory" {
  description = "ECS task memory in MB"
  type        = number
  default     = 1024
}

variable "app_count" {
  description = "Number of ECS tasks to run — set to 2+ for HA"
  type        = number
  default     = 1
}
