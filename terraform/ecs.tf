resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"   # free tier for basic metrics; adds per-task CPU/memory graphs
  }
}

# ── Task definition ───────────────────────────────────────────────────────────
# The task definition is the "blueprint" for a container run — it specifies the
# image, CPU/memory, networking mode, IAM roles, and how to inject config.
#
# Two roles are always involved in a Fargate task:
#   execution_role_arn — used by the ECS AGENT to pull the image, fetch secrets,
#                        and write logs. Never seen by your app code.
#   task_role_arn      — used by YOUR APP CODE at runtime. The AWS SDK picks up
#                        these credentials automatically from the ECS task
#                        metadata endpoint (169.254.170.2).

resource "aws_ecs_task_definition" "app" {
  family                   = local.name_prefix
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"   # required for Fargate; each task gets its own ENI
  cpu                      = var.container_cpu
  memory                   = var.container_memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name  = var.project_name
    # Initial image — CI/CD overwrites this on every deploy by registering a
    # new task definition revision with the SHA-tagged image.
    image = "${aws_ecr_repository.main.repository_url}:latest"

    portMappings = [{
      containerPort = 8080
      hostPort      = 8080
      protocol      = "tcp"
    }]

    # Non-secret configuration — safe to appear in task definition plaintext.
    # Spring Boot's relaxed binding maps SPRING_DATASOURCE_URL → spring.datasource.url,
    # overriding the localhost default in application.yml.
    environment = [
      { name = "SPRING_PROFILES_ACTIVE",      value = "prod" },
      { name = "SPRING_DATASOURCE_URL",        value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/${var.db_name}" },
      { name = "SPRING_DATASOURCE_USERNAME",   value = var.db_username },
      { name = "AWS_REGION",                   value = var.aws_region },
      { name = "SCIM_JWT_SECRET_NAME",         value = aws_secretsmanager_secret.jwt_signing_key.name },
      { name = "SCIM_JWT_ISSUER",              value = var.scim_jwt_issuer },
      { name = "SCIM_SES_FROM_ADDRESS",        value = var.ses_from_address },
      # Use caller-supplied base URL if provided, otherwise derive from ALB DNS.
      # ALB DNS is HTTP only — swap to HTTPS once you add ACM + HTTPS listener.
      { name = "SCIM_BASE_URL", value = var.scim_base_url != "" ? var.scim_base_url : "http://${aws_lb.main.dns_name}" },
      # Tell the JVM to use 75% of container memory as heap max.
      # Without this, the JVM defaults to 25% of physical RAM — on a 1 GB task
      # that's only 256 MB, which is borderline for Spring Boot under load.
      { name = "JAVA_TOOL_OPTIONS", value = "-XX:MaxRAMPercentage=75.0" },
    ]

    # Secrets are fetched by the ECS AGENT (execution role) and injected as env
    # vars at task startup. The raw secret value never appears in the task
    # definition JSON — only the ARN does. Use `arn` not `name` so the policy
    # resource match is exact and rotation doesn't break the reference.
    secrets = [{
      name      = "SPRING_DATASOURCE_PASSWORD"
      valueFrom = aws_secretsmanager_secret.db_password.arn
    }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }

    # Health check inside the container — ECS uses this to decide when a task
    # is healthy enough to receive traffic and when to restart it. The ALB
    # health check is separate (networking.tf) and is the authoritative signal
    # for traffic routing; this one is for container-level restart decisions.
    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:8080/scim/v2/ServiceProviderConfig || exit 1"]
      interval    = 30
      timeout     = 10
      retries     = 3
      startPeriod = 60   # Spring Boot startup takes ~20-30s; give it headroom
    }
  }])
}

# ── ECS service ───────────────────────────────────────────────────────────────
# The service keeps `app_count` tasks running at all times and replaces them
# if they fail. On each CI/CD deploy, the GitHub Actions workflow registers a
# new task definition revision and updates this service to run it.

resource "aws_ecs_service" "main" {
  name            = "${local.name_prefix}-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.app_count
  launch_type     = "FARGATE"

  # Minimum 100% / Maximum 200% ensures zero-downtime rolling deploys:
  # new tasks come up alongside old tasks before old tasks are stopped.
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  # ECS Exec — lets you `aws ecs execute-command` into a running task for
  # debugging without opening SSH. Useful during the demo phase.
  enable_execute_command = true

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs.id]
    # assign_public_ip is required for Fargate tasks in a public subnet to
    # pull images from ECR and call AWS APIs. If you move to private subnets
    # you would use NAT gateway or VPC endpoints instead.
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = var.project_name
    container_port   = 8080
  }

  # Ignore task_definition changes — CI/CD updates the task definition on every
  # deploy. Without this, Terraform would revert the running revision back to
  # whatever is in the .tf file the next time you run `terraform apply`.
  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [aws_lb_listener.http]
}
