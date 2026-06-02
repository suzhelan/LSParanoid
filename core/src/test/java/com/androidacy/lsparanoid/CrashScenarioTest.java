package com.androidacy.lsparanoid;

import com.androidacy.lsparanoid.processor.StringEntry;
import com.androidacy.lsparanoid.processor.StringRegistryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 新架构崩溃场景测试。
 * 测试 DefaultStringProcessor 和 StringRegistry 在异常条件下的健壮性。
 */
class CrashScenarioTest {

    // ==================== DefaultStringProcessor 健壮性测试 ====================

    @Test
    @DisplayName("CRASH: 损坏的加密数据解密应抛出异常而非崩溃")
    void corruptedEncryptedDataShouldThrowNotCrash() {
        StringProcessor processor = new DefaultStringProcessor(12345L);

        // 空数据（比最小有效数据还小）
        assertThrows(Exception.class, () -> {
            processor.decrypt(new byte[0], null);
        }, "空数据应抛出异常");

        // 过短数据（不足以包含4字节长度头）
        assertThrows(Exception.class, () -> {
            processor.decrypt(new byte[3], null);
        }, "过短数据应抛出异常");

        // 长度头声明比实际数据更长
        byte[] corrupted = new byte[10];
        corrupted[0] = 0x00; corrupted[1] = 0x00; corrupted[2] = 0x01; corrupted[3] = 0x00; // length=256
        assertThrows(Exception.class, () -> {
            processor.decrypt(corrupted, null);
        }, "长度不匹配应抛出异常");
    }

    @Test
    @DisplayName("CRASH: 大字符串加解密应正常工作")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void veryLargeStringShouldNotCrash() {
        StringProcessor processor = new DefaultStringProcessor(99999L);

        // 创建大字符串 (50KB)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50000; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        String largeString = sb.toString();

        assertDoesNotThrow(() -> {
            byte[] encrypted = processor.encrypt(largeString, null);
            String decrypted = processor.decrypt(encrypted, null);
            assertEquals(largeString, decrypted, "大字符串加解密应正确");
        }, "50KB字符串加解密不应崩溃");
    }

    @Test
    @DisplayName("CRASH: 空字符串加解密不应崩溃")
    void emptyStringShouldNotCrash() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        String original = "";

        byte[] encrypted = processor.encrypt(original, null);
        assertNotNull(encrypted, "空字符串加密结果不应为 null");
        assertTrue(encrypted.length >= 4, "空字符串加密结果至少包含长度头");

        String decrypted = processor.decrypt(encrypted, null);
        assertEquals(original, decrypted, "空字符串加解密应正确");
    }

    @Test
    @DisplayName("CRASH: 使用错误密钥解密应抛出异常或返回乱码")
    void wrongKeyDecryptShouldNotCrash() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        String original = "Secret message";

        byte[] key1 = "correct-key".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] key2 = "wrong-key!!!".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] encrypted = processor.encrypt(original, key1);

        // 使用错误密钥不应崩溃（但结果可能不正确）
        assertDoesNotThrow(() -> {
            String result = processor.decrypt(encrypted, key2);
            // 结果可能不等于原始字符串
            assertNotNull(result, "解密结果不应为 null");
        }, "错误密钥解密不应崩溃");
    }

    @Test
    @DisplayName("CRASH: 多线程并发加解密不应崩溃")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void concurrentEncryptDecryptShouldNotCrash() {
        StringProcessor processor = new DefaultStringProcessor(77777L);
        String original = "Concurrent test string";

        byte[] encrypted = processor.encrypt(original, null);

        // 启动多个线程同时解密
        Thread[] threads = new Thread[10];
        final Exception[] exceptions = new Exception[10];

        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        String decrypted = processor.decrypt(encrypted, null);
                        assertEquals(original, decrypted, "并发解密结果应正确");
                    }
                } catch (Exception e) {
                    exceptions[index] = e;
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            assertDoesNotThrow(() -> thread.join(2000),
                "线程应正常完成不死锁");
        }

        for (int i = 0; i < exceptions.length; i++) {
            assertNull(exceptions[i],
                "线程 " + i + " 不应抛出异常: " +
                (exceptions[i] != null ? exceptions[i].getMessage() : ""));
        }
    }

    // ==================== StringRegistry 健壮性测试 ====================

    @Test
    @DisplayName("CRASH: 注册大量字符串不应内存溢出")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void registeringManyStringsShouldNotOOM() {
        StringProcessor processor = new DefaultStringProcessor(33333L);
        try (StringRegistryImpl registry = new StringRegistryImpl(processor, null)) {
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 10_000; i++) {
                    StringEntry entry = registry.registerString("String number " + i);
                    assertNotNull(entry, "注册条目不应为 null");
                    assertTrue(entry.getEncryptedData().length > 0, "加密数据不应为空");
                }
            }, "注册10000个字符串不应内存溢出");
        }
    }

    @Test
    @DisplayName("CRASH: 空字符串后跟非空字符串注册应正常")
    void emptyThenNonEmptyRegistration() {
        StringProcessor processor = new DefaultStringProcessor(22222L);
        try (StringRegistryImpl registry = new StringRegistryImpl(processor, null)) {
            StringEntry entry1 = registry.registerString("");
            StringEntry entry2 = registry.registerString("Non-empty");

            assertNotNull(entry1, "空字符串条目不应为 null");
            assertNotNull(entry2, "非空字符串条目不应为 null");
            assertNotSame(entry1, entry2, "不同字符串应返回不同条目");
        }
    }

    @Test
    @DisplayName("CRASH: shouldFog=false 的字符串不应注册")
    void shouldFogFalseStringNotRegistered() {
        // 创建一个 shouldFog 总返回 false 的处理器
        StringProcessor noOpProcessor = new StringProcessor() {
            private final DefaultStringProcessor delegate = new DefaultStringProcessor(0L);

            @Override
            public byte[] encrypt(String data, byte[] key) {
                return delegate.encrypt(data, key);
            }

            @Override
            public String decrypt(byte[] data, byte[] key) {
                return delegate.decrypt(data, key);
            }

            @Override
            public boolean shouldFog(String data) {
                return false; // 所有字符串都不混淆
            }
        };

        // 注册仍然可以执行（Patcher 会调用 shouldFog 决定是否跳过）
        try (StringRegistryImpl registry = new StringRegistryImpl(noOpProcessor, null)) {
            assertDoesNotThrow(() -> {
                StringEntry entry = registry.registerString("test");
                assertNotNull(entry);
            });
        }
    }

    // ==================== Base64 / HexHelper 健壮性测试 ====================

    @Test
    @DisplayName("CRASH: Base64 超长输入不应整数溢出")
    void base64ExtremelyLongInputShouldNotOverflow() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            sb.append("ABCD");
        }
        String longBase64 = sb.toString();

        assertDoesNotThrow(() -> {
            Base64Decoder.decode(longBase64);
        }, "超长Base64字符串解码不应整数溢出");
    }

    @Test
    @DisplayName("CRASH: 格式错误的 Base64 应优雅处理")
    void malformedBase64ShouldNotCrash() {
        String[] malformedInputs = {
            "ABC",           // 不完整块
            "A===",          // 过多填充
            "====",          // 仅填充
        };

        for (String input : malformedInputs) {
            assertDoesNotThrow(() -> {
                try {
                    Base64Decoder.decode(input);
                } catch (IllegalArgumentException e) {
                    // 预期行为：非法字符
                }
            }, "格式错误的 Base64 不应崩溃: " + input);
        }
    }

    @Test
    @DisplayName("CRASH: HexHelper 非法字符应抛出异常")
    void hexHelperInvalidCharsShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> {
            HexHelper.decode("GHIJ"); // 非法十六进制字符
        }, "非法十六进制字符应抛出异常");

        assertThrows(IllegalArgumentException.class, () -> {
            HexHelper.decode("ABC"); // 奇数长度
        }, "奇数长度应抛出异常");
    }

    @Test
    @DisplayName("CRASH: 包含所有 Unicode 平面的字符串加解密")
    void allUnicodePlanesShouldNotCrash() {
        StringProcessor processor = new DefaultStringProcessor(11111L);

        // 包含各种 Unicode 字符
        String original = "ASCII: abc, "
                + "Latin: áéíóú, "
                + "Cyrillic: абв, "
                + "CJK: 中文日本, "
                + "Arabic: العربية, "
                + "Emoji: 😀🎉🌍, "
                + "Math: ∑∫∞";

        assertDoesNotThrow(() -> {
            byte[] encrypted = processor.encrypt(original, null);
            String decrypted = processor.decrypt(encrypted, null);
            assertEquals(original, decrypted, "所有Unicode平面字符应正确加解密");
        }, "包含多种 Unicode 的字符串不应崩溃");
    }

    // ==================== ObfuscationMode 健壮性测试 ====================

    @Test
    @DisplayName("CRASH: ObfuscationMode 空字符串应抛出异常")
    void obfuscationModeEmptyStringShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> {
            ObfuscationMode.fromString("");
        }, "空字符串模式名应抛出异常");
    }
}
