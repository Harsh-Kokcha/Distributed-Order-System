resource "aws_ecr_repository" "order_service" {
  name                 = "${var.project_name}/order-service"
  image_tag_mutability = "MUTABLE"
}

resource "aws_ecr_repository" "inventory_service" {
  name                 = "${var.project_name}/inventory-service"
  image_tag_mutability = "MUTABLE"
}

resource "aws_ecr_repository" "payment_service" {
  name                 = "${var.project_name}/payment-service"
  image_tag_mutability = "MUTABLE"
}

# Push images with (repeat per service):
#   aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin <account-id>.dkr.ecr.<region>.amazonaws.com
#   docker build -t order-service ./order-service
#   docker tag order-service:latest <account-id>.dkr.ecr.<region>.amazonaws.com/distributed-order-system/order-service:latest
#   docker push <account-id>.dkr.ecr.<region>.amazonaws.com/distributed-order-system/order-service:latest
