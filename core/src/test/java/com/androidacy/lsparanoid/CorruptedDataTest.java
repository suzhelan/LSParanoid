package com.androidacy.lsparanoid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 损坏/恶意数据处理测试。
 * 测试 DefaultStringProcessor 在异常输入下的健壮性。
 */
class CorruptedDataTest {

    @Test
    @DisplayName("CORRUPT: 损坏的加密数据（翻转位）")
    void flippedBitsInEncryptedData() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        String original = "Test data for corruption";

        byte[] encrypted = processor.encrypt(original, null);

        // 翻转加密数据中的某些位
        byte[] corrupted = Arrays.copyOf(encrypted, encrypted.length);
        for (int i = 4; i < corrupted.length; i++) { // 跳过长度头
            corrupted[i] = (byte) (corrupted[i] ^ 0xFF); // 翻转所有位
        }

        // 解密不应崩溃（但结果应与原始不同）
        assertDoesNotThrow(() -> {
            String result = processor.decrypt(corrupted, null);
            assertNotNull(result, "损坏数据解密结果不应为 null");
            assertNotEquals(original, result, "损坏数据解密结果应与原始不同");
        }, "损坏数据的解密不应崩溃");
    }

    @Test
    @DisplayName("CORRUPT: 截断的加密数据")
    void truncatedEncryptedData() {
        StringProcessor processor = new DefaultStringProcessor(12345L);
        String original = "Test string";

        byte[] encrypted = processor.encrypt(original, null);

        // 截断数据（保留长度头但截断有效载荷）
        byte[] truncated = Arrays.copyOf(encrypted, encrypted.length / 2);

        assertThrows(Exception.class, () -> {
            processor.decrypt(truncated, null);
        }, "截断数据解密应抛出异常");
    }

    @Test
    @DisplayName("CORRUPT: 长度头声称比实际数据更长")
    void lengthHeaderLongerThanActualData() {
        StringProcessor processor = new DefaultStringProcessor(12345L);

        // 构造长度头为 1000 但实际数据只有 10 字节
        byte[] malicious = new byte[14]; // 4字节头 + 10字节数据
        malicious[0] = 0x00;
        malicious[1] = 0x00;
        malicious[2] = 0x03; // length = 768
        malicious[3] = (byte) 0xE8;

        assertThrows(Exception.class, () -> {
            processor.decrypt(malicious, null);
        }, "长度不匹配应抛出异常");
    }

    @Test
    @DisplayName("CORRUPT: 长度头为负数")
    void negativeLengthHeader() {
        StringProcessor processor = new DefaultStringProcessor(12345L);

        // 长度头为负数（高字节最高位为1）
        byte[] malicious = new byte[20];
        malicious[0] = (byte) 0xFF; // length = negative
        malicious[1] = (byte) 0xFF;
        malicious[2] = (byte) 0xFF;
        malicious[3] = (byte) 0xFF;

        assertThrows(Exception.class, () -> {
            processor.decrypt(malicious, null);
        }, "负数长度头应抛出异常");
    }

    @Test
    @DisplayName("CORRUPT: 全零数据")
    void allZeroData() {
        StringProcessor processor = new DefaultStringProcessor(12345L);

        byte[] zeros = new byte[20]; // 全零（长度=0，后面是16字节填充）

        // 全零长度为0，应正常处理
        assertDoesNotThrow(() -> {
            String result = processor.decrypt(zeros, null);
            // 长度头为0意味着空字符串
            assertNotNull(result);
        }, "全零数据不应崩溃");
    }

    @Test
    @DisplayName("CORRUPT: 使用不同种子解密")
    void decryptWithDifferentSeed() {
        StringProcessor proc1 = new DefaultStringProcessor(111L);
        StringProcessor proc2 = new DefaultStringProcessor(222L);
        String original = "Secret message";

        byte[] encrypted = proc1.encrypt(original, null);

        // 使用不同种子解密（密钥流不同）
        assertDoesNotThrow(() -> {
            String result = proc2.decrypt(encrypted, null);
            assertNotNull(result, "不同种子解密结果不应为 null");
            assertNotEquals(original, result, "不同种子解密结果应与原始不同");
        }, "使用不同种子解密不应崩溃");
    }

    @Test
    @DisplayName("CORRUPT: Base64 解码无效字符")
    void base64InvalidCharacters() {
        assertDoesNotThrow(() -> {
            try {
                Base64Decoder.decode("!!!invalid!!!");
            } catch (IllegalArgumentException e) {
                // 预期行为
            }
        }, "无效 Base64 字符不应导致崩溃");
    }

    @Test
    @DisplayName("CORRUPT: Hex 解码奇数长度")
    void hexDecodeOddLength() {
        assertThrows(IllegalArgumentException.class, () -> {
            HexHelper.decode("ABC");
        }, "奇数长度十六进制字符串应抛出异常");
    }
}
