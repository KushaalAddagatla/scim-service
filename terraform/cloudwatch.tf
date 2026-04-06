# ECS routes container stdout/stderr here via the awslogs log driver.
# The log group name is referenced in the task definition's logConfiguration.
resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/${local.name_prefix}"
  retention_in_days = 30   # 30 days covers a typical audit window; reduce for cost savings
}

# ── Optional: CloudWatch alarm on ECS task restarts ───────────────────────────
# Uncomment once you want alerting. Fires when ECS restarts a task more than
# once in 5 minutes (indicates a crash loop).
#
# resource "aws_cloudwatch_metric_alarm" "ecs_task_restarts" {
#   alarm_name          = "${local.name_prefix}-task-restarts"
#   comparison_operator = "GreaterThanThreshold"
#   evaluation_periods  = 1
#   metric_name         = "RunningTaskCount"
#   namespace           = "ECS/ContainerInsights"
#   period              = 300
#   statistic           = "SampleCount"
#   threshold           = 1
#   alarm_description   = "ECS task restarted — possible crash loop"
#   dimensions = {
#     ClusterName = aws_ecs_cluster.main.name
#     ServiceName = aws_ecs_service.main.name
#   }
# }
