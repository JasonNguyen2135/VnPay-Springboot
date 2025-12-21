package com.example.demo.controller;

import com.example.demo.entity.PaymentTransaction;
import com.example.demo.repository.PaymentTransactionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/vnpay")
public class VnPayCallbackController {

    @Value("${vnpay.hashSecret}")
    private String vnpHashSecret;

    private final PaymentTransactionRepository paymentRepo;

    public VnPayCallbackController(PaymentTransactionRepository paymentRepo) {
        this.paymentRepo = paymentRepo;
    }

    /**
     * CALLBACK TỪ VNPAY
     * - Nhận GET + POST
     * - Verify chữ ký
     * - Update H2 payment status
     * - Redirect về Android bằng intent://
     */
    @RequestMapping(
            value = "/callback",
            method = {RequestMethod.GET, RequestMethod.POST}
    )
    public void handleCallback(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        // 1️⃣ Lấy toàn bộ params từ VNPay
        Map<String, String> vnpParams = new HashMap<>();
        request.getParameterMap().forEach(
                (k, v) -> vnpParams.put(k, v[0])
        );

        // 2️⃣ Lấy & remove hash
        String secureHash = vnpParams.remove("vnp_SecureHash");
        vnpParams.remove("vnp_SecureHashType");

        // 3️⃣ Verify chữ ký
        String hashData = buildHashData(vnpParams);
        String calculatedHash = hmacSHA512(vnpHashSecret, hashData);

        String paymentId = vnpParams.get("vnp_TxnRef");
        String responseCode = vnpParams.get("vnp_ResponseCode");

        boolean isSuccess =
                calculatedHash.equalsIgnoreCase(secureHash)
                        && "00".equals(responseCode);

        String appointmentId = null;

        // 4️⃣ Update H2 payment transaction
        if (paymentId != null) {
            Optional<PaymentTransaction> opt =
                    paymentRepo.findById(paymentId);

            if (opt.isPresent()) {
                PaymentTransaction tx = opt.get();
                appointmentId = tx.getAppointmentId();

                if (isSuccess) {
                    tx.setStatus("SUCCESS");
                    // TODO: update Firebase appointment -> BOOKED + paid=true
                } else {
                    tx.setStatus("FAILED");
                }
                paymentRepo.save(tx);
            }
        }

        if (appointmentId == null) {
            appointmentId = "unknown";
        }

        // 5️⃣ Redirect về Android (deep link)
        String intentUrl =
                "intent://payment"
                        + "?status=" + (isSuccess ? "success" : "failed")
                        + "&appointmentId=" + appointmentId
                        + "#Intent;scheme=umc;package=com.example.umc;end";

        response.sendRedirect(intentUrl);
    }

    // ======================
    // HELPER METHODS
    // ======================

    private String buildHashData(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.isEmpty()) {
                sb.append(key)
                        .append("=")
                        .append(URLEncoder.encode(value, StandardCharsets.US_ASCII))
                        .append("&");
            }
        }

        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey =
                    new SecretKeySpec(
                            key.getBytes(StandardCharsets.UTF_8),
                            "HmacSHA512"
                    );
            mac.init(secretKey);

            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("VNPay hash error", e);
        }
    }
}
