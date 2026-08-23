package com.harsh.inventoryservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    protected InventoryItem() {
        // JPA
    }

    public InventoryItem(String productId, int totalQuantity) {
        this.productId = productId;
        this.totalQuantity = totalQuantity;
        this.reservedQuantity = 0;
    }

    public int available() {
        return totalQuantity - reservedQuantity;
    }

    public boolean reserve(int quantity) {
        if (available() < quantity) {
            return false;
        }
        this.reservedQuantity += quantity;
        return true;
    }

    public void release(int quantity) {
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
    }

    public String getProductId() { return productId; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
}
