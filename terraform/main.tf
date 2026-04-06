terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state — keeps the state file out of your laptop and enables team
  # collaboration. Bootstrap: apply once without this block to create the S3
  # bucket and DynamoDB table, then uncomment and run `terraform init -migrate-state`.
  #
  # backend "s3" {
  #   bucket         = "scim-service-terraform-state"
  #   key            = "prod/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "scim-service-terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  # default_tags propagates to every resource that supports tags.
  # This is how you satisfy "tag everything for cost allocation" in a real
  # AWS account without repeating tags = { ... } on every resource block.
  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# ── Optional: remote state storage ───────────────────────────────────────────
# Uncomment these two resources, apply, then enable the backend block above.

# resource "aws_s3_bucket" "terraform_state" {
#   bucket = "${var.project_name}-terraform-state"
# }
#
# resource "aws_s3_bucket_versioning" "terraform_state" {
#   bucket = aws_s3_bucket.terraform_state.id
#   versioning_configuration { status = "Enabled" }
# }
#
# resource "aws_dynamodb_table" "terraform_locks" {
#   name         = "${var.project_name}-terraform-locks"
#   billing_mode = "PAY_PER_REQUEST"
#   hash_key     = "LockID"
#   attribute { name = "LockID"; type = "S" }
# }

# ── Shared locals ─────────────────────────────────────────────────────────────
# name_prefix keeps every resource name consistent and makes it easy to
# identify which stack a resource belongs to in a multi-account AWS org.

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

data "aws_availability_zones" "available" {
  state = "available"
}
