resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.project_name}-redis-subnet"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_elasticache_cluster" "redis" {
  cluster_id           = "${var.project_name}-redis"
  engine               = "redis"
  engine_version       = "7.0"
  node_type            = "cache.t4g.micro" # cheapest node type, fine for a portfolio demo
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  subnet_group_name    = aws_elasticache_subnet_group.main.name
  security_group_ids   = [aws_security_group.app.id]

  tags = {
    Project = var.project_name
  }
}
