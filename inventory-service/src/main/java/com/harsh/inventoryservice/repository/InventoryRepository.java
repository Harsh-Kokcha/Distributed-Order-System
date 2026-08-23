package com.harsh.inventoryservice.repository;

import com.harsh.inventoryservice.model.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<InventoryItem, String> {
}
