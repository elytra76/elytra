package com.elytra.client.account;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Password hashing for local accounts: PBKDF2-HMAC-SHA256 with a random per-account
 * salt. Passwords are never written anywhere; only the salt and the derived key are.
 */
public final class PasswordHasher {

    /** OWASP's current floor for PBKDF2-HMAC-SHA256. Stored per account so it can be raised later. */
    public static final int ITERATIONS = 210_000;
    public static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static byte[] newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return salt;
    }

    /** Blocking and deliberately slow (~100-250 ms); never call this on the render thread. */
    public static byte[] hash(String password, byte[] salt, int iterations) throws GeneralSecurityException {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(chars, '\0');
        }
    }

    /** Constant-time comparison. */
    public static boolean matches(byte[] expected, byte[] actual) {
        return expected != null && actual != null && MessageDigest.isEqual(expected, actual);
    }
}
