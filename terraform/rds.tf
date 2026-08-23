# Single RDS instance hosting all three services' databases, matching the
# docker-compose setup (init-multiple-dbs.sh creates orderdb/inventorydb/
# paymentdb). A stricter "true" microservices deployment would use 3 separate
# RDS instances so services can't accidentally reach each other's schema -
# noted as a next-step hardening item, not done here to keep AWS cost low
# for a portfolio project.
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_db_instance" "postgres" {
  identifier             = "${var.project_name}-postgres"
  engine                 = "postgres"
  engine_version         = "16"
  instance_class         = "db.t4g.micro" # free-tier eligible
  allocated_storage      = 20
  username               = var.db_username
  password               = var.db_password
  db_name                = "orderdb"
  vpc_security_group_ids = [aws_security_group.app.id]
  db_subnet_group_name   = aws_db_subnet_group.main.name
  publicly_accessible    = false
  skip_final_snapshot    = true

  tags = {
    Project = var.project_name
  }
}

# inventorydb and paymentdb are created by a one-off connection after the
# instance is up (RDS only lets you set one db_name at creation time):
#   psql -h <rds-endpoint> -U orderapp -d orderdb -c "CREATE DATABASE inventorydb;"
#   psql -h <rds-endpoint> -U orderapp -d orderdb -c "CREATE DATABASE paymentdb;"
