package com.androidacy.lsparanoid;

import com.androidacy.lsparanoid.processor.StringEntry;
import com.androidacy.lsparanoid.processor.StringRegistryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 新架构集成测试。
 * 测试 DefaultStringProcessor 加解密 + StringRegistry 注册 + HexHelper 编解码。
 */
class IntegrationTest {

    // ==================== DefaultStringProcessor 加解密测试 ====================

    @Test
    @DisplayName("DefaultStringProcessor: 简单 ASCII 字符串加解密往返")
    void defaultProcessorSimpleAsciiRoundTrip() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        String original = "Hello, World!";

        byte[] encrypted = processor.encrypt(original, null);
        String decrypted = processor.decrypt(encrypted, null);

        assertEquals(original, decrypted, "加解密往返应保持字符串不变");
    }

    @Test
    @DisplayName("DefaultStringProcessor: 空字符串加解密")
    void defaultProcessorEmptyString() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        String original = "";

        byte[] encrypted = processor.encrypt(original, null);
        assertNotNull(encrypted, "空字符串加密结果不应为 null");
        String decrypted = processor.decrypt(encrypted, null);
        assertEquals(original, decrypted, "空字符串加解密应正确");
    }

    @Test
    @DisplayName("DefaultStringProcessor: Unicode 和 Emoji 加解密")
    void defaultProcessorUnicodeAndEmoji() {
        StringProcessor processor = new DefaultStringProcessor(54321L);
        String original = "Hello 世界 🌍 Привет 🎉";

        byte[] encrypted = processor.encrypt(original, null);
        String decrypted = processor.decrypt(encrypted, null);

        assertEquals(original, decrypted, "加解密应保留 Unicode/Emoji");
    }

    @Test
    @DisplayName("DefaultStringProcessor: 特殊字符加解密")
    void defaultProcessorSpecialCharacters() {
        StringProcessor processor = new DefaultStringProcessor(99999L);
        String original = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~\n\r\t";

        byte[] encrypted = processor.encrypt(original, null);
        String decrypted = processor.decrypt(encrypted, null);

        assertEquals(original, decrypted, "加解密应保留特殊字符");
    }

    @Test
    @DisplayName("DefaultStringProcessor: 多个字符串使用相同种子确定性加密")
    void defaultProcessorDeterministic() {
        String original = "Test string for determinism";

        // 第一次加密
        StringProcessor proc1 = new DefaultStringProcessor(42L);
        byte[] encrypted1 = proc1.encrypt(original, null);

        // 第二次加密（相同种子）
        StringProcessor proc2 = new DefaultStringProcessor(42L);
        byte[] encrypted2 = proc2.encrypt(original, null);

        assertArrayEquals(encrypted1, encrypted2, "相同种子应产生相同加密结果");
    }

    @Test
    @DisplayName("DefaultStringProcessor: 不同种子产生不同输出")
    void defaultProcessorDifferentSeeds() {
        String original = "Test string";

        StringProcessor proc1 = new DefaultStringProcessor(111L);
        byte[] encrypted1 = proc1.encrypt(original, null);

        StringProcessor proc2 = new DefaultStringProcessor(222L);
        byte[] encrypted2 = proc2.encrypt(original, null);

        assertFalse(Arrays.equals(encrypted1, encrypted2),
            "不同种子应产生不同的加密结果");
    }

    @Test
    @DisplayName("DefaultStringProcessor: 使用自定义密钥加解密")
    void defaultProcessorWithCustomKey() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        String original = "Secret message with key";
        byte[] key = "my-secret-key-123".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] encrypted = processor.encrypt(original, key);
        String decrypted = processor.decrypt(encrypted, key);

        assertEquals(original, decrypted, "使用自定义密钥加解密应正确");
    }

    @Test
    @DisplayName("DefaultStringProcessor: shouldFog 过滤空字符串")
    void defaultProcessorShouldFog() {
        StringProcessor processor = new DefaultStringProcessor(0L);

        assertFalse(processor.shouldFog(""), "空字符串不应混淆");
        assertTrue(processor.shouldFog("hello"), "非空字符串应混淆");
        assertTrue(processor.shouldFog("a"), "单字符应混淆");
    }

    // ==================== StringRegistry 注册测试 ====================

    @Test
    @DisplayName("StringRegistry: 短字符串应为内联存储")
    void stringRegistryShortStringIsInline() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        try (StringRegistryImpl registry = new StringRegistryImpl(processor, null)) {
            StringEntry entry = registry.registerString("Hello");

            assertInstanceOf(StringEntry.Inline.class, entry,
                "短字符串应使用内联存储");
            assertTrue(entry.getEncryptedData().length > 0, "加密数据不应为空");
        }
    }

    @Test
    @DisplayName("StringRegistry: 字符串去重")
    void stringRegistryDeduplication() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        try (StringRegistryImpl registry = new StringRegistryImpl(processor, null)) {
            StringEntry entry1 = registry.registerString("Hello");
            StringEntry entry2 = registry.registerString("Hello");

            assertSame(entry1, entry2, "相同字符串应返回相同条目（去重）");
        }
    }

    @Test
    @DisplayName("StringRegistry: 不同字符串返回不同条目")
    void stringRegistryDifferentStrings() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        try (StringRegistryImpl registry = new StringRegistryImpl(processor, null)) {
            StringEntry entry1 = registry.registerString("Hello");
            StringEntry entry2 = registry.registerString("World");

            assertNotSame(entry1, entry2, "不同字符串应返回不同条目");
        }
    }

    // ==================== HexHelper 编解码测试 ====================

    @Test
    @DisplayName("HexHelper: 编解码往返")
    void hexHelperRoundTrip() {
        byte[] original = {0x00, 0x01, 0x0F, 0x10, (byte) 0xFF, (byte) 0xAB, 0x7F};

        String hex = HexHelper.encode(original);
        byte[] decoded = HexHelper.decode(hex);

        assertArrayEquals(original, decoded, "Hex 编解码往返应保持数据不变");
    }

    @Test
    @DisplayName("HexHelper: 空数组")
    void hexHelperEmptyArray() {
        byte[] original = new byte[0];

        String hex = HexHelper.encode(original);
        assertEquals("", hex, "空数组应编码为空字符串");

        byte[] decoded = HexHelper.decode(hex);
        assertArrayEquals(original, decoded, "空字符串解码应为空数组");
    }

    @Test
    @DisplayName("HexHelper: 编码输出为小写")
    void hexHelperLowercase() {
        byte[] data = {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF};
        String hex = HexHelper.encode(data);

        assertEquals("abcdef", hex, "Hex 编码应为小写");
    }

    // ==================== ObfuscationMode 测试 ====================

    @Test
    @DisplayName("ObfuscationMode: fromString 解析")
    void obfuscationModeFromString() {
        assertEquals(ObfuscationMode.BASE64, ObfuscationMode.fromString("base64"));
        assertEquals(ObfuscationMode.BASE64, ObfuscationMode.fromString("BASE64"));
        assertEquals(ObfuscationMode.BASE64, ObfuscationMode.fromString("Base64"));
        assertEquals(ObfuscationMode.HEX, ObfuscationMode.fromString("hex"));
        assertEquals(ObfuscationMode.BYTES, ObfuscationMode.fromString("bytes"));
    }

    @Test
    @DisplayName("ObfuscationMode: 无效名称抛出异常")
    void obfuscationModeInvalidName() {
        assertThrows(IllegalArgumentException.class, () -> ObfuscationMode.fromString("invalid"));
    }

    // ==================== 端到端测试 ====================

    @Test
    @DisplayName("端到端: 加密 → Hex 编码 → 解码 → 解密")
    void endToEndHexMode() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        String original = "End-to-end test with hex mode";

        // 加密
        byte[] encrypted = processor.encrypt(original, null);

        // 编码为 hex
        String hexString = HexHelper.encode(encrypted);

        // 从 hex 解码
        byte[] decoded = HexHelper.decode(hexString);

        // 解密
        String result = processor.decrypt(decoded, null);

        assertEquals(original, result, "端到端 hex 模式加解密应正确");
    }

    @Test
    @DisplayName("端到端: 加密 → Base64 编码 → 解码 → 解密")
    void endToEndBase64Mode() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        String original = "End-to-end test with base64 mode 中文 🎉";

        // 加密
        byte[] encrypted = processor.encrypt(original, null);

        // 编码为 base64
        String base64String = java.util.Base64.getEncoder().encodeToString(encrypted);

        // 从 base64 解码
        byte[] decoded = java.util.Base64.getDecoder().decode(base64String);

        // 解密
        String result = processor.decrypt(decoded, null);

        assertEquals(original, result, "端到端 base64 模式加解密应正确");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 42, 12345, 99999, -1, -12345, Long.MAX_VALUE, Long.MIN_VALUE})
    @DisplayName("多种种子值加解密往返")
    void roundTripWithVariousSeeds(long seed) {
        StringProcessor processor = new DefaultStringProcessor(seed);
        String original = "Test with seed: " + seed;

        byte[] encrypted = processor.encrypt(original, null);
        String decrypted = processor.decrypt(encrypted, null);

        assertEquals(original, decrypted, "种子 " + seed + " 加解密应正确");
    }
}
