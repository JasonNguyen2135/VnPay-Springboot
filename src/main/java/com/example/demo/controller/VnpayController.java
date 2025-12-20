package com.example.demo.controller;

import com.example.demo.dto.PaymentRequest;
import com.example.demo.service.VnpayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/vnpay")
public class VnpayController {

    private final VnpayService vnpayService;

    public VnpayController(VnpayService vnpayService) {
        this.vnpayService = vnpayService;
    }

    // Android gọi
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody PaymentRequest req) {

        String url = vnpayService.createPaymentUrl(
                req.getAppointmentId(),
                req.getAmount()
        );

        return ResponseEntity.ok(
                Map.of("paymentUrl", url)
        );
    }
}
