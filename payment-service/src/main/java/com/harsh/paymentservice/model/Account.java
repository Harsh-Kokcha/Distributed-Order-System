package com.harsh.paymentservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "customer_id")
    private String customerId;

    @Column(nullable = false)
    private BigDecimal balance;

    // Optimistic locking: if two payment attempts for the same customer
    // race each other, the second save() will throw
    // OptimisticLockingFailureException instead of silently overwriting
    // the first debit. This is the DB-level equivalent of the Redis lock
    // used in inventory-service - shown here as an alternative approach
    // worth being able to compare in an interview.
    @Version
    private Long version;

    protected Account() {
        // JPA
    }

    public Account(String customerId, BigDecimal balance) {
        this.customerId = customerId;
        this.balance = balance;
    }

    public boolean debit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            return false;
        }
        balance = balance.subtract(amount);
        return true;
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public String getCustomerId() { return customerId; }
    public BigDecimal getBalance() { return balance; }
}
