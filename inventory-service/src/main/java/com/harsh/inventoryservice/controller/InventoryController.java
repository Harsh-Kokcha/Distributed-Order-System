package com.harsh.inventoryservice.controller;

import com.harsh.inventoryservice.model.InventoryItem;
import com.harsh.inventoryservice.repository.InventoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryRepository inventoryRepository;

    public InventoryController(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public record SeedRequest(String productId, int quantity) {}

    /** Seed or top up stock for a product. Used for local testing / demo setup, not part of the order flow. */
    @PostMapping("/seed")
    public ResponseEntity<InventoryItem> seed(@RequestBody SeedRequest request) {
        InventoryItem item = inventoryRepository.findById(request.productId())
                .orElse(new InventoryItem(request.productId(), 0));
        InventoryItem updated = new InventoryItem(request.productId(), request.quantity());
        return ResponseEntity.ok(inventoryRepository.save(updated));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryItem> get(@PathVariable String productId) {
        InventoryItem item = inventoryRepository.findById(productId)
                .orElseThrow(() -> new NoSuchElementException("Unknown product: " + productId));
        return ResponseEntity.ok(item);
    }
}
