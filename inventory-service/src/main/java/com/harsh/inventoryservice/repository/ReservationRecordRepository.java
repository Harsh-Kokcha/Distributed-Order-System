package com.harsh.inventoryservice.repository;

import com.harsh.inventoryservice.model.ReservationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReservationRecordRepository extends JpaRepository<ReservationRecord, UUID> {
}
