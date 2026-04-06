# ── ECR repository ────────────────────────────────────────────────────────────

resource "aws_ecr_repository" "ui" {
  name                 = "${var.project_name}-ui"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "ui" {
  repository = aws_ecr_repository.ui.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# ── Security group ────────────────────────────────────────────────────────────
# Separate from the backend SG — nginx listens on 80, backend on 8080.
# Only the ALB may open connections to the frontend task; the task itself
# needs unrestricted outbound only to pull the image from ECR on startup.

resource "aws_security_group" "ecs_ui" {
  name        = "${local.name_prefix}-ecs-ui-sg"
  description = "Allow port 80 from ALB to nginx frontend"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "HTTP from ALB"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ── CloudWatch log group ──────────────────────────────────────────────────────

resource "aws_cloudwatch_log_group" "ecs_ui" {
  name              = "/ecs/${local.name_prefix}-ui"
  retention_in_days = 30
}

# ── ECS task definition ───────────────────────────────────────────────────────
# nginx serving pre-built static files needs far less compute than Spring Boot.
# 256 CPU / 512 MB is plenty — keep it lean to minimise Fargate billing.
#
# No task_role_arn: nginx makes zero AWS SDK calls at runtime. The execution
# role (shared with the backend) is sufficient for image pull + log delivery.
#
# VITE_API_BASE_URL is baked into the JS bundle at docker build time, not at
# task launch time. The empty string tells axios to use relative paths — the
# ALB routes /scim/* to the backend so the browser never crosses origins.

resource "aws_ecs_task_definition" "ui" {
  family                   = "${local.name_prefix}-ui"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.ecs_execution.arn

  container_definitions = jsonencode([{
    name  = "${var.project_name}-ui"
    image = "${aws_ecr_repository.ui.repository_url}:latest"

    portMappings = [{
      containerPort = 80
      hostPort      = 80
      protocol      = "tcp"
    }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs_ui.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget -qO- http://localhost/ > /dev/null || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 10
    }
  }])
}

# ── ECS service ───────────────────────────────────────────────────────────────

resource "aws_ecs_service" "ui" {
  name            = "${local.name_prefix}-ui-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.ui.arn
  desired_count   = var.app_count
  launch_type     = "FARGATE"

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs_ui.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.ui.arn
    container_name   = "${var.project_name}-ui"
    container_port   = 80
  }

  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [aws_lb_listener.http]
}
