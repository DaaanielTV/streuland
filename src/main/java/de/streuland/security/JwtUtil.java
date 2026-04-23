package de.streuland.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class JwtUtil {
    public static String createToken(String subject, long ttlMillis, String secret) {
        long now = System.currentTimeMillis();
        long exp = ttlMillis > 0 ? now + ttlMillis : now + 24L * 60 * 60 * 1000; // default 24h
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + escape(subject) + "\", \"exp\":" + (exp / 1000) + "}");
        String unsigned = header + "." + payload;
        String signature = hmacSha256(unsigned, secret);
        return unsigned + "." + signature;
    }

    private static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return base64UrlBytes(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }

    private static String base64Url(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64UrlBytes(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String escape(String s) {
        return s == null ? "" : s;
    }
}
