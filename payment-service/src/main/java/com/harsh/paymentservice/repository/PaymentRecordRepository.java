package com.harsh.paymentservice.repository;

import com.harsh.paymentservice.model.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {
}
