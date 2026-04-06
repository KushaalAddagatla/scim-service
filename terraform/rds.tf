# RDS requires a subnet group that spans at least two AZs even when
# multi_az = false. The group defines the pool of subnets RDS may use;
# the actual instance lands in one of them.
resource "aws_db_subnet_group" "main" {
  name        = "${local.name_prefix}-db-subnet-group"
  subnet_ids  = aws_subnet.private[*].id
  description = "Private subnets for RDS - not reachable from the internet"
}

resource "aws_db_instance" "main" {
  identifier = "${local.name_prefix}-postgres"

  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db_password.result

  # Store the password in Secrets Manager (secrets.tf) rather than here.
  # The `password` argument above is used by RDS at creation time; Terraform
  # stores it in state. For a production setup, use manage_master_user_password
  # (RDS-native rotation) instead.

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  # single-AZ saves ~$15/month on a t3.micro; enable multi_az for any
  # environment that needs failover SLA.
  multi_az            = false
  publicly_accessible = false

  allocated_storage     = 20
  max_allocated_storage = 100   # autoscaling up to 100 GB, billing is per GB used

  backup_retention_period = 7
  skip_final_snapshot     = true   # set to false and add final_snapshot_identifier for prod

  # Flyway runs migrations on app startup, so the DB just needs to exist with
  # the correct name. No manual schema setup required.
}
