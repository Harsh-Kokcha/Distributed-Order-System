package com.harsh.paymentservice.service;

import com.harsh.paymentservice.model.Account;
import com.harsh.paymentservice.model.PaymentOutcome;
import com.harsh.paymentservice.model.PaymentRecord;
import com.harsh.paymentservice.repository.AccountRepository;
import com.harsh.paymentservice.repository.PaymentRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Debit + payment-record write happen inside one @Transactional method, so
 * either both commit or neither does. That's what closes the double-charge
 * window: if the process dies after the debit commits but before the Kafka
 * publish, the record is already there, so a redelivery of the same
 * message finds it in PaymentEventConsumer and replays the recorded
 * outcome instead of debiting again.
 */
@Service
public class PaymentProcessingService {

    private final AccountRepository accountRepository;
    private final PaymentRecordRepository paymentRecordRepository;

    public PaymentProcessingService(AccountRepository accountRepository, PaymentRecordRepository paymentRecordRepository) {
        this.accountRepository = accountRepository;
        this.paymentRecordRepository = paymentRecordRepository;
    }

    public Optional<PaymentRecord> findExisting(UUID orderId) {
        return paymentRecordRepository.findById(orderId);
    }

    /** Throws OptimisticLockingFailureException on a concurrent-write conflict; caller retries. */
    @Transactional
    public PaymentRecord attemptCharge(UUID orderId, String customerId, BigDecimal amount) {
        Optional<Account> accountOpt = accountRepository.findById(customerId);
        if (accountOpt.isEmpty()) {
            return recordRejection(orderId, "Unknown customer: " + customerId);
        }

        Account account = accountOpt.get();
        if (!account.debit(amount)) {
            return recordRejection(orderId, "Insufficient funds");
        }

        accountRepository.save(account);
        return paymentRecordRepository.save(new PaymentRecord(orderId, PaymentOutcome.CONFIRMED, "Charged " + amount));
    }

    @Transactional
    public PaymentRecord recordRejection(UUID orderId, String reason) {
        return paymentRecordRepository.save(new PaymentRecord(orderId, PaymentOutcome.REJECTED, reason));
    }
}
