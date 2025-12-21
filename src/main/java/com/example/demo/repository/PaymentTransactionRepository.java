package com.example.demo.repository;

import com.example.demo.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, String> {

    Optional<PaymentTransaction>
    findTopByAppointmentIdOrderByCreatedAtDesc(String appointmentId);
}
