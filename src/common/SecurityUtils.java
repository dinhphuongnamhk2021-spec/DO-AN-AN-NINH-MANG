package common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class SecurityUtils {
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Tạo chuỗi Salt ngẫu nhiên
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Băm mật khẩu cùng với Salt bằng thuật toán SHA-256
     */
    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Lỗi thuật toán SHA-256", e);
        }
    }

    /**
     * Xác thực mật khẩu nhập vào
     */
    public static boolean verifyPassword(String inputPassword, String salt, String expectedHashed) {
        String actualHashed = hashPassword(inputPassword, salt);
        return actualHashed.equals(expectedHashed);
    }

    /**
     * Sinh Session Token ngẫu nhiên cho phòng chơi
     */
    public static String generateSessionToken() {
        byte[] token = new byte[24];
        RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
}
