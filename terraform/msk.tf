# MSK Serverless instead of a provisioned MSK cluster or self-hosting Kafka
# on EC2. Chosen deliberately: provisioned MSK bills per-broker-hour even
# when idle (expensive for a portfolio project that isn't running 24/7),
# and self-hosting on EC2 means you're now also responsible for patching/
# operating Kafka yourself, which isn't the point of this project. MSK
# Serverless bills per request/storage instead, which is much cheaper for
# something like a resume demo you spin up occasionally.
#
# Trade-off worth knowing for an interview: MSK Serverless only supports
# IAM authentication (no PLAINTEXT), so the Spring Kafka client config needs
# the aws-msk-iam-auth library and a SASL_SSL security protocol - different
# from the PLAINTEXT setup used in local docker-compose. This is called out
# explicitly rather than glossed over, since it's a real config difference
# between local dev and this AWS deployment.
resource "aws_security_group" "msk" {
  name   = "${var.project_name}-msk-sg"
  vpc_id = data.aws_vpc.default.id

  ingress {
    from_port       = 9098 # MSK Serverless IAM auth port
    to_port         = 9098
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_msk_serverless_cluster" "kafka" {
  cluster_name = "${var.project_name}-kafka"

  vpc_config {
    subnet_ids         = data.aws_subnets.default.ids
    security_group_ids = [aws_security_group.msk.id]
  }

  client_authentication {
    sasl {
      iam {
        enabled = true
      }
    }
  }

  tags = {
    Project = var.project_name
  }
}
