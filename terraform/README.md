# AWS Deployment (Terraform)

This provisions RDS Postgres, ElastiCache Redis, MSK Serverless (Kafka), ECR
repositories, and an ECS Fargate cluster running all three services.

## Before you run this

**This costs real money and requires your own AWS account and credentials.**
I (Claude) cannot apply this for you — it has to be run from your machine
with your AWS credentials configured. Nothing in this repo has been deployed
yet; this is infrastructure-as-code ready to run, not a live system.

Rough always-on cost estimate at the sizes configured here (ap-south-1,
prices approximate and change over time — check the AWS pricing calculator
before applying):
- RDS db.t4g.micro: ~$12–15/month (or free if within your account's free-tier window)
- ElastiCache cache.t4g.micro: ~$12/month
- MSK Serverless: pay-per-request/storage, but budget a few dollars/month minimum even at low volume
- ECS Fargate (3 tasks, 0.5 vCPU/1GB each, running continuously): ~$30–40/month

**If you only want this for a demo/interview, destroy it right after**:
`terraform destroy` — don't leave it running 24/7 unless you want the bill.

## Steps

```bash
cd terraform

# 1. Set your DB password (never commit this)
export TF_VAR_db_password="choose-a-real-password"

# 2. Initialize and review the plan
terraform init
terraform plan

# 3. Apply
terraform apply

# 4. Create the remaining two databases on the RDS instance (RDS only lets
#    you set one db_name at creation)
psql -h $(terraform output -raw rds_endpoint) -U orderapp -d orderdb \
  -c "CREATE DATABASE inventorydb; CREATE DATABASE paymentdb;"

# 5. Build and push each service's image to its ECR repo
aws ecr get-login-password --region ap-south-1 | docker login --username AWS \
  --password-stdin <your-account-id>.dkr.ecr.ap-south-1.amazonaws.com

docker build -t order-service ../order-service
docker tag order-service:latest $(terraform output -json ecr_repository_urls | jq -r .order_service):latest
docker push $(terraform output -json ecr_repository_urls | jq -r .order_service):latest
# ... repeat for inventory-service and payment-service

# 6. Force ECS to pick up the newly pushed images
aws ecs update-service --cluster distributed-order-system-cluster \
  --service distributed-order-system-order-service --force-new-deployment
# ... repeat for the other two services

# 7. When you're done demoing it:
terraform destroy
```

## Known gap: MSK Serverless uses IAM auth, not PLAINTEXT

Local `docker-compose` Kafka uses PLAINTEXT (no auth) for simplicity. MSK
Serverless only supports IAM-based SASL auth. That means the Spring Kafka
producer/consumer configs need the `aws-msk-iam-auth` library and a
different security protocol when actually pointed at MSK — **this is not
yet wired into the Spring config in this repo**. That's the honest,
specific gap between "infra is provisioned" and "the app is actually
running against it." Documented here rather than silently glossed over.
