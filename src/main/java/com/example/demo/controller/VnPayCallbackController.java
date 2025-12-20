package com.example.demo.controller;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    private static final String VNP_HASH_SECRET = "YOUR_VNP_HASH_SECRET";

    @GetMapping("/callback")
    public void handleCallback(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        Map<String, String> vnpParams = new HashMap<>();
        request.getParameterMap().forEach(
                (k, v) -> vnpParams.put(k, v[0])
        );

        // 🔐 Remove secure hash
        String secureHash = vnpParams.remove("vnp_SecureHash");
        vnpParams.remove("vnp_SecureHashType");

        // 🔐 Build hash data
        String hashData = buildHashData(vnpParams);
        String calculatedHash = hmacSHA512(VNP_HASH_SECRET, hashData);

        String txnRef = vnpParams.get("vnp_TxnRef");
        String responseCode = vnpParams.get("vnp_ResponseCode");

        String deepLink;

        if (calculatedHash.equalsIgnoreCase(secureHash)
                && "00".equals(responseCode)) {

            // ✅ SUCCESS
            deepLink = "umc://payment?status=success&appointmentId=" + txnRef;

            // TODO:
            // - update DB appointment = PAID
            // - update Firebase if needed

        } else {
            // ❌ FAILED
            deepLink = "umc://payment?status=failed&appointmentId=" + txnRef;
        }

        response.sendRedirect(deepLink);
    }

    // ======================
    // HELPERS
    // ======================

    private String buildHashData(Map<String, String> params) {
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
        sb.deleteCharAt(sb.length() - 1);
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
