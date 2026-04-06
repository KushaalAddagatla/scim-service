resource "aws_ecr_repository" "main" {
  name                 = var.project_name
  image_tag_mutability = "MUTABLE"   # SHA tags are immutable in practice; MUTABLE allows :latest overwrites

  image_scanning_configuration {
    scan_on_push = true   # free basic vulnerability scan on every push
  }
}

# Lifecycle policy — keep the 10 most recent images per tag prefix.
# Without this, ECR storage grows unbounded. At $0.10/GB/month it adds up
# when CI pushes multiple times per day.
resource "aws_ecr_lifecycle_policy" "main" {
  repository = aws_ecr_repository.main.name

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
