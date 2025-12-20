package com.example.demo.dto;

import lombok.Data;

@Data
public class PaymentRequest {
    private String appointmentId;
    private long amount; // VNĐ
}