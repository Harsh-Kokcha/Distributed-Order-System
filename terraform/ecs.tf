resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"
}

resource "aws_iam_role" "ecs_task_execution" {
  name = "${var.project_name}-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# MSK Serverless requires IAM auth from the client side too - the task role
# (not just the execution role) needs permission to connect/read/write.
resource "aws_iam_role_policy" "msk_access" {
  name = "${var.project_name}-msk-access"
  role = aws_iam_role.ecs_task_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "kafka-cluster:Connect",
        "kafka-cluster:DescribeCluster",
        "kafka-cluster:AlterCluster",
        "kafka-cluster:ReadData",
        "kafka-cluster:WriteData",
        "kafka-cluster:CreateTopic",
        "kafka-cluster:DescribeTopic"
      ]
      Resource = "*"
    }]
  })
}

locals {
  services = {
    order-service = {
      image_repo = aws_ecr_repository.order_service.repository_url
      port       = 8081
      db_name    = "orderdb"
    }
    inventory-service = {
      image_repo = aws_ecr_repository.inventory_service.repository_url
      port       = 8082
      db_name    = "inventorydb"
    }
    payment-service = {
      image_repo = aws_ecr_repository.payment_service.repository_url
      port       = 8083
      db_name    = "paymentdb"
    }
  }
}

resource "aws_ecs_task_definition" "service" {
  for_each                 = local.services
  family                   = "${var.project_name}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task_execution.arn

  container_definitions = jsonencode([{
    name  = each.key
    image = "${each.value.image_repo}:latest"
    portMappings = [{
      containerPort = each.value.port
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/${each.value.db_name}" },
      { name = "SPRING_DATASOURCE_USERNAME", value = var.db_username },
      { name = "SPRING_DATASOURCE_PASSWORD", value = var.db_password },
      { name = "SPRING_KAFKA_BOOTSTRAP_SERVERS", value = aws_msk_serverless_cluster.kafka.bootstrap_brokers_sasl_iam },
      { name = "SPRING_DATA_REDIS_HOST", value = aws_elasticache_cluster.redis.cache_nodes[0].address },
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = "/ecs/${var.project_name}-${each.key}"
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = each.key
        "awslogs-create-group"  = "true"
      }
    }
  }])
}

resource "aws_ecs_service" "service" {
  for_each        = local.services
  name            = "${var.project_name}-${each.key}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.service[each.key].arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = true # simplifies this for a demo; a real deployment would sit behind an ALB in private subnets
  }
}
