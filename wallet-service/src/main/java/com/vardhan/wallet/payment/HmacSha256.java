package com.vardhan.wallet.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 as lowercase hex: the scheme payment gateways use to sign webhooks and checkout results.
 */
public final class HmacSha256 {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSha256() {
    }

    public static String hex(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /**
     * Constant-time comparison, so an attacker cannot learn the correct signature byte by byte
     * from how long a rejection takes.
     */
    public static boolean matches(String secret, byte[] data, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        byte[] expected = hex(secret, data).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.trim().getBytes(StandardCharsets.UTF_8));
    }
}
