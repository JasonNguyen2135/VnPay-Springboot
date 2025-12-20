package com.example.demo.controller;

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

    /**
     * CALLBACK THẬT CỦA VNPAY
     * - Nhận GET + POST
     * - Verify hash
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

        // 1️⃣ Lấy toàn bộ params VNPay
        Map<String, String> vnpParams = new HashMap<>();
        request.getParameterMap().forEach(
                (k, v) -> vnpParams.put(k, v[0])
        );

        // 2️⃣ Tách secure hash
        String secureHash = vnpParams.remove("vnp_SecureHash");
        vnpParams.remove("vnp_SecureHashType");

        // 3️⃣ Build hash data + verify
        String hashData = buildHashData(vnpParams);
        String calculatedHash = hmacSHA512(vnpHashSecret, hashData);

        String txnRef = vnpParams.get("vnp_TxnRef");
        String responseCode = vnpParams.get("vnp_ResponseCode");

        boolean isSuccess =
                calculatedHash.equalsIgnoreCase(secureHash)
                        && "00".equals(responseCode);

        // 4️⃣ TODO: update DB / Firebase (nếu cần)
        // if (isSuccess) { update appointment = PAID }

        // 5️⃣ Redirect về Android App (KHÔNG cần frontend)
        String intentUrl =
                "intent://payment"
                        + "?status=" + (isSuccess ? "success" : "failed")
                        + "&appointmentId=" + txnRef
                        + "#Intent;scheme=umc;package=com.example.umc;end";

        response.sendRedirect(intentUrl);
    }

    // ======================
    // HELPERS
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
            sb.setLength(sb.length() - 1); // remove last &
        }

        return sb.toString();
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey =
                    new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKey);

            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
