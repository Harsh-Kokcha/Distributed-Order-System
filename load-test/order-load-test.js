import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

/**
 * Load test for POST /orders.
 *
 * Run with: k6 run load-test/order-load-test.js
 * (install k6 first: https://k6.io/docs/get-started/installation)
 *
 * Before running:
 *   1. docker-compose up -d
 *   2. Seed enough stock that the test doesn't just measure rejection speed:
 *        curl -X POST localhost:8082/inventory/seed -H "Content-Type: application/json" \
 *          -d '{"productId": "load-test-sku", "quantity": 1000000}'
 *   3. Seed a funded account:
 *        curl -X POST localhost:8083/accounts/seed -H "Content-Type: application/json" \
 *          -d '{"customerId": "load-test-customer", "balance": 999999999}'
 *
 * This measures the ORDER CREATION endpoint's throughput/latency - i.e. how
 * fast order-service can accept requests, validate, persist, and publish the
 * Kafka event. It does NOT measure end-to-end saga completion time (inventory
 * + payment processing happen asynchronously after the response returns) -
 * that's a deliberate scope choice: request-accept latency is what a real
 * checkout API's SLA is usually measured against, since the client gets an
 * order ID back immediately and polls or gets notified for final status.
 */
export const options = {
    stages: [
        { duration: '10s', target: 20 },   // ramp up
        { duration: '30s', target: 50 },   // steady load
        { duration: '10s', target: 0 },    // ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<200', 'p(99)<500'],
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export default function () {
    const payload = JSON.stringify({
        idempotencyKey: `load-test-${randomString(16)}`,
        customerId: 'load-test-customer',
        productId: 'load-test-sku',
        quantity: 1,
        amount: 9.99,
    });

    const res = http.post(`${BASE_URL}/orders`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    check(res, {
        'status is 201': (r) => r.status === 201,
    });

    sleep(0.1);
}
