# Distributed Order Processing System

A microservices-based order processing pipeline implementing the **SAGA pattern**
for distributed transactions, with real concurrency-safety guarantees under
load. Built to mirror how systems like e-commerce checkout or payment
processing actually work: multiple independent services that must stay
consistent *without* a single database transaction spanning all of them.

## Why this exists

A normal CRUD app reads and writes one database. This system solves a
different problem: **inventory**, **payment**, and **order status** are three
separate services with three separate databases (true database-per-service).
When you place an order, there is no single transaction that can atomically
reserve stock AND charge the customer AND mark the order complete — because
in the real world those are often genuinely separate systems. So the
interesting engineering here isn't the CRUD, it's:

- What happens if payment fails *after* inventory was already reserved?
  (→ compensating rollback, the actual SAGA pattern)
- What happens if a client retries the same request because it timed out?
  (→ idempotency keys)
- What happens when two orders want the last unit of the same product at
  the same instant? (→ distributed locking, proven under real concurrent load
  in `InventoryConcurrencyTest`)

## Architecture

```
                    POST /orders
                        |
                        v
                +----------------+
                |  order-service  |  <-- owns the SAGA state machine
                |   (port 8081)   |      PENDING -> INVENTORY_RESERVED
                +--------+--------+              -> PAYMENT_CONFIRMED
                         |                        -> COMPLETED
                 publishes OrderCreatedEvent
                         |
                         v
              Kafka topic: order-created
                         |
                         v
              +--------------------+
              | inventory-service   |  <-- Redis distributed lock guards
              |    (port 8082)      |      each product during reservation
              +----------+----------+
                         |
           reserved? --- Kafka: inventory-reserved --- rejected? --- Kafka: inventory-rejected
                         |                                                  |
                         v                                                  v
              +-------------------+                                (back to order-service,
              |  payment-service   |  <-- optimistic locking (@Version)     order marked
              |   (port 8083)      |      + retry on conflict               INVENTORY_REJECTED)
              +---------+----------+
                        |
         confirmed? --- Kafka: payment-confirmed --- rejected? --- Kafka: payment-rejected
                        |                                                |
                        v                                                v
              order marked COMPLETED                      order-service publishes
                                                            OrderRolledBackEvent
                                                                    |
                                                                    v
                                                     inventory-service releases
                                                     the stock it reserved earlier
                                                     (the compensating transaction)
```

Each service has its own Postgres database and its own copy of the Kafka
event classes (no shared library) — this is intentional. It keeps the
services independently deployable at the cost of some duplication, which is
the standard trade-off in real microservice architectures.

## Concurrency safety — two different approaches, on purpose

| Service | Mechanism | Why |
|---|---|---|
| inventory-service | Redis distributed lock (SET NX PX + Lua-script safe unlock) | Conflicts here are common (many orders can compete for one popular product), so blocking upfront is worth it |
| payment-service | JPA optimistic locking (`@Version`) + retry loop | Conflicts here are rare (one customer rarely has two payments in flight at once), so letting both proceed and retrying the loser is cheaper |

Having *both* in one project is a deliberate choice — it's a natural thing to
walk an interviewer through: "here's when I'd reach for a lock vs. when I'd
reach for optimistic concurrency control, and why."

## Fault tolerance: dead-letter topics

Every Kafka listener retries a failing message 3 times (1s apart) before
giving up and publishing the raw record to a `<topic>.DLT` topic instead of
either blocking the partition forever or silently dropping it. An operator
can inspect `order-created.DLT`, `inventory-reserved.DLT`, etc. and decide
whether to replay or discard. Configured in each service's
`KafkaConsumerConfig`.

## Caching: Redis cache-aside on `GET /orders/{id}`

`OrderQueryService` checks Redis first, falls back to Postgres on a miss,
and populates the cache on the way back (30s TTL as a safety net). The part
that actually matters is invalidation: every status transition in
`OrderEventConsumer` explicitly evicts that order's cache entry, so a client
polling for status never sees a stale result — eviction-on-write rather than
relying on the TTL alone.

## Load testing

`load-test/order-load-test.js` is a k6 script hitting `POST /orders` with
ramping concurrent load (20 → 50 virtual users) and asserting p95 < 200ms,
p99 < 500ms. Run it yourself with `k6 run load-test/order-load-test.js`
(setup steps are in the script's header comment) and use the real numbers
it prints for your resume bullet — don't quote a number until you've
actually run it once.

## AWS deployment

Terraform in `/terraform` provisions RDS Postgres, ElastiCache Redis, MSK
Serverless (Kafka), ECR repos, and an ECS Fargate cluster. **This is
infrastructure-as-code, not a live deployment** — see `terraform/README.md`
for cost estimates, exact steps, and one known gap (MSK Serverless needs IAM
auth wiring in the Spring Kafka config that isn't done yet). Run it
yourself with your own AWS credentials; I can't apply AWS changes from here.

## Running it locally

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Build and run each service (separate terminals)
cd order-service && mvn spring-boot:run
cd inventory-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run

# 3. Seed a product and a customer account
curl -X POST localhost:8082/inventory/seed -H "Content-Type: application/json" \
  -d '{"productId": "sku-123", "quantity": 10}'

curl -X POST localhost:8083/accounts/seed -H "Content-Type: application/json" \
  -d '{"customerId": "cust-1", "balance": 500.00}'

# 4. Place an order
curl -X POST localhost:8081/orders -H "Content-Type: application/json" \
  -d '{
        "idempotencyKey": "demo-key-1",
        "customerId": "cust-1",
        "productId": "sku-123",
        "quantity": 2,
        "amount": 49.99
      }'

# 5. Check the order status - should progress to COMPLETED within a second or two
curl localhost:8081/orders/{id}

# 6. Retry the SAME request with the same idempotencyKey - proves idempotency
#    (returns the existing order instead of creating a duplicate)
curl -X POST localhost:8081/orders -H "Content-Type: application/json" \
  -d '{ "idempotencyKey": "demo-key-1", ... same body ... }'
```

## Running the concurrency stress test

```bash
docker-compose up -d redis   # only Redis is needed for this test
cd inventory-service
mvn test -Dtest=InventoryConcurrencyTest
```

This fires 50 concurrent reservation attempts at a product with only 10
units of stock, and asserts exactly 10 succeed and 40 are correctly
rejected — proving the lock prevents overselling under real concurrent load.
The test prints the actual elapsed time for the run.

## Honest project status / roadmap

Everything above is real and working (code-complete — see the "not yet run"
caveat below). What's explicitly **not** done:

- [ ] The k6 load test has been written but not yet run — don't put a
      throughput/latency number on your resume until you've actually
      executed it and gotten a real one
- [ ] Terraform is written but not yet applied — nothing is deployed to AWS
      until you run `terraform apply` yourself
- [ ] MSK Serverless IAM auth is not yet wired into the Spring Kafka client
      config (works with local PLAINTEXT Kafka; needs `aws-msk-iam-auth`
      library + SASL config to actually talk to the deployed MSK cluster)
- [ ] Retry-with-backoff on lock contention instead of immediate rejection
      (currently rejects immediately, which is simpler but less resilient)
- [ ] A proper orchestrator-style saga (currently choreography-based —
      services react to each other's events rather than a central
      coordinator driving the flow) — both are valid patterns, worth
      comparing in an interview
- [ ] Centralized distributed tracing (correlation IDs across services,
      something like Zipkin) — logs currently live independently per service

## Tech stack

Java 17, Spring Boot 3.3, Spring Kafka, Spring Data JPA, PostgreSQL, Redis,
Docker + Docker Compose, JUnit 5, k6 (load testing), Terraform + AWS
(RDS, ElastiCache, MSK Serverless, ECS Fargate, ECR).
