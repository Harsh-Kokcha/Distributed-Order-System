terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

variable "aws_region" {
  default = "ap-south-1"
}

variable "project_name" {
  default = "distributed-order-system"
}

variable "db_username" {
  default = "orderapp"
}

variable "db_password" {
  description = "Set via terraform.tfvars or TF_VAR_db_password env var - never commit this."
  sensitive   = true
}

# --- Networking: use the default VPC to keep this cheap and simple for a
# portfolio project. A production system would use a dedicated VPC with
# private subnets for RDS/ECS and a NAT gateway - noted in README as a
# deliberate scope trade-off, not an oversight. ---
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

resource "aws_security_group" "app" {
  name        = "${var.project_name}-app-sg"
  description = "Security group for ECS services, RDS, and Redis"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }

  ingress {
    from_port   = 8081
    to_port     = 8083
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"] # tighten to your IP / ALB SG in a real deployment
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
