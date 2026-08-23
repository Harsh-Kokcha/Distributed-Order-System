output "rds_endpoint" {
  value = aws_db_instance.postgres.address
}

output "redis_endpoint" {
  value = aws_elasticache_cluster.redis.cache_nodes[0].address
}

output "msk_bootstrap_brokers" {
  value = aws_msk_serverless_cluster.kafka.bootstrap_brokers_sasl_iam
}

output "ecr_repository_urls" {
  value = {
    order_service     = aws_ecr_repository.order_service.repository_url
    inventory_service = aws_ecr_repository.inventory_service.repository_url
    payment_service   = aws_ecr_repository.payment_service.repository_url
  }
}
