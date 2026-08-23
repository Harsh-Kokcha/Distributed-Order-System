package com.harsh.paymentservice.controller;

import com.harsh.paymentservice.model.Account;
import com.harsh.paymentservice.repository.AccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public record SeedRequest(String customerId, BigDecimal balance) {}

    /** Seed or top up a customer's balance. Used for local testing / demo setup. */
    @PostMapping("/seed")
    public ResponseEntity<Account> seed(@RequestBody SeedRequest request) {
        Account account = new Account(request.customerId(), request.balance());
        return ResponseEntity.ok(accountRepository.save(account));
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<Account> get(@PathVariable String customerId) {
        Account account = accountRepository.findById(customerId)
                .orElseThrow(() -> new NoSuchElementException("Unknown customer: " + customerId));
        return ResponseEntity.ok(account);
    }
}
