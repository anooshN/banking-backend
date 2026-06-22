resource "aws_elasticache_subnet_group" "banking" {
  name       = "banking-redis-subnet"
  subnet_ids = module.vpc.private_subnets
}

resource "aws_security_group" "redis" {
  name_prefix = "banking-redis-"
  vpc_id      = module.vpc.vpc_id
  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }
}

resource "aws_elasticache_replication_group" "banking" {
  replication_group_id = "banking-redis"
  description          = "Banking App Redis Cluster"
  node_type            = "cache.r6g.large"
  num_cache_clusters   = 3
  port                 = 6379

  subnet_group_name  = aws_elasticache_subnet_group.banking.name
  security_group_ids = [aws_security_group.redis.id]

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  automatic_failover_enabled = true

  parameter_group_name = "default.redis7"
}
