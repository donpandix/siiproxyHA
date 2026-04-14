package cl.cesarg.siiproxyHA.infrastructure.security;

public interface CryptoService {
    EncryptedValue encrypt(String plaintext) throws Exception;
    String decrypt(String ciphertext, String iv) throws Exception;

    record EncryptedValue(String ciphertext, String iv) {}
}
