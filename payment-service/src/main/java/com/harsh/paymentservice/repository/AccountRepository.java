package com.harsh.paymentservice.repository;

import com.harsh.paymentservice.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
}
