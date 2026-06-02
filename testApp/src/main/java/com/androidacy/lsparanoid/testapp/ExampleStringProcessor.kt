/*
 * Copyright 2023 LSPosed
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.androidacy.lsparanoid.testapp

import android.R.attr.key
import com.androidacy.lsparanoid.StringProcessor
import java.nio.charset.StandardCharsets

/**
 * 示例自定义字符串加解密处理器。
 * 
 * 这是一个简单的 XOR 加密实现，用于演示自定义处理器的用法。
 * 实际项目中可以替换为 AES、ChaCha20 等更强的加密算法。
 * 
 * ## 使用方式
 * 
 * 在 build.gradle.kts 中配置：
 * ```kotlin
 * lsparanoid {
 * processor = "com.androidacy.lsparanoid.testapp.ExampleStringProcessor"
 * key = "my-secret-key-123"
 * mode = "base64"
 * }
 * ```
 */
class ExampleStringProcessor : StringProcessor {
    /**
     * 加密字符串。
     * 使用密钥循环 XOR 每个字节。
     * 
     * @param data 明文字符串
     * @param key  加密密钥（来自 Gradle 配置，可能为 null）
     * @return 加密后的字节数组
     */
    override fun encrypt(data: String, key: ByteArray?): ByteArray {
        val plaintext = data.toByteArray(StandardCharsets.UTF_8)
        val effectiveKey = resolveKey(key)

        // 加密：plaintext[i] XOR key[i % keyLen]
        val encrypted = ByteArray(plaintext.size)
        for (i in plaintext.indices) {
            encrypted[i] =
                (plaintext[i].toInt() xor effectiveKey[i % effectiveKey.size].toInt()).toByte()
        }
        return encrypted
    }

    /**
     * 解密字节数组。
     * XOR 加密是对称的，所以解密与加密操作相同。
     * 
     * @param data 加密后的字节数组
     * @param key  解密密钥（与加密时相同）
     * @return 原始明文字符串
     */
    override fun decrypt(data: ByteArray, key: ByteArray?): String {
        val effectiveKey = resolveKey(key)

        // 解密：data[i] XOR key[i % keyLen]（与加密相同）
        val plaintext = ByteArray(data.size)
        for (i in data.indices) {
            plaintext[i] =
                (data[i].toInt() xor effectiveKey[i % effectiveKey.size].toInt()).toByte()
        }
        return String(plaintext, StandardCharsets.UTF_8)
    }

    /**
     * 判断字符串是否需要混淆。
     * 
     * 
     * 过滤策略：
     * - 空字符串不混淆（无意义）
     * - 过长的字符串不混淆（避免性能问题，阈值 500 字符）
     * - 其他字符串都混淆
     */
    override fun shouldFog(data: String): Boolean {
        if (data.isEmpty()) {
            return false
        }
        // 过长的字符串跳过，避免增加太多体积
        return data.length <= 512
    }

    /**
     * 自定义格式化：将加密后的 byte[] 转为 DEX 中显示的字符串。
     * 
     * 
     * 示例：将每个字节转为 8 位二进制，然后把 0 替换为"喵"，1 替换为"呜"，
     * 字节之间用"~"分隔。
     * 
     * 
     * 例如 byte[]{0x48, 0x65} → "呜喵喵呜喵喵呜喵~呜喵喵喵喵呜呜呜"
     * 
     * 
     * 这样在反编译的 DEX 中，看到的不是 Base64 或 Hex，而是一串"喵呜"字符，
     * 大大增加逆向工程的难度和趣味性。
     * 
     * @param data 加密后的字节数组
     * @return 格式化后的字符串（将出现在 DEX 中）
     */
    override fun formatData(data: ByteArray): String {
        val sb = StringBuilder()
        for (i in data.indices) {
            if (i > 0) sb.append('~')
            // 将字节转为 8 位二进制字符串
            val binary = String.format(
                "%8s",
                Integer.toBinaryString(data[i].toInt() and 0xFF)
            ).replace(' ', '0')
            // 替换: 0→喵, 1→呜
            for (j in 0..<binary.length) {
                sb.append(if (binary[j] == '0') '喵' else '呜')
            }
        }
        return sb.toString()
    }

    /**
     * 自定义解析：将 DEX 中的格式化字符串解析回 byte[]。
     * 
     * 
     * 与 formatData 互为逆操作：将"喵"还原为 0，"呜"还原为 1，
     * 然后将每 8 个二进制位组合回一个字节。
     * 
     * @param formatted DEX 中的格式化字符串
     * @return 加密后的字节数组
     */
    override fun parseData(formatted: String): ByteArray {
        // 按 ~ 分割为各字节的"喵呜"串
        val parts: Array<String?> = formatted.split("~".toRegex()).toTypedArray()
        val data = ByteArray(parts.size)
        for (i in parts.indices) {
            // 喵→0, 呜→1, 还原为二进制字符串
            val binary = StringBuilder()
            for (j in 0..<parts[i]!!.length) {
                val c = parts[i]!![j]
                binary.append(if (c == '喵') '0' else '1')
            }
            data[i] = binary.toString().toInt(2).toByte()
        }
        return data
    }

    /**
     * 解析实际使用的密钥。
     * 如果未配置外部密钥，使用内置默认密钥。
     */
    private fun resolveKey(key: ByteArray?): ByteArray {
        if (key != null && key.isNotEmpty()) {
            return key
        }
        // 默认密钥（当 Gradle 中未配置 key 时使用）
        return "lsparanoid-default-key".toByteArray(StandardCharsets.UTF_8)
    }
}
