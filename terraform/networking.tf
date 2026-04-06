# ── VPC ───────────────────────────────────────────────────────────────────────

resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true   # required for RDS hostnames to resolve within the VPC

  tags = { Name = "${local.name_prefix}-vpc" }
}

# ── Subnets ───────────────────────────────────────────────────────────────────
# Two public subnets (ALB + ECS tasks) and two private subnets (RDS) spread
# across two AZs. Fargate runs in public subnets with assign_public_ip=true,
# eliminating the need for a NAT gateway ($32/month). RDS stays in private
# subnets — reachable from ECS within the VPC, not from the internet.

resource "aws_subnet" "public" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet("10.0.0.0/16", 8, count.index + 1)   # 10.0.1.0/24, 10.0.2.0/24
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = { Name = "${local.name_prefix}-public-${count.index + 1}" }
}

resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet("10.0.0.0/16", 8, count.index + 10)  # 10.0.10.0/24, 10.0.11.0/24
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = { Name = "${local.name_prefix}-private-${count.index + 1}" }
}

# ── Internet Gateway + routing ────────────────────────────────────────────────

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${local.name_prefix}-igw" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }
  tags = { Name = "${local.name_prefix}-public-rt" }
}

resource "aws_route_table_association" "public" {
  count          = 2
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# ── Security groups ───────────────────────────────────────────────────────────

# ALB — accepts public HTTP traffic, forwards to ECS tasks
resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb-sg"
  description = "Allow inbound HTTP from the internet"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ECS tasks — accept traffic from the ALB only; egress unrestricted so the
# container can reach ECR (image pull), Secrets Manager, SES, and CloudWatch
# over HTTPS without a VPC endpoint ($7/month each).
resource "aws_security_group" "ecs" {
  name        = "${local.name_prefix}-ecs-sg"
  description = "Allow inbound from ALB, unrestricted outbound"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "App port from ALB"
    from_port       = 8080
    to_port         = 8080
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

# RDS — accessible only from ECS tasks within the VPC, never from the internet
resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds-sg"
  description = "Allow PostgreSQL from ECS tasks only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from ECS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }
}

# ── Application Load Balancer ─────────────────────────────────────────────────
# Internet-facing ALB distributes traffic across ECS tasks and provides a
# stable DNS name (the task public IP changes on every task restart).
# Health checks target the public discovery endpoint — no auth required per
# RFC 7644 §4, so the ALB doesn't need to forward any headers.

resource "aws_lb" "main" {
  name               = "${local.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  # Access logs can be enabled later by adding an S3 bucket target here.
  # Disabled for the portfolio build to avoid the S3 cost.
}

resource "aws_lb_target_group" "app" {
  name        = "${local.name_prefix}-api-tg"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id

  health_check {
    path                = "/scim/v2/ServiceProviderConfig"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 10
    matcher             = "200"
  }

  deregistration_delay = 30
}

# Frontend target group — nginx on port 80, health-checked at root
resource "aws_lb_target_group" "ui" {
  name        = "${local.name_prefix}-ui-tg"
  port        = 80
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id

  health_check {
    path                = "/"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    matcher             = "200"
  }

  deregistration_delay = 30
}

# ── ALB listener — path-based routing ────────────────────────────────────────
# Default action forwards to the React frontend.
# An explicit listener rule (below) intercepts all backend paths first.
# Rule priority is evaluated lowest-number-first; default rule fires last.

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  # Default: serve the React SPA. The nginx SPA fallback config handles client-
  # side routes (React Router), so every path that isn't caught by the backend
  # rule below lands on index.html.
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.ui.arn
  }
}

# All backend-owned paths route to the Spring Boot service.
# A single rule with multiple path patterns uses OR semantics — any matching
# path triggers the forward. Update this list if new top-level paths are added
# to the backend (e.g. a future /admin/* endpoint).
resource "aws_lb_listener_rule" "backend_paths" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10

  condition {
    path_pattern {
      # AWS ALB path-pattern rules allow max 5 values per condition.
      # Trailing wildcards consolidate related paths:
      #   /swagger-ui*  covers /swagger-ui.html and /swagger-ui/**
      #   /v3/api-docs* covers /v3/api-docs and /v3/api-docs/**
      values = [
        "/scim/*",
        "/certifications/*",
        "/swagger-ui*",
        "/v3/api-docs*",
        "/actuator/*",
      ]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}
