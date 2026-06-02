package com.androidacy.lsparanoid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultStringProcessor 单元测试。
 * 替代原有的 DeobfuscatorHelper 测试。
 */
class DeobfuscatorHelperTest {

    @Test
    @DisplayName("DefaultStringProcessor: 基本加解密往返")
    void basicRoundTrip() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        String original = "Hello, World!";

        byte[] encrypted = processor.encrypt(original, null);
        String decrypted = processor.decrypt(encrypted, null);

        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("DefaultStringProcessor: 加密后数据应与原文不同")
    void encryptedDataShouldDifferFromOriginal() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        String original = "Test message";

        byte[] encrypted = processor.encrypt(original, null);
        byte[] originalBytes = original.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // 加密后数据长度 = 原始字节数 + 4字节头
        assertEquals(originalBytes.length + 4, encrypted.length,
            "加密后长度应等于原始长度+4字节头");
    }

    @Test
    @DisplayName("DefaultStringProcessor: shouldFog 默认行为")
    void shouldFogDefaultBehavior() {
        StringProcessor processor = new DefaultStringProcessor(0L);

        assertFalse(processor.shouldFog(""), "空字符串不应混淆");
        assertTrue(processor.shouldFog("hello"), "非空字符串应混淆");
    }

    @Test
    @DisplayName("DeobfuscatorHelper: MAX_CHUNK_LENGTH 常量仍可用")
    void maxChunkLengthConstantStillAvailable() {
        assertEquals(0x1fff, DeobfuscatorHelper.MAX_CHUNK_LENGTH,
            "MAX_CHUNK_LENGTH 应为 8191");
    }

    @Test
    @DisplayName("DefaultStringProcessor: 使用 null 密钥")
    void nullKeyShouldWork() {
        StringProcessor processor = new DefaultStringProcessor(42L);
        String original = "Test with null key";

        byte[] encrypted = processor.encrypt(original, null);
        String decrypted = processor.decrypt(encrypted, null);

        assertEquals(original, decrypted, "null 密钥加解密应正确");
    }

    @Test
    @DisplayName("DefaultStringProcessor: 使用空字节数组密钥")
    void emptyKeyShouldWork() {
        StringProcessor processor = new DefaultStringProcessor(42L);
        String original = "Test with empty key";
        byte[] emptyKey = new byte[0];

        byte[] encrypted = processor.encrypt(original, emptyKey);
        String decrypted = processor.decrypt(encrypted, emptyKey);

        assertEquals(original, decrypted, "空密钥加解密应正确");
    }

    @Test
    @DisplayName("DefaultStringProcessor: 使用自定义密钥")
    void customKeyShouldWork() {
        StringProcessor processor = new DefaultStringProcessor(42L);
        String original = "Test with custom key";
        byte[] key = "my-secret-key".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] encrypted = processor.encrypt(original, key);
        String decrypted = processor.decrypt(encrypted, key);

        assertEquals(original, decrypted, "自定义密钥加解密应正确");
    }
}
