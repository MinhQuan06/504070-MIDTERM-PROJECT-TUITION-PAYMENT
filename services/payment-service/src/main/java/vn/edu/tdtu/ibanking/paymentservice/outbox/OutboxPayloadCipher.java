package vn.edu.tdtu.ibanking.paymentservice.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class OutboxPayloadCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public OutboxPayloadCipher(@Value("${app.outbox.encryption-key}") String encodedKey) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encodedKey);
            if (bytes.length != 32) throw new IllegalArgumentException();
            key = new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("OUTBOX_ENCRYPTION_KEY must be Base64 of 32 random bytes");
        }
    }
    public String encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] packed = Arrays.copyOf(iv, iv.length + encrypted.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception error) { throw new IllegalStateException("OUTBOX_ENCRYPT_FAILED"); }
    }
    public byte[] decrypt(String encoded) {
        try {
            byte[] packed = Base64.getDecoder().decode(encoded);
            if (packed.length < 28) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(packed, 12)));
            return cipher.doFinal(packed, 12, packed.length - 12);
        } catch (Exception error) { throw new IllegalStateException("OUTBOX_DECRYPT_FAILED"); }
    }
}
