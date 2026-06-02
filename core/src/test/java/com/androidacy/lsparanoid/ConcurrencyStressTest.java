package com.androidacy.lsparanoid;

import com.androidacy.lsparanoid.processor.StringEntry;
import com.androidacy.lsparanoid.processor.StringRegistryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发压力测试。
 * 测试 DefaultStringProcessor 和 StringRegistry 的线程安全性。
 */
class ConcurrencyStressTest {

    @Test
    @DisplayName("STRESS: 100个线程同时解密同一字符串")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void hundredThreadsDecodingSameString() throws Exception {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        String original = "Concurrent test string with Unicode 世界";
        byte[] encrypted = processor.encrypt(original, null);

        int threadCount = 100;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicReference<String> errorMessage = new AtomicReference<>("");

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                        for (int j = 0; j < 10; j++) {
                            String result = processor.decrypt(encrypted, null);
                            if (!original.equals(result)) {
                                failed.set(true);
                                errorMessage.set("Decrypted string mismatch");
                            }
                        }
                    } catch (Exception e) {
                        failed.set(true);
                        errorMessage.set(e.getMessage());
                    }
                });
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertFalse(failed.get(), "并发解密应全部成功: " + errorMessage.get());
    }

    @Test
    @DisplayName("STRESS: 多线程并发注册不同字符串")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void concurrentStringRegistration() throws Exception {
        StringProcessor processor = new DefaultStringProcessor(54321L);
        StringRegistryImpl registry = new StringRegistryImpl(processor, null);

        int threadCount = 20;
        int stringsPerThread = 100;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);

        try {
            for (int t = 0; t < threadCount; t++) {
                final int threadIdx = t;
                executor.submit(() -> {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                        for (int i = 0; i < stringsPerThread; i++) {
                            StringEntry entry = registry.registerString(
                                "Thread" + threadIdx + "_String" + i);
                            assertNotNull(entry, "注册结果不应为 null");
                        }
                    } catch (Exception e) {
                        failed.set(true);
                    }
                });
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            registry.close();
        }

        assertFalse(failed.get(), "并发注册应全部成功");
    }

    @Test
    @DisplayName("STRESS: 多线程加密+解密混合操作")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void mixedEncryptDecryptUnderLoad() throws Exception {
        StringProcessor processor = new DefaultStringProcessor(99999L);

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicReference<String> errorMsg = new AtomicReference<>("");

        try {
            for (int i = 0; i < threadCount; i++) {
                final String testData = "TestString_" + i + "_测试_🎉";
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 50; j++) {
                            byte[] encrypted = processor.encrypt(testData, null);
                            String decrypted = processor.decrypt(encrypted, null);
                            if (!testData.equals(decrypted)) {
                                failed.set(true);
                                errorMsg.set("加解密不匹配");
                            }
                        }
                    } catch (Exception e) {
                        failed.set(true);
                        errorMsg.set(e.getMessage());
                    }
                });
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }

        assertFalse(failed.get(), "混合加解密压力测试应成功: " + errorMsg.get());
    }

    @RepeatedTest(5)
    @DisplayName("STRESS: 反复注册和去重验证")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void repeatedRegistrationAndDeduplication() {
        StringProcessor processor = new DefaultStringProcessor(77777L);
        try (StringRegistryImpl registry = new StringRegistryImpl(processor, null)) {
            // 注册相同字符串多次
            StringEntry entry1 = registry.registerString("DedupTest");
            StringEntry entry2 = registry.registerString("DedupTest");
            StringEntry entry3 = registry.registerString("DedupTest");

            assertSame(entry1, entry2, "相同字符串应返回相同条目");
            assertSame(entry2, entry3, "去重应稳定");
        }
    }

    @Test
    @DisplayName("STRESS: 大量并发 HexHelper 编解码")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void concurrentHexHelperOperations() throws Exception {
        byte[] testData = new byte[256];
        for (int i = 0; i < 256; i++) {
            testData[i] = (byte) i;
        }

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 100; j++) {
                            String hex = HexHelper.encode(testData);
                            byte[] decoded = HexHelper.decode(hex);
                            if (!java.util.Arrays.equals(testData, decoded)) {
                                failed.set(true);
                            }
                        }
                    } catch (Exception e) {
                        failed.set(true);
                    }
                });
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertFalse(failed.get(), "并发 HexHelper 操作应全部成功");
    }
}
