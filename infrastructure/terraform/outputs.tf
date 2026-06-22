output "cluster_endpoint" {
  value = module.eks.cluster_endpoint
}
output "cluster_name" {
  value = module.eks.cluster_name
}
output "auth_db_endpoint" {
  value     = aws_rds_cluster.auth.endpoint
  sensitive = true
}
output "accounts_db_endpoint" {
  value     = aws_rds_cluster.accounts.endpoint
  sensitive = true
}
output "redis_endpoint" {
  value     = aws_elasticache_replication_group.banking.primary_endpoint_address
  sensitive = true
}
output "kafka_bootstrap_brokers_tls" {
  value     = aws_msk_cluster.banking.bootstrap_brokers_tls
  sensitive = true
}
