package cl.cesarg.siiproxyHA.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class AesGcmCryptoService implements CryptoService {

    private static final String AES = "AES";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCryptoService(@Value("${security.encryption.master-key}") String hexKey) {
        if (hexKey == null || hexKey.isBlank()) {
            throw new IllegalStateException("security.encryption.master-key must be configured");
        }
        byte[] decoded = HexFormat.of().parseHex(hexKey);
        this.key = new SecretKeySpec(decoded, AES);
    }

    @Override
    public EncryptedValue encrypt(String plaintext) throws Exception {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

        String ctB64 = Base64.getEncoder().encodeToString(ciphertext);
        String ivB64 = Base64.getEncoder().encodeToString(iv);
        return new EncryptedValue(ctB64, ivB64);
    }

    @Override
    public String decrypt(String ciphertext, String iv) throws Exception {
        byte[] ct = Base64.getDecoder().decode(ciphertext);
        byte[] ivb = Base64.getDecoder().decode(iv);

        Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, ivb);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        byte[] plain = cipher.doFinal(ct);
        return new String(plain);
    }
}
