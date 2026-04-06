output "alb_dns_name" {
  description = "Public URL of the load balancer — use as SCIM_BASE_URL"
  value       = "http://${aws_lb.main.dns_name}"
}

output "ecr_repository_url" {
  description = "ECR image URL — set as ECR_REGISTRY + ECR_REPOSITORY in GitHub Secrets"
  value       = aws_ecr_repository.main.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name — set as ECS_CLUSTER in GitHub Secrets"
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "ECS service name — set as ECS_SERVICE in GitHub Secrets"
  value       = aws_ecs_service.main.name
}

output "ecs_task_definition_family" {
  description = "ECS task definition family — set as ECS_TASK_DEF_NAME in GitHub Secrets"
  value       = aws_ecs_task_definition.app.family
}

output "ecs_container_name" {
  description = "Container name inside the task definition — set as CONTAINER_NAME in GitHub Secrets"
  value       = var.project_name
}

output "rds_endpoint" {
  description = "RDS hostname (without port) — for debugging only, not needed in app config"
  value       = aws_db_instance.main.address
}

output "jwt_secret_name" {
  description = "Secrets Manager secret name for the JWT signing key"
  value       = aws_secretsmanager_secret.jwt_signing_key.name
}

# ── Frontend outputs ──────────────────────────────────────────────────────────

output "ecr_repository_ui_url" {
  description = "Frontend ECR image URL — set ECR_REPOSITORY_UI = last segment in GitHub Secrets"
  value       = aws_ecr_repository.ui.repository_url
}

output "ecs_service_ui_name" {
  description = "Frontend ECS service name — set as ECS_SERVICE_UI in GitHub Secrets"
  value       = aws_ecs_service.ui.name
}

output "ecs_task_definition_ui_family" {
  description = "Frontend task definition family — set as ECS_TASK_DEF_NAME_UI in GitHub Secrets"
  value       = aws_ecs_task_definition.ui.family
}

output "ecs_container_ui_name" {
  description = "Frontend container name — set as CONTAINER_NAME_UI in GitHub Secrets"
  value       = "${var.project_name}-ui"
}
