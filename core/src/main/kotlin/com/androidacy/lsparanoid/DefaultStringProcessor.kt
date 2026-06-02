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

package com.androidacy.lsparanoid

/**
 * 默认字符串加解密处理器。
 *
 * 当用户未在 Gradle 中配置自定义 `processor` 时，框架自动使用此默认实现。
 * 采用基于种子值的 XOR 流加密方案，简单高效，适合基本的字符串混淆需求。
 *
 * ## 加密算法
 *
 * 1. 将明文字符串编码为 UTF-8 字节数组
 * 2. 将字节长度写入前 4 字节（大端序）
 * 3. 使用种子生成的密钥流逐字节 XOR 加密
 *
 * ## 密钥流生成
 *
 * 基于种子值通过混合函数生成 16 字节循环密钥。
 * 如果 Gradle 中配置了 `key`，则将种子密钥与用户密钥混合使用。
 *
 * ## 安全性说明
 *
 * 此实现仅为混淆级别，不提供密码学安全保证。
 * 如需更强的加密，请实现自定义的 [StringProcessor]。
 *
 * @param seed 种子值，用于生成确定性密钥流。
 *             同一种子值确保相同字符串的加密结果一致（可重现构建）。
 */
class DefaultStringProcessor(
    private val seed: Long
) : StringProcessor {

    /** 从种子生成的 16 字节基础密钥 */
    private val seedKey: ByteArray by lazy { generateSeedKey(seed) }

    override fun encrypt(data: String, key: ByteArray?): ByteArray {
        val plaintext = data.toByteArray(Charsets.UTF_8)
        val effectiveKey = resolveKey(key)
        val result = ByteArray(plaintext.size + HEADER_SIZE)

        // 写入长度头（大端序，4字节）
        writeLength(result, plaintext.size)

        // 使用密钥流逐字节 XOR 加密
        var keyState = seed
        for (i in plaintext.indices) {
            keyState = mixKeyState(keyState, i)
            val keyByte = effectiveKey[(i + (keyState.toInt() and 0xFF)) % effectiveKey.size]
            result[i + HEADER_SIZE] = (plaintext[i].toInt() xor keyByte.toInt() xor (keyState.toInt() and 0xFF)).toByte()
        }

        return result
    }

    override fun decrypt(data: ByteArray, key: ByteArray?): String {
        val effectiveKey = resolveKey(key)

        // 读取长度头
        val length = readLength(data)
        require(length >= 0 && length <= data.size - HEADER_SIZE) {
            "Invalid encrypted data: length=$length, dataSize=${data.size}"
        }

        // 使用密钥流逐字节 XOR 解密
        val plaintext = ByteArray(length)
        var keyState = seed
        for (i in 0 until length) {
            keyState = mixKeyState(keyState, i)
            val keyByte = effectiveKey[(i + (keyState.toInt() and 0xFF)) % effectiveKey.size]
            plaintext[i] = (data[i + HEADER_SIZE].toInt() xor keyByte.toInt() xor (keyState.toInt() and 0xFF)).toByte()
        }

        return String(plaintext, Charsets.UTF_8)
    }

    override fun shouldFog(data: String): Boolean {
        // 默认策略：混淆所有非空字符串
        // 空字符串混淆没有意义，跳过
        return data.isNotEmpty()
    }

    // formatData/parseData 使用接口默认实现（Base64），仅在 mode=custom 时被调用

    // ==================== 内部方法 ====================

    /**
     * 解析实际使用的密钥。
     * 如果用户提供了 key，则混合种子密钥和用户密钥；
     * 否则仅使用种子密钥。
     */
    private fun resolveKey(userKey: ByteArray?): ByteArray {
        if (userKey == null) return seedKey

        // 混合种子密钥和用户密钥
        val combined = ByteArray(seedKey.size + userKey.size)
        System.arraycopy(seedKey, 0, combined, 0, seedKey.size)
        System.arraycopy(userKey, 0, combined, seedKey.size, userKey.size)
        return combined
    }

    companion object {
        /** 长度头大小（4字节大端序整数） */
        private const val HEADER_SIZE = 4

        /**
         * 从种子值生成 16 字节确定性密钥。
         * 使用多轮混合确保种子的所有位都参与密钥生成。
         */
        @JvmStatic
        private fun generateSeedKey(seed: Long): ByteArray {
            val key = ByteArray(16)
            var s = seed

            // 第一轮：低32位
            s = mixKeyState(s, 0)
            writeInt(key, 0, s.toInt())
            // 第二轮
            s = mixKeyState(s, 1)
            writeInt(key, 4, s.toInt())
            // 第三轮
            s = mixKeyState(s, 2)
            writeInt(key, 8, s.toInt())
            // 第四轮：高32位
            s = mixKeyState(s, 3)
            writeInt(key, 12, s.toInt())

            return key
        }

        /**
         * 密钥状态混合函数。
         * 使用类似 splitmix64 的混合算法，确保每一步的输出都充分分散。
         */
        @JvmStatic
        private fun mixKeyState(state: Long, index: Int): Long {
            // 黄金比例常数 (0x9E3779B97F4A7C15 的有符号表示)
            var s = state + index.toLong() * -7046029252770673131L
            s = (s xor (s ushr 30)) * -4658895280553007687L
            s = (s xor (s ushr 27)) * -7723592293110705685L
            return s xor (s ushr 31)
        }

        /** 将长度写入字节数组前4字节（大端序） */
        @JvmStatic
        private fun writeLength(data: ByteArray, length: Int) {
            data[0] = (length ushr 24).toByte()
            data[1] = (length ushr 16).toByte()
            data[2] = (length ushr 8).toByte()
            data[3] = length.toByte()
        }

        /** 从字节数组前4字节读取长度（大端序） */
        @JvmStatic
        private fun readLength(data: ByteArray): Int {
            return ((data[0].toInt() and 0xFF) shl 24) or
                    ((data[1].toInt() and 0xFF) shl 16) or
                    ((data[2].toInt() and 0xFF) shl 8) or
                    (data[3].toInt() and 0xFF)
        }

        /** 将 int 写入字节数组的指定位置（小端序，用于密钥生成） */
        @JvmStatic
        private fun writeInt(data: ByteArray, offset: Int, value: Int) {
            data[offset] = value.toByte()
            data[offset + 1] = (value ushr 8).toByte()
            data[offset + 2] = (value ushr 16).toByte()
            data[offset + 3] = (value ushr 24).toByte()
        }
    }
}
