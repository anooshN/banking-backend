resource "aws_db_subnet_group" "banking" {
  name       = "banking-db-subnet-group"
  subnet_ids = module.vpc.private_subnets
}

resource "aws_security_group" "rds" {
  name_prefix = "banking-rds-"
  vpc_id      = module.vpc.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }
}

# Auth DB
resource "aws_rds_cluster" "auth" {
  cluster_identifier     = "banking-auth-cluster"
  engine                 = "aurora-postgresql"
  engine_version         = "15.4"
  database_name          = "banking_auth"
  master_username        = "banking"
  master_password        = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.banking.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  storage_encrypted      = true
  deletion_protection    = true
  backup_retention_period = 7
  skip_final_snapshot    = false
  final_snapshot_identifier = "banking-auth-final-snapshot"
}

resource "aws_rds_cluster_instance" "auth" {
  count              = 2
  identifier         = "banking-auth-${count.index}"
  cluster_identifier = aws_rds_cluster.auth.id
  instance_class     = var.db_instance_class
  engine             = aws_rds_cluster.auth.engine
}

# Accounts DB
resource "aws_rds_cluster" "accounts" {
  cluster_identifier     = "banking-accounts-cluster"
  engine                 = "aurora-postgresql"
  engine_version         = "15.4"
  database_name          = "banking_accounts"
  master_username        = "banking"
  master_password        = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.banking.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  storage_encrypted      = true
  deletion_protection    = true
  backup_retention_period = 7
  skip_final_snapshot    = false
  final_snapshot_identifier = "banking-accounts-final-snapshot"
}

resource "aws_rds_cluster_instance" "accounts" {
  count              = 2
  identifier         = "banking-accounts-${count.index}"
  cluster_identifier = aws_rds_cluster.accounts.id
  instance_class     = var.db_instance_class
  engine             = aws_rds_cluster.accounts.engine
}
