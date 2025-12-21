package com.example.demo.service;

import com.example.demo.entity.PaymentTransaction;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.util.VnpayUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class VnpayService {
    private final PaymentTransactionRepository repo;
    public VnpayService(PaymentTransactionRepository repo) {
        this.repo = repo;
    }

    @Value("${vnpay.tmnCode}")
    private String tmnCode;

    @Value("${vnpay.hashSecret}")
    private String hashSecret;

    @Value("${vnpay.payUrl}")
    private String payUrl;

    @Value("${vnpay.returnUrl}")
    private String returnUrl;

    public String createPaymentUrl(String appointmentId, long amount) {

        // 1️⃣ Check trạng thái cũ
        repo.findTopByAppointmentIdOrderByCreatedAtDesc(appointmentId)
                .ifPresent(tx -> {
                    if ("SUCCESS".equals(tx.getStatus())) {
                        throw new RuntimeException("Appointment already paid");
                    }
                });

        // 2️⃣ TẠO PAYMENT ID MỚI (CỰC KỲ QUAN TRỌNG)
        String paymentId =
                appointmentId + "_" + System.currentTimeMillis();

        // 3️⃣ Lưu H2: PENDING
        PaymentTransaction tx = new PaymentTransaction();
        tx.setPaymentId(paymentId);
        tx.setAppointmentId(appointmentId);
        tx.setAmount(amount);
        tx.setStatus("PENDING");
        tx.setCreatedAt(LocalDateTime.now());
        repo.save(tx);

        // 4️⃣ Tạo params VNPay
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(amount * 100));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", paymentId); // ✅ ĐÃ FIX
        params.put("vnp_OrderInfo", "Thanh toan lich kham " + appointmentId);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", "127.0.0.1");

        params.put(
                "vnp_CreateDate",
                new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
        );

        String query = VnpayUtil.buildQuery(params);
        String hash = VnpayUtil.hmacSHA512(hashSecret, query);

        return payUrl + "?" + query + "&vnp_SecureHash=" + hash;
    }

}