package cc.techox.boardgame.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class HashUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String generateSalt(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return toHex(b);
    }

    public static String hashPassword(String raw) {
        String salt = generateSalt(8);
        String h = sha256Hex(salt + raw);
        return salt + ":" + h;
    }

    public static boolean verifyPassword(String raw, String stored) {
        if (stored == null || stored.isEmpty()) return false;
        if (stored.contains(":")) {
            String[] parts = stored.split(":", 2);
            String salt = parts[0];
            String h = parts[1];
            return sha256Hex(salt + raw).equalsIgnoreCase(h);
        } else {
            return sha256Hex(raw).equalsIgnoreCase(stored);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}